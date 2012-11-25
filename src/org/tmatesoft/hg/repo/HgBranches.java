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
package org.tmatesoft.hg.repo;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;

import org.tmatesoft.hg.core.HgException;
import org.tmatesoft.hg.core.HgInvalidControlFileException;
import org.tmatesoft.hg.core.HgInvalidRevisionException;
import org.tmatesoft.hg.core.Nodeid;
import org.tmatesoft.hg.internal.Experimental;
import org.tmatesoft.hg.repo.HgChangelog.RawChangeset;
import org.tmatesoft.hg.util.ProgressSupport;

/**
 *
 * @author Artem Tikhomirov
 * @author TMate Software Ltd.
 */
public class HgBranches {
	
	private final Map<String, BranchInfo> branches = new TreeMap<String, BranchInfo>();
	private final HgRepository repo;
	private boolean isCacheActual = false;

	HgBranches(HgRepository hgRepo) {
		repo = hgRepo;
	}

	private int readCache() {
		File branchheadsCache = getCacheFile();
		int lastInCache = -1;
		if (!branchheadsCache.canRead()) {
			return lastInCache;
		}
		BufferedReader br = null;
		final Pattern spacePattern = Pattern.compile(" ");
		try {
			final LinkedHashMap<String, List<Nodeid>> branchHeads = new LinkedHashMap<String, List<Nodeid>>();
			br = new BufferedReader(new FileReader(branchheadsCache));
			String line = br.readLine();
			if (line == null || line.trim().length() == 0) {
				return lastInCache;
			}
			String[] cacheIdentity = spacePattern.split(line.trim());
			lastInCache = Integer.parseInt(cacheIdentity[1]);
			final int lastKnownRepoRevIndex = repo.getChangelog().getLastRevision();
			if (lastInCache > lastKnownRepoRevIndex || !repo.getChangelog().getRevision(lastKnownRepoRevIndex).equals(Nodeid.fromAscii(cacheIdentity[0]))) {
				// there are chances cache file got invalid entries due to e.g. rollback operation
				return -1;
			}
			while ((line = br.readLine()) != null) {
				String[] elements = spacePattern.split(line.trim());
				if (elements.length != 2) {
					// bad entry
					continue;
				}
				// I assume split returns substrings of the original string, hence copy of a branch name
				String branchName = new String(elements[elements.length-1]);
				List<Nodeid> heads = branchHeads.get(elements[1]);
				if (heads == null) {
					branchHeads.put(branchName, heads = new LinkedList<Nodeid>());
				}
				heads.add(Nodeid.fromAscii(elements[0]));
			}
			for (Map.Entry<String, List<Nodeid>> e : branchHeads.entrySet()) {
				Nodeid[] heads = e.getValue().toArray(new Nodeid[e.getValue().size()]);
				BranchInfo bi = new BranchInfo(e.getKey(), heads);
				branches.put(e.getKey(), bi);
			}
			return lastInCache;
		} catch (IOException ex) {
			 // log error, but otherwise do nothing
			repo.getContext().getLog().warn(getClass(), ex, null);
			// FALL THROUGH to return -1 indicating no cache information 
		} catch (NumberFormatException ex) {
			repo.getContext().getLog().warn(getClass(), ex, null);
			// FALL THROUGH
		} catch (HgInvalidControlFileException ex) {
			// shall not happen, thus log as error
			repo.getContext().getLog().error(getClass(), ex, null);
			// FALL THROUGH
		} catch (HgInvalidRevisionException ex) {
			repo.getContext().getLog().error(getClass(), ex, null);
			// FALL THROUGH
		} finally {
			if (br != null) {
				try {
					br.close();
				} catch (IOException ex) {
					repo.getContext().getLog().info(getClass(), ex, null); // ignore
				}
			}
		}
		return -1; // deliberately not lastInCache, to avoid anything but -1 when 1st line was read and there's error is in lines 2..end
	}

