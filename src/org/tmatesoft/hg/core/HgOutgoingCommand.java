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

import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.tmatesoft.hg.internal.RepositoryComparator;
import org.tmatesoft.hg.repo.HgChangelog;
import org.tmatesoft.hg.repo.HgRemoteRepository;
import org.tmatesoft.hg.repo.HgRepository;
import org.tmatesoft.hg.util.CancelSupport;
import org.tmatesoft.hg.util.CancelledException;
import org.tmatesoft.hg.util.ProgressSupport;

/**
 * Command to find out changes made in a local repository and missing at remote repository. 
 * 
 * @author Artem Tikhomirov
 * @author TMate Software Ltd.
 */
public class HgOutgoingCommand extends HgAbstractCommand<HgOutgoingCommand> {

	private final HgRepository localRepo;
	private HgRemoteRepository remoteRepo;
	@SuppressWarnings("unused")
	private boolean includeSubrepo;
	private RepositoryComparator comparator;
	private HgChangelog.ParentWalker parentHelper;
	private Set<String> branches;

	public HgOutgoingCommand(HgRepository hgRepo) {
		localRepo = hgRepo;
	}

	/**
	 * @param hgRemote remoteRepository to compare against
	 * @return <code>this</code> for convenience
	 */
	public HgOutgoingCommand against(HgRemoteRepository hgRemote) {
		remoteRepo = hgRemote;
		comparator = null;
		return this;
	}

	/**
	 * Select specific branch to pull. 
	 * Multiple branch specification possible (changeset from any of these would be included in result).
	 * Note, {@link #executeLite(Object)} does not respect this setting.
	 * 
	 * @param branch - branch name, case-sensitive, non-null.
	 * @return <code>this</code> for convenience
	 * @throws IllegalArgumentException when branch argument is null
	 */
	public HgOutgoingCommand branch(String branch) {
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
	 * @return <code>this</code> for convenience
	 */
	public HgOutgoingCommand subrepo(boolean include) {
		includeSubrepo = include;
		throw HgRepository.notImplemented();
	}

	/**
	 * Lightweight check for outgoing changes. 
	 * Reported changes are from any branch (limits set by {@link #branch(String)} are not taken into account.
	 * 
	 * @return list on local nodes known to be missing at remote server 
	 * @throws HgRemoteConnectionException when failed to communicate with remote repository
	 * @throws HgInvalidControlFileException if access to revlog index/data entry failed
	 * @throws CancelledException if execution of the command was cancelled
	 */
	public List<Nodeid> executeLite() throws HgRemoteConnectionException, HgInvalidControlFileException, CancelledException {
		final ProgressSupport ps = getProgressSupport(null);
		try {
			ps.start(10);
			return getComparator(new ProgressSupport.Sub(ps, 5), getCancelSupport(null, true)).getLocalOnlyRevisions();
		} finally {
			ps.done();
		}
	}

	/**
	 * Complete information about outgoing changes
	 * 
	 * @param handler delegate to process changes
	 * @throws HgRemoteConnectionException when failed to communicate with remote repository
	 * @throws HgInvalidControlFileException if access to revlog index/data entry failed
	 * @throws HgCallbackTargetException to re-throw exception from the handler
	 * @throws CancelledException if execution of the command was cancelled
	 */
	public void executeFull(final HgChangesetHandler handler) throws HgRemoteConnectionException, HgInvalidControlFileException, HgCallbackTargetException, CancelledException {
		if (handler == null) {
			throw new IllegalArgumentException("Delegate can't be null");
		}
		final ProgressSupport ps = getProgressSupport(handler);
		final CancelSupport cs = getCancelSupport(handler, true);
		try {
			ps.start(-1);
			ChangesetTransformer inspector = new ChangesetTransformer(localRepo, handler, getParentHelper(), ps, cs);
			inspector.limitBranches(branches);
			getComparator(new ProgressSupport.Sub(ps, 1), cs).visitLocalOnlyRevisions(inspector);
			inspector.checkFailure();
		} finally {
			ps.done();
		}
	}

	private RepositoryComparator getComparator(ProgressSupport ps, CancelSupport cs) throws HgRemoteConnectionException, HgInvalidControlFileException, CancelledException {
		if (remoteRepo == null) {
			throw new IllegalArgumentException("Shall specify remote repository to compare against");
		}
		if (comparator == null) {
			comparator = new RepositoryComparator(getParentHelper(), remoteRepo);
			comparator.compare(ps, cs);
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

}
