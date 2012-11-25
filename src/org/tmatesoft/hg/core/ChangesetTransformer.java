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

import java.util.Set;

import org.tmatesoft.hg.repo.HgChangelog;
import org.tmatesoft.hg.repo.HgChangelog.RawChangeset;
import org.tmatesoft.hg.repo.HgRepository;
import org.tmatesoft.hg.repo.HgStatusCollector;
import org.tmatesoft.hg.util.CancelSupport;
import org.tmatesoft.hg.util.CancelledException;
import org.tmatesoft.hg.util.PathPool;
import org.tmatesoft.hg.util.PathRewrite;
import org.tmatesoft.hg.util.ProgressSupport;

/**
 * Bridges {@link HgChangelog.RawChangeset} with high-level {@link HgChangeset} API
 * FIXME move to .internal once access to package-local HgChangeset cons is resolved
 * 
 * @author Artem Tikhomirov
 * @author TMate Software Ltd.
 */
/*package-local*/ class ChangesetTransformer implements HgChangelog.Inspector {
	private final HgChangesetHandler handler;
	private final ProgressSupport progressHelper;
	private final CancelSupport cancelHelper;
	private final Transformation t;
	private Set<String> branches;
	private HgCallbackTargetException failure;
	private CancelledException cancellation;

	// repo and delegate can't be null, parent walker can
	// ps and cs can't be null
	public ChangesetTransformer(HgRepository hgRepo, HgChangesetHandler delegate, HgChangelog.ParentWalker pw, ProgressSupport ps, CancelSupport cs) {
		if (hgRepo == null || delegate == null) {
			throw new IllegalArgumentException();
		}
		if (ps == null || cs == null) {
			throw new IllegalArgumentException();
		}
		HgStatusCollector statusCollector = new HgStatusCollector(hgRepo);
		t = new Transformation(statusCollector, pw);
		handler = delegate;
		cancelHelper = cs;
		progressHelper = ps;
	}
	
	public void next(int revisionNumber, Nodeid nodeid, RawChangeset cset) {
		if (failure != null || cancellation != null) {
			return; // FIXME need a better way to stop iterating revlog 
		}
		if (branches != null && !branches.contains(cset.branch())) {
			return;
		}

		HgChangeset changeset = t.handle(revisionNumber, nodeid, cset);
		try {
			handler.next(changeset);
			progressHelper.worked(1);
			cancelHelper.checkCancelled();
		} catch (RuntimeException ex) {
			failure = new HgCallbackTargetException(ex).setRevision(nodeid).setRevisionIndex(revisionNumber);
		} catch (CancelledException ex) {
			cancellation = ex;
		}
	}
	
	public void checkFailure() throws HgCallbackTargetException, CancelledException {
		if (failure != null) {
			HgCallbackTargetException toThrow = failure;
			failure = null; // just in (otherwise unexpected) case this instance would get reused
			throw toThrow;
		}
		if (cancellation != null) {
			CancelledException toThrow = cancellation;
			cancellation = null;
			throw toThrow;
		}
	}
	
	public void limitBranches(Set<String> branches) {
		this.branches = branches;
	}

	// part relevant to RawChangeset->HgChangeset transformation
	static class Transformation {
		private final HgChangeset changeset;

		public Transformation(HgStatusCollector statusCollector, HgChangelog.ParentWalker pw) {
			// files listed in a changeset don't need their names to be rewritten (they are normalized already)
			PathPool pp = new PathPool(new PathRewrite.Empty());
			statusCollector.setPathPool(pp);
			changeset = new HgChangeset(statusCollector, pp);
			changeset.setParentHelper(pw);
		}
		
		HgChangeset handle(int revisionNumber, Nodeid nodeid, RawChangeset cset) {
			changeset.init(revisionNumber, nodeid, cset);
			return changeset;
		}
	}
}