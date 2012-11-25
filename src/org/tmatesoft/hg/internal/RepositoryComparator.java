/*
 * Copyright (c) 2011 TMate Software Ltd
 *  
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; version 2 of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * For information on how to redistribute this software under
 * the terms of a license other than GNU General Public License
 * contact TMate Software at support@hg4j.com
 */
package org.tmatesoft.hg.internal;

import static org.tmatesoft.hg.core.Nodeid.NULL;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.tmatesoft.hg.core.HgBadStateException;
import org.tmatesoft.hg.core.HgInvalidControlFileException;
import org.tmatesoft.hg.core.HgRemoteConnectionException;
import org.tmatesoft.hg.core.Nodeid;
import org.tmatesoft.hg.repo.HgChangelog;
import org.tmatesoft.hg.repo.HgRemoteRepository;
import org.tmatesoft.hg.repo.HgRemoteRepository.Range;
import org.tmatesoft.hg.repo.HgRemoteRepository.RemoteBranch;
import org.tmatesoft.hg.util.CancelSupport;
import org.tmatesoft.hg.util.CancelledException;
import org.tmatesoft.hg.util.ProgressSupport;

/**
 *
 * @author Artem Tikhomirov
 * @author TMate Software Ltd.
 */
public class RepositoryComparator {

	private final boolean debug = Boolean.parseBoolean(System.getProperty("hg4j.remote.debug"));
	private final HgChangelog.ParentWalker localRepo;
	private final HgRemoteRepository remoteRepo;
	private List<Nodeid> common;

	public RepositoryComparator(HgChangelog.ParentWalker pwLocal, HgRemoteRepository hgRemote) {
		localRepo = pwLocal;
		remoteRepo = hgRemote;
	}
	
	public RepositoryComparator compare(ProgressSupport progressSupport, CancelSupport cancelSupport) throws HgRemoteConnectionException, CancelledException {
		cancelSupport.checkCancelled();
		progressSupport.start(10);
		common = Collections.unmodifiableList(findCommonWithRemote());
		// sanity check
		for (Nodeid n : common) {
			if (!localRepo.knownNode(n)) {
				throw new HgBadStateException("Unknown node reported as common:" + n);
			}
		}
		progressSupport.done();
		return this;
	}
	
	public List<Nodeid> getCommon() {
		if (common == null) {
			throw new HgBadStateException("Call #compare(Object) first");
		}
		return common;
	}
	
	/**
	 * @return revisions that are children of common entries, i.e. revisions that are present on the local server and not on remote.
	 */
	public List<Nodeid> getLocalOnlyRevisions() {
		return localRepo.childrenOf(getCommon());
	}
	
	/**
	 * Similar to @link {@link #getLocalOnlyRevisions()}, use this one if you need access to changelog entry content, not 
	 * only its revision number. 
	 * @param inspector delegate to analyze changesets, shall not be <code>null</code>
	 */
	public void visitLocalOnlyRevisions(HgChangelog.Inspector inspector) throws HgInvalidControlFileException {
		if (inspector == null) {
			throw new IllegalArgumentException();
		}
		// one can use localRepo.childrenOf(getCommon()) and then iterate over nodeids, but there seems to be
		// another approach to get all changes after common:
		// find index of earliest revision, and report all that were later
		final HgChangelog changelog = localRepo.getRepo().getChangelog();
		int earliestRevision = Integer.MAX_VALUE;
		List<Nodeid> commonKnown = getCommon();
		for (Nodeid n : commonKnown) {
			if (!localRepo.hasChildren(n)) {
				// there might be (old) nodes, known both locally and remotely, with no children
				// hence, we don't need to consider their local revision number
				continue;
			}
			int lr = changelog.getRevisionIndex(n);
			if (lr < earliestRevision) {
				earliestRevision = lr;
			}
		}
		if (earliestRevision == Integer.MAX_VALUE) {
			// either there are no common nodes (known locally and at remote)
			// or no local children found (local is up to date). In former case, perhaps I shall bit return silently,
			// but check for possible wrong repo comparison (hs says 'repository is unrelated' if I try to 
			// check in/out for a repo that has no common nodes.
			return;
		}
		if (earliestRevision < 0 || earliestRevision >= changelog.getLastRevision()) {
			throw new HgBadStateException(String.format("Invalid index of common known revision: %d in total of %d", earliestRevision, 1+changelog.getLastRevision()));
		}
		changelog.range(earliestRevision+1, changelog.getLastRevision(), inspector);
	}

