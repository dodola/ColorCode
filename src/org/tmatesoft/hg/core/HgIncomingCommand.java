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
package org.tmatesoft.hg.core;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.tmatesoft.hg.internal.RepositoryComparator;
import org.tmatesoft.hg.internal.RepositoryComparator.BranchChain;
import org.tmatesoft.hg.repo.HgBundle;
import org.tmatesoft.hg.repo.HgChangelog;
import org.tmatesoft.hg.repo.HgChangelog.RawChangeset;
import org.tmatesoft.hg.repo.HgRemoteRepository;
import org.tmatesoft.hg.repo.HgRepository;
import org.tmatesoft.hg.util.CancelledException;
import org.tmatesoft.hg.util.ProgressSupport;

/**
 * Command to find out changes available in a remote repository, missing locally.
 * 
 * @author Artem Tikhomirov
 * @author TMate Software Ltd.
 */
public class HgIncomingCommand extends HgAbstractCommand<HgIncomingCommand> {

	private final HgRepository localRepo;
	private HgRemoteRepository remoteRepo;
	@SuppressWarnings("unused")
	private boolean includeSubrepo;
	private RepositoryComparator comparator;
	private List<BranchChain> missingBranches;
	private HgChangelog.ParentWalker parentHelper;
	private Set<String> branches;

	public HgIncomingCommand(HgRepository hgRepo) {
	 	localRepo = hgRepo;
	}
	
	public HgIncomingCommand against(HgRemoteRepository hgRemote) {
		remoteRepo = hgRemote;
		comparator = null;
		missingBranches = null;
		return this;
	}

	/**
	 * Select specific branch to push.
	 * Multiple branch specification possible (changeset from any of these would be included in result).
	 * Note, {@link #executeLite(Object)} does not respect this setting.
	 * 
	 * @param branch - branch name, case-sensitive, non-null.
	 * @return <code>this</code> for convenience
	 * @throws IllegalArgumentException when branch argument is null
	 */
	public HgIncomingCommand branch(String branch) {
		if (branch == null) {
			throw new IllegalArgumentException();
		}
		if (branches == null) {
			branches = new TreeSet<String>();
		}
		branches.add(branch);
		return this;
	}
	
	/**
	 * PLACEHOLDER, NOT IMPLEMENTED YET.
	 * 
	 * Whether to include sub-repositories when collecting changes, default is <code>true</code> XXX or false?
	 * @return <code>this</code> for convenience
	 */
	public HgIncomingCommand subrepo(boolean include) {
		includeSubrepo = include;
		throw HgRepository.notImplemented();
	}

	/**
	 * Lightweight check for incoming changes, gives only list of revisions to pull.
	 * Reported changes are from any branch (limits set by {@link #branch(String)} are not taken into account. 
	 *   
	 * @return list of nodes present at remote and missing locally
	 * @throws HgRemoteConnectionException when failed to communicate with remote repository
	 * @throws HgInvalidControlFileException if access to revlog index/data entry failed
	 * @throws CancelledException if execution of the command was cancelled
	 */
	public List<Nodeid> executeLite() throws HgRemoteConnectionException, HgInvalidControlFileException, CancelledException {
		LinkedHashSet<Nodeid> result = new LinkedHashSet<Nodeid>();
		RepositoryComparator repoCompare = getComparator();
		for (BranchChain bc : getMissingBranches()) {
			List<Nodeid> missing = repoCompare.visitBranches(bc);
			HashSet<Nodeid> common = new HashSet<Nodeid>(); // ordering is irrelevant  
			repoCompare.collectKnownRoots(bc, common);
			// missing could only start with common elements. Once non-common, rest is just distinct branch revision trails.
			for (Iterator<Nodeid> it = missing.iterator(); it.hasNext() && common.contains(it.next()); it.remove()) ; 
			result.addAll(missing);
		}
		ArrayList<Nodeid> rv = new ArrayList<Nodeid>(result);
		return rv;
	}

	/**
	 * Full information about incoming changes
	 * 
	 * @throws HgRemoteConnectionException when failed to communicate with remote repository
	 * @throws HgInvalidControlFileException if access to revlog index/data entry failed
	 * @throws HgInvalidFileException to indicate failure working with locally downloaded changes in a bundle file
	 * @throws HgCallbackTargetException to re-throw exception from the handler
	 * @throws CancelledException if execution of the command was cancelled
	 */
	public void executeFull(final HgChangesetHandler handler) throws HgRemoteConnectionException, HgInvalidControlFileException, HgInvalidFileException, HgCallbackTargetException, CancelledException {
		if (handler == null) {
			throw new IllegalArgumentException("Delegate can't be null");
		}
		final List<Nodeid> common = getCommon();
		HgBundle changegroup = remoteRepo.getChanges(common);
		final ProgressSupport ps = getProgressSupport(handler);
		try {
			final ChangesetTransformer transformer = new ChangesetTransformer(localRepo, handler, getParentHelper(), ps, getCancelSupport(handler, true));
			transformer.limitBranches(branches);
			changegroup.changes(localRepo, new HgChangelog.Inspector() {
				private int localIndex;
				private final HgChangelog.ParentWalker parentHelper;
			
				{
					parentHelper = getParentHelper();
					// new revisions, if any, would be added after all existing, and would get numbered started with last+1
					localIndex = localRepo.getChangelog().getRevisionCount();
				}
				
				public void next(int revisionNumber, Nodeid nodeid, RawChangeset cset) {
					if (parentHelper.knownNode(nodeid)) {
						if (!common.contains(nodeid)) {
							throw new HgBadStateException("Bundle shall not report known nodes other than roots we've supplied");
						}
						return;
					}
					transformer.next(localIndex++, nodeid, cset);
				}
			});
			transformer.checkFailure();
		} finally {
			ps.done();
		}
	}

	private RepositoryComparator getComparator() throws HgInvalidControlFileException, CancelledException {
		if (remoteRepo == null) {
			throw new IllegalArgumentException("Shall specify remote repository to compare against", null);
		}
		if (comparator == null) {
			comparator = new RepositoryComparator(getParentHelper(), remoteRepo);
//			comparator.compare(context); // XXX meanwhile we use distinct path to calculate common  
		}
		return comparator;
	}
	
	private HgChangelog.ParentWalker getParentHelper() throws HgInvalidControlFileException {
		if (parentHelper == null) {
			parentHelper = localRepo.getChangelog().new ParentWalker();
			parentHelper.init();
		}
		return parentHelper;
	}
	
	private List<BranchChain> getMissingBranches() throws HgRemoteConnectionException, HgInvalidControlFileException, CancelledException {
		if (missingBranches == null) {
			missingBranches = getComparator().calculateMissingBranches();
		}
		return missingBranches;
	}

	private List<Nodeid> getCommon() throws HgRemoteConnectionException, HgInvalidControlFileException, CancelledException {
//		return getComparator(context).getCommon();
		final LinkedHashSet<Nodeid> common = new LinkedHashSet<Nodeid>();
		// XXX common can be obtained from repoCompare, but at the moment it would almost duplicate work of calculateMissingBranches
		// once I refactor latter, common shall be taken from repoCompare.
		RepositoryComparator repoCompare = getComparator();
		for (BranchChain bc : getMissingBranches()) {
			repoCompare.collectKnownRoots(bc, common);
		}
		return new LinkedList<Nodeid>(common);
	}
}