	void collect(final ProgressSupport ps) throws HgInvalidControlFileException {
		branches.clear();
		ps.start(1 + repo.getChangelog().getRevisionCount() * 2);
		//
		int lastCached = readCache();
		isCacheActual = lastCached == repo.getChangelog().getLastRevision();
		if (!isCacheActual) {
			final HgChangelog.ParentWalker pw = repo.getChangelog().new ParentWalker();
			pw.init();
			ps.worked(repo.getChangelog().getRevisionCount());
			// first revision branch found at
			final HashMap<String, Nodeid> branchStart = new HashMap<String, Nodeid>();
			// last revision seen for the branch
			final HashMap<String, Nodeid> branchLastSeen = new HashMap<String, Nodeid>();
			// revisions from the branch that have no children at all
			final HashMap<String, List<Nodeid>> branchHeads = new HashMap<String, List<Nodeid>>();
			// revisions that are immediate children of a node from a given branch
			// after iteration, there are some revisions left in this map (children of a branch last revision
			// that doesn't belong to the branch. No use of this now, perhaps can deduce isInactive (e.g.those 
			// branches that have non-empty candidates are inactive if all their heads are roots for those left)
			final HashMap<String, List<Nodeid>> branchHeadCandidates = new HashMap<String, List<Nodeid>>();
			HgChangelog.Inspector insp = new HgChangelog.Inspector() {
				
				public void next(int revisionNumber, Nodeid nodeid, RawChangeset cset) {
					String branchName = cset.branch();
					if (!branchStart.containsKey(branchName)) {
						branchStart.put(branchName, nodeid);
						branchHeads.put(branchName, new LinkedList<Nodeid>());
						branchHeadCandidates.put(branchName, new LinkedList<Nodeid>());
					} else {
						final List<Nodeid> headCandidates = branchHeadCandidates.get(branchName);
						if (headCandidates.remove(nodeid)) {
							// likely we don't need to keep parent anymore, as we found at least 1 child thereof to be at the same branch
							// however, it's possible the child we found is a result of an earlier fork, and revision in the 
							// branchLastSeen is 'parallel' head, which needs to be kept
							Nodeid lastSeenInBranch = branchLastSeen.get(branchName);
							// check if current revision is on descendant line. Seems direct parents check is enough
							if (pw.safeFirstParent(nodeid).equals(lastSeenInBranch) || pw.safeSecondParent(nodeid).equals(lastSeenInBranch)) {
								branchLastSeen.remove(branchName);
							}
						}
					}
					List<Nodeid> immediateChildren = pw.directChildren(nodeid);
					if (immediateChildren.size() > 0) {
						// 1) children may be in another branch
						// and unless we later came across another element from this branch,
						// we need to record all these as potential heads
						//
						// 2) head1 with children in different branch, and head2 in this branch without children
						branchLastSeen.put(branchName, nodeid);
						branchHeadCandidates.get(branchName).addAll(immediateChildren);
					} else {
						// no more children known for this node, it's (one of the) head of the branch
						branchHeads.get(branchName).add(nodeid);
					}
					ps.worked(1);
				}
			}; 
			repo.getChangelog().range(lastCached == -1 ? 0 : lastCached+1, HgRepository.TIP, insp);
			// those last seen revisions from the branch that had no children from the same branch are heads.
			for (String bn : branchLastSeen.keySet()) {
				// these are inactive branches? - there were children, but not from the same branch?
				branchHeads.get(bn).add(branchLastSeen.get(bn));
			}
			for (String bn : branchStart.keySet()) {
				BranchInfo bi = branches.get(bn);
				if (bi != null) {
					// although heads from cache shall not intersect with heads after lastCached,
					// use of LHS doesn't hurt (and makes sense e.g. if cache is not completely correct in my tests) 
					LinkedHashSet<Nodeid> heads = new LinkedHashSet<Nodeid>(bi.getHeads());
					for (Nodeid oldHead : bi.getHeads()) {
						// XXX perhaps, need pw.canReach(Nodeid from, Collection<Nodeid> to)
						List<Nodeid> newChildren = pw.childrenOf(Collections.singletonList(oldHead));
						if (!newChildren.isEmpty()) {
							// likely not a head any longer,
							// check if any new head can be reached from old one, and, if yes,
							// do not consider that old head as head.
							for (Nodeid newHead : branchHeads.get(bn)) {
								if (newChildren.contains(newHead)) {
									heads.remove(oldHead);
									break;
								}
							}
						} // else - oldHead still head for the branch
					}
					heads.addAll(branchHeads.get(bn));
					bi = new BranchInfo(bn, bi.getStart(), heads.toArray(new Nodeid[0]));
				} else {
					Nodeid[] heads = branchHeads.get(bn).toArray(new Nodeid[0]);
					bi = new BranchInfo(bn, branchStart.get(bn), heads);
				}
				branches.put(bn, bi);
			}
		}
		final HgChangelog clog = repo.getChangelog();
		final HgChangelog.RevisionMap rmap = clog.new RevisionMap().init();
		for (BranchInfo bi : branches.values()) {
			bi.validate(clog, rmap);
		}
		ps.done();
	}

	public List<BranchInfo> getAllBranches() {
		return new LinkedList<BranchInfo>(branches.values());
				
	}

	public BranchInfo getBranch(String name) {
		return branches.get(name);
	}