	private List<Nodeid> findCommonWithRemote() throws HgRemoteConnectionException {
		List<Nodeid> remoteHeads = remoteRepo.heads();
		LinkedList<Nodeid> resultCommon = new LinkedList<Nodeid>(); // these remotes are known in local
		LinkedList<Nodeid> toQuery = new LinkedList<Nodeid>(); // these need further queries to find common
		for (Nodeid rh : remoteHeads) {
			if (localRepo.knownNode(rh)) {
				resultCommon.add(rh);
			} else {
				toQuery.add(rh);
			}
		}
		if (toQuery.isEmpty()) {
			return resultCommon; 
		}
		LinkedList<RemoteBranch> checkUp2Head = new LinkedList<RemoteBranch>(); // branch.root and branch.head are of interest only.
		// these are branches with unknown head but known root, which might not be the last common known,
		// i.e. there might be children changeset that are also available at remote, [..?..common-head..remote-head] - need to 
		// scroll up to common head.
		while (!toQuery.isEmpty()) {
			List<RemoteBranch> remoteBranches = remoteRepo.branches(toQuery);	//head, root, first parent, second parent
			toQuery.clear();
			while(!remoteBranches.isEmpty()) {
				RemoteBranch rb = remoteBranches.remove(0);
				// I assume branches remote call gives branches with head equal to what I pass there, i.e.
				// that I don't need to check whether rb.head is unknown.
				if (localRepo.knownNode(rb.root)) {
					// we known branch start, common head is somewhere in its descendants line  
					checkUp2Head.add(rb);
				} else {
					// dig deeper in the history, if necessary
					if (!rb.p1.isNull() && !localRepo.knownNode(rb.p1)) {
						toQuery.add(rb.p1);
					}
					if (!rb.p2.isNull() && !localRepo.knownNode(rb.p2)) {
						toQuery.add(rb.p2);
					}
				}
			}
		}
		// can't check nodes between checkUp2Head element and local heads, remote might have distinct descendants sequence
		for (RemoteBranch rb : checkUp2Head) {
			// rb.root is known locally
			List<Nodeid> remoteRevisions = remoteRepo.between(rb.head, rb.root);
			if (remoteRevisions.isEmpty()) {
				// head is immediate child
				resultCommon.add(rb.root);
			} else {
				// between gives result from head to root, I'd like to go in reverse direction
				Collections.reverse(remoteRevisions);
				Nodeid root = rb.root;
				while(!remoteRevisions.isEmpty()) {
					Nodeid n = remoteRevisions.remove(0);
					if (localRepo.knownNode(n)) {
						if (remoteRevisions.isEmpty()) {
							// this is the last known node before an unknown
							resultCommon.add(n);
							break;
						}
						if (remoteRevisions.size() == 1) {
							// there's only one left between known n and unknown head
							// this check is to save extra between query, not really essential
							Nodeid last = remoteRevisions.remove(0);
							resultCommon.add(localRepo.knownNode(last) ? last : n);
							break;
						}
						// might get handy for next between query, to narrow search down
						root = n;
					} else {
						remoteRevisions = remoteRepo.between(n, root);
						Collections.reverse(remoteRevisions);
						if (remoteRevisions.isEmpty()) {
							resultCommon.add(root);
						}
					}
				}
			}
		}
		// TODO ensure unique elements in the list
		return resultCommon;
	}

	// somewhat similar to Outgoing.findCommonWithRemote() 
	public List<BranchChain> calculateMissingBranches() throws HgRemoteConnectionException {
		List<Nodeid> remoteHeads = remoteRepo.heads();
		LinkedList<Nodeid> common = new LinkedList<Nodeid>(); // these remotes are known in local
		LinkedList<Nodeid> toQuery = new LinkedList<Nodeid>(); // these need further queries to find common
		for (Nodeid rh : remoteHeads) {
			if (localRepo.knownNode(rh)) {
				common.add(rh);
			} else {
				toQuery.add(rh);
			}
		}
		if (toQuery.isEmpty()) {
			return Collections.emptyList(); // no incoming changes
		}
		LinkedList<BranchChain> branches2load = new LinkedList<BranchChain>(); // return value
		// detailed comments are in Outgoing.findCommonWithRemote
		LinkedList<RemoteBranch> checkUp2Head = new LinkedList<RemoteBranch>();
		// records relation between branch head and its parent branch, if any
		HashMap<Nodeid, BranchChain> head2chain = new HashMap<Nodeid, BranchChain>();
		while (!toQuery.isEmpty()) {
			List<RemoteBranch> remoteBranches = remoteRepo.branches(toQuery);	//head, root, first parent, second parent
			toQuery.clear();
			while(!remoteBranches.isEmpty()) {
				RemoteBranch rb = remoteBranches.remove(0);
				BranchChain chainElement = head2chain.get(rb.head);
				if (chainElement == null) {
					chainElement = new BranchChain(rb.head);
					// record this unknown branch to download later
					branches2load.add(chainElement);
					// the only chance we'll need chainElement in the head2chain is when we know this branch's root 
					head2chain.put(rb.head, chainElement);
				}
				if (localRepo.knownNode(rb.root)) {
					// we known branch start, common head is somewhere in its descendants line  
					checkUp2Head.add(rb);
				} else {
					chainElement.branchRoot = rb.root;
					// dig deeper in the history, if necessary
					boolean hasP1 = !rb.p1.isNull(), hasP2 = !rb.p2.isNull();  
					if (hasP1 && !localRepo.knownNode(rb.p1)) {
						toQuery.add(rb.p1);
						// we might have seen parent node already, and recorded it as a branch chain
						// we shall reuse existing BC to get it completely initializer (head2chain map
						// on second put with the same key would leave first BC uninitialized.
						
						// It seems there's no reason to be affraid (XXX although shall double-check)
						// that BC's chain would get corrupt (its p1 and p2 fields assigned twice with different values)
						// as parents are always the same (and likely, BC that is common would be the last unknown)
						BranchChain bc = head2chain.get(rb.p1);
						if (bc == null) {
							head2chain.put(rb.p1, bc = new BranchChain(rb.p1));
						}
						chainElement.p1 = bc;
					}
					if (hasP2 && !localRepo.knownNode(rb.p2)) {
						toQuery.add(rb.p2);
						BranchChain bc = head2chain.get(rb.p2);
						if (bc == null) {
							head2chain.put(rb.p2, bc = new BranchChain(rb.p2));
						}
						chainElement.p2 = bc;
					}
					if (!hasP1 && !hasP2) {
						// special case, when we do incoming against blank repository, chainElement.branchRoot
						// is first unknown element (revision 0). We need to add another fake BranchChain
						// to fill the promise that terminal BranchChain has branchRoot that is known both locally and remotely
						BranchChain fake = new BranchChain(NULL);
						fake.branchRoot = NULL;
						chainElement.p1 = chainElement.p2 = fake;
					}
				}
			}
		}
		for (RemoteBranch rb : checkUp2Head) {
			Nodeid h = rb.head;
			Nodeid r = rb.root;
			int watchdog = 1000;
			assert head2chain.containsKey(h);
			BranchChain bc = head2chain.get(h);
			assert bc != null : h.toString();
			// if we know branch root locally, there could be no parent branch chain elements.
			assert bc.p1 == null;
			assert bc.p2 == null;
			do {
				List<Nodeid> between = remoteRepo.between(h, r);
				if (between.isEmpty()) {
					bc.branchRoot = r;
					break;
				} else {
					Collections.reverse(between);
					for (Nodeid n : between) {
						if (localRepo.knownNode(n)) {
							r = n;
						} else {
							h = n;
							break;
						}
					}
					Nodeid lastInBetween = between.get(between.size() - 1);
					if (r.equals(lastInBetween)) {
						bc.branchRoot = r;
						break;
					} else if (h.equals(lastInBetween)) { // the only chance for current head pointer to point to the sequence tail
						// is when r is second from the between list end (iow, head,1,[2],4,8...,root)
						bc.branchRoot = r;
						break;
					}
				}
			} while(--watchdog > 0);
			if (watchdog == 0) {
				throw new HgBadStateException(String.format("Can't narrow down branch [%s, %s]", rb.head.shortNotation(), rb.root.shortNotation()));
			}
		}
		if (debug) {
			System.out.println("calculateMissingBranches:");
			for (BranchChain bc : branches2load) {
				bc.dump();
			}
		}
		return branches2load;
	}

	// root and head (and all between) are unknown for each chain element but last (terminal), which has known root (revision
	// known to be locally and at remote server
	// alternative would be to keep only unknown elements (so that promise of calculateMissingBranches would be 100% true), but that 
	// seems to complicate the method, while being useful only for the case when we ask incoming for an empty repository (i.e.
	// where branch chain return all nodes, -1..tip.
	public static final class BranchChain {
		// when we construct a chain, we know head which is missing locally, hence init it right away.
		// as for root (branch unknown start), we might happen to have one locally, and need further digging to find out right branch start  
		public final Nodeid branchHead;
		public Nodeid branchRoot;
		// either of these can be null, or both.
		// although RemoteBranch has either both parents null, or both non-null, when we construct a chain
		// we might encounter that we locally know one of branch's parent, hence in the chain corresponding field will be blank.
		public BranchChain p1;
		public BranchChain p2;