	/**
	 * Writes down information about repository branches in a format Mercurial native client can understand.
	 * Cache file gets overwritten only if it is out of date (i.e. misses some branch information)
	 * @throws IOException if write to cache file failed
	 * @throws HgException subclass of {@link HgException} in case of repository access issue
	 */
	@Experimental(reason="Usage of cache isn't supposed to be public knowledge")
	public void writeCache() throws IOException, HgException {
		if (isCacheActual) {
			return;
		}
		File branchheadsCache = getCacheFile();
		if (!branchheadsCache.exists()) {
			branchheadsCache.getParentFile().mkdirs(); // just in case cache/ doesn't exist jet
			branchheadsCache.createNewFile();
		}
		if (!branchheadsCache.canWrite()) {
			return;
		}
		final int lastRev = repo.getChangelog().getLastRevision();
		final Nodeid lastNid = repo.getChangelog().getRevision(lastRev);
		BufferedWriter bw = new BufferedWriter(new FileWriter(branchheadsCache));
		bw.write(lastNid.toString());
		bw.write((int) ' ');
		bw.write(Integer.toString(lastRev));
		bw.write("\n");
		for (BranchInfo bi : branches.values()) {
			for (Nodeid nid : bi.getHeads()) {
				bw.write(nid.toString());
				bw.write((int) ' ');
				bw.write(bi.getName());
				bw.write("\n");
			}
		}
		bw.close();
	}

	private File getCacheFile() {
		// prior to 1.8 used to be .hg/branchheads.cache
		return new File(repo.getRepositoryRoot(), "cache/branchheads");
	}

	public static class BranchInfo {
		private final String name;
		private List<Nodeid> heads;
		private boolean closed;
		private final Nodeid start;
		private List<Nodeid> closedHeads; // subset of heads, those that bear 'closed' flag, or null if closed == true

		// XXX in fact, few but not all branchHeads might be closed, and isClosed for whole branch is not
		// possible to determine.
		BranchInfo(String branchName, Nodeid first, Nodeid[] branchHeads) {
			name = branchName;
			start = first;
			heads = Arrays.asList(branchHeads);
		}
		
		// incomplete branch, there's not enough information at the time of creation. shall be replaced with
		// proper BI in #collect()
		BranchInfo(String branchName, Nodeid[] branchHeads) {
			this(branchName, Nodeid.NULL, branchHeads);
		}
		
		void validate(HgChangelog clog, HgChangelog.RevisionMap rmap) throws HgInvalidControlFileException {
			int[] localCset = new int[heads.size()];
			int i = 0;
			for (Nodeid h : heads) {
				localCset[i++] = rmap.revisionIndex(h);
			}
			// [0] tipmost, [1] tipmost open
			final Nodeid[] tipmost = new Nodeid[] {null, null};
			final boolean[] allClosed = new boolean[] { true };
			final ArrayList<Nodeid> _closedHeads = new ArrayList<Nodeid>(heads.size());
			clog.range(new HgChangelog.Inspector() {
				
				public void next(int revisionNumber, Nodeid nodeid, RawChangeset cset) {
					assert heads.contains(nodeid);
					tipmost[0] = nodeid;
					if (!"1".equals(cset.extras().get("close"))) {
						tipmost[1] = nodeid;
						allClosed[0] = false;
					} else {
						_closedHeads.add(nodeid);
					}
				}
			}, localCset);
			closed = allClosed[0];
			Nodeid[] outcome = new Nodeid[localCset.length];
			i = 0;
			if (!closed && tipmost[1] != null) { 
				outcome[i++] = tipmost[1];
				if (i < outcome.length && !tipmost[0].equals(tipmost[1])) {
					outcome[i++] = tipmost[0];
				}
			} else {
				outcome[i++] = tipmost[0];
			}
			for (Nodeid h : heads) {
				if (!h.equals(tipmost[0]) && !h.equals(tipmost[1])) {
					outcome[i++] = h;
				}
			}
			heads = Arrays.asList(outcome);
			if (closed) {
				// no need
				closedHeads = null;
			} else {
				_closedHeads.trimToSize();
				closedHeads = _closedHeads;
			}
		}

		public String getName() {
			return name;
		}
		/**
		 * @return <code>true</code> if all heads of this branch are marked as closed
		 */
		public boolean isClosed() {
			return closed;
		}

		/**
		 * @return all heads for the branch, both open and closed, tip-most head first
		 */
		public List<Nodeid> getHeads() {
			return heads;
		}

		/**
		 * 
		 * @param head one of revision from {@link #getHeads() heads} of this branch 
		 * @return true if this particular head is closed
		 * @throws IllegalArgumentException if argument is not from {@link #getHeads() heads} of this branch
		 */
		public boolean isClosed(Nodeid head) {
			if (!heads.contains(head)) {
				throw new IllegalArgumentException(String.format("Revision %s does not belong to heads of %s branch", head, name), null);
			}
			if (closed) {
				return true;
			}
			return closedHeads.contains(head);
		}
//		public Nodeid getTip() {
//		}
		/*public*/ Nodeid getStart() {
			// first node where branch appears
			return start;
		}
	}
}