		public BranchChain(Nodeid head) {
			assert head != null;
			branchHead = head;
		}
		public boolean isTerminal() {
			return p1 == null && p2 == null; // either can be null, see comment above. Terminal is only when no way to descent
		}
		
		// true when this BranchChain is a branch that spans up to very start of the repository
		// Thus, the only common revision is NULL, recorded in a fake BranchChain object shared between p1 and p2
		/*package-local*/ boolean isRepoStart() {
			return p1 == p2 && p1 != null && p1.branchHead == p1.branchRoot && p1.branchHead.isNull();
		}

		@Override
		public String toString() {
			return String.format("BranchChain [%s, %s]", branchRoot, branchHead);
		}

		void dump() {
			System.out.println(toString());
			internalDump("  ");
		}

		private void internalDump(String prefix) {
			if (p1 != null) {
				System.out.println(prefix + p1.toString());
			} else if (p2 != null) {
				System.out.println(prefix + "NONE?!");
			}
			if (p2 != null) {
				System.out.println(prefix + p2.toString());
			} else if (p1 != null) {
				System.out.println(prefix + "NONE?!");
			}
			prefix += "  ";
			if (p1 != null) {
				p1.internalDump(prefix);
			}
			if (p2 != null) {
				p2.internalDump(prefix);
			}
		}
	}

	/**
	 * @return list of nodeids from branchRoot to branchHead, inclusive. IOW, first element of the list is always root of the branch 
	 */
	public List<Nodeid> completeBranch(final Nodeid branchRoot, final Nodeid branchHead) throws HgRemoteConnectionException {
		class DataEntry {
			public final Nodeid queryHead;
			public final int headIndex;
			public List<Nodeid> entries;

			public DataEntry(Nodeid head, int index, List<Nodeid> data) {
				queryHead = head;
				headIndex = index;
				entries = data;
			}
		};

		List<Nodeid> initial = remoteRepo.between(branchHead, branchRoot);
		Nodeid[] result = new Nodeid[1 + (1 << initial.size())];
		result[0] = branchHead;
		int rootIndex = -1; // index in the result, where to place branche's root.
		if (initial.isEmpty()) {
			rootIndex = 1;
		} else if (initial.size() == 1) {
			rootIndex = 2;
		}
		LinkedList<DataEntry> datas = new LinkedList<DataEntry>();
		// DataEntry in datas has entries list filled with 'between' data, whereas 
		// DataEntry in toQuery keeps only nodeid and its index, with entries to be initialized before 
		// moving to datas. 
		LinkedList<DataEntry> toQuery = new LinkedList<DataEntry>();
		//
		datas.add(new DataEntry(branchHead, 0, initial));
		int totalQueries = 1;
		HashSet<Nodeid> queried = new HashSet<Nodeid>();
		while(!datas.isEmpty()) {
			// keep record of those planned to be queried next time we call between()
			// although may keep these in queried, if really don't want separate collection
			HashSet<Nodeid> scheduled = new HashSet<Nodeid>();  
			do {
				DataEntry de = datas.removeFirst();
				// populate result with discovered elements between de.qiueryRoot and branch's head
				for (int i = 1, j = 0; j < de.entries.size(); i = i << 1, j++) {
					int idx = de.headIndex + i;
					result[idx] = de.entries.get(j);
				}
				// form next query entries from new unknown elements
				if (de.entries.size() > 1) {
					/* when entries has only one element, it means de.queryRoot was at head-2 position, and thus
					 * no new information can be obtained. E.g. when it's 2, it might be case of [0..4] query with
					 * [1,2] result, and we need one more query to get element 3.   
					 */
					for (int i =1, j = 0; j < de.entries.size(); i = i<<1, j++) {
						int idx = de.headIndex + i;
						Nodeid x = de.entries.get(j);
						if (!queried.contains(x) && !scheduled.contains(x) && (rootIndex == -1 || rootIndex - de.headIndex > 1)) {
							/*queries for elements right before head is senseless, but unless we know head's index, do it anyway*/
							toQuery.add(new DataEntry(x, idx, null));
							scheduled.add(x);
						}
					}
				}
			} while (!datas.isEmpty());
			if (!toQuery.isEmpty()) {
				totalQueries++;
			}
			// for each query, create an between request range, keep record Range->DataEntry to know range's start index  
			LinkedList<HgRemoteRepository.Range> betweenBatch = new LinkedList<HgRemoteRepository.Range>();
			HashMap<HgRemoteRepository.Range, DataEntry> rangeToEntry = new HashMap<HgRemoteRepository.Range, DataEntry>();
			for (DataEntry de : toQuery) {
				queried.add(de.queryHead);
				HgRemoteRepository.Range r = new HgRemoteRepository.Range(branchRoot, de.queryHead);
				betweenBatch.add(r);
				rangeToEntry.put(r, de);
			}
			if (!betweenBatch.isEmpty()) {
				Map<Range, List<Nodeid>> between = remoteRepo.between(betweenBatch);
				for (Entry<Range, List<Nodeid>> e : between.entrySet()) {
					DataEntry de = rangeToEntry.get(e.getKey());
					assert de != null;
					de.entries = e.getValue();
					if (rootIndex == -1 && de.entries.size() == 1) {
						// returned sequence of length 1 means we used element from [head-2] as root
						int numberOfElementsExcludingRootAndHead = de.headIndex + 1;
						rootIndex = numberOfElementsExcludingRootAndHead + 1;
						if (debug) {
							System.out.printf("On query %d found out exact number of missing elements: %d\n", totalQueries, numberOfElementsExcludingRootAndHead);
						}
					}
					datas.add(de); // queue up to record result and construct further requests
				}
				betweenBatch.clear();
				rangeToEntry.clear();
			}
			toQuery.clear();
		}
		if (rootIndex == -1) {
			throw new HgBadStateException("Shall not happen, provided between output is correct"); // FIXME EXCEPTIONS
		}
		result[rootIndex] = branchRoot;
		boolean resultOk = true;
		LinkedList<Nodeid> fromRootToHead = new LinkedList<Nodeid>();
		for (int i = 0; i <= rootIndex; i++) {
			Nodeid n = result[i];
			if (n == null) {
				System.out.printf("ERROR: element %d wasn't found\n",i);
				resultOk = false;
			}
			fromRootToHead.addFirst(n); // reverse order
		}
		if (debug) {
			System.out.println("Total queries:" + totalQueries);
		}
		if (!resultOk) {
			throw new HgBadStateException("See console for details"); // FIXME EXCEPTIONS
		}
		return fromRootToHead;
	}

	/**
	 *  returns in order from branch root to head
	 *  for a non-empty BranchChain, shall return modifiable list
	 */
	public List<Nodeid> visitBranches(BranchChain bc) throws HgRemoteConnectionException {
		if (bc == null) {
			return Collections.emptyList();
		}
		List<Nodeid> mine = completeBranch(bc.branchRoot, bc.branchHead);
		if (bc.isTerminal() || bc.isRepoStart()) {
			return mine;
		}
		List<Nodeid> parentBranch1 = visitBranches(bc.p1);
		List<Nodeid> parentBranch2 = visitBranches(bc.p2);
		// merge
		LinkedList<Nodeid> merged = new LinkedList<Nodeid>();
		ListIterator<Nodeid> i1 = parentBranch1.listIterator(), i2 = parentBranch2.listIterator();
		while (i1.hasNext() && i2.hasNext()) {
			Nodeid n1 = i1.next();
			Nodeid n2 = i2.next();
			if (n1.equals(n2)) {
				merged.addLast(n1);
			} else {
				// first different => add both, and continue adding both tails sequentially 
				merged.add(n2);
				merged.add(n1);
				break;
			}
		}
		// copy rest of second parent branch
		while (i2.hasNext()) {
			merged.add(i2.next());
		}
		// copy rest of first parent branch
		while (i1.hasNext()) {
			merged.add(i1.next());
		}
		//
		ArrayList<Nodeid> rv = new ArrayList<Nodeid>(mine.size() + merged.size());
		rv.addAll(merged);
		rv.addAll(mine);
		return rv;
	}

	public void collectKnownRoots(BranchChain bc, Set<Nodeid> result) {
		if (bc == null) {
			return;
		}
		if (bc.isTerminal()) {
			result.add(bc.branchRoot);
			return;
		}
		if (bc.isRepoStart()) {
			return;
		}
		collectKnownRoots(bc.p1, result);
		collectKnownRoots(bc.p2, result);
	}
}
