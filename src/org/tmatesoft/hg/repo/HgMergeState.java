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

import static org.tmatesoft.hg.core.Nodeid.NULL;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.tmatesoft.hg.core.HgBadStateException;
import org.tmatesoft.hg.core.HgFileRevision;
import org.tmatesoft.hg.core.HgInvalidControlFileException;
import org.tmatesoft.hg.core.Nodeid;
import org.tmatesoft.hg.internal.ManifestRevision;
import org.tmatesoft.hg.internal.Pool;
import org.tmatesoft.hg.util.Pair;
import org.tmatesoft.hg.util.Path;
import org.tmatesoft.hg.util.PathPool;
import org.tmatesoft.hg.util.PathRewrite;

/**
 *
 * @author Artem Tikhomirov
 * @author TMate Software Ltd.
 */
public class HgMergeState {
	private Nodeid wcp1, wcp2, stateParent;
	
	public enum Kind {
		Resolved, Unresolved;
	}
	
	public static class Entry {
		private final Kind state;
		private final HgFileRevision parent1;
		private final HgFileRevision parent2;
		private final HgFileRevision ancestor;
		private final Path wcFile;

		/*package-local*/Entry(Kind s, Path actualCopy, HgFileRevision p1, HgFileRevision p2, HgFileRevision ca) {
			if (p1 == null || p2 == null || ca == null || actualCopy == null) {
				throw new IllegalArgumentException();
			}
			state = s;
			wcFile = actualCopy;
			parent1 = p1;
			parent2 = p2;
			ancestor = ca;
		}
		
		public Kind getState() {
			return state;
		}
		public Path getActualFile() {
			return wcFile;
		}
		public HgFileRevision getFirstParent() {
			return parent1;
		}
		public HgFileRevision getSecondParent() {
			return parent2;
		}
		public HgFileRevision getCommonAncestor() {
			return ancestor;
		}
	}

	private final HgRepository repo;
	private Entry[] entries;

	HgMergeState(HgRepository hgRepo) {
		repo = hgRepo;
	}

	public void refresh() throws HgInvalidControlFileException {
		entries = null;
		// it's possible there are two parents but no merge/state, we shall report this case as 'merging', with proper
		// first and second parent values
		stateParent = Nodeid.NULL;
		Pool<Nodeid> nodeidPool = new Pool<Nodeid>();
		Pool<Path> fnamePool = new Pool<Path>();
		Pair<Nodeid, Nodeid> wcParents = repo.getWorkingCopyParents();
		wcp1 = nodeidPool.unify(wcParents.first()); wcp2 = nodeidPool.unify(wcParents.second());
		final File f = new File(repo.getRepositoryRoot(), "merge/state");
		if (!f.canRead()) {
			// empty state
			return;
		}
		try {
			ArrayList<Entry> result = new ArrayList<Entry>();
			// FIXME need to settle use of Pool<Path> and PathPool
			// latter is pool that can create objects on demand, former is just cache
			PathPool pathPool = new PathPool(new PathRewrite.Empty()); 
			final ManifestRevision m1 = new ManifestRevision(nodeidPool, fnamePool);
			final ManifestRevision m2 = new ManifestRevision(nodeidPool, fnamePool);
			if (!wcp2.isNull()) {
				final int rp2 = repo.getChangelog().getRevisionIndex(wcp2);
				repo.getManifest().walk(rp2, rp2, m2);
			}
			BufferedReader br = new BufferedReader(new FileReader(f));
			String s = br.readLine();
			stateParent = nodeidPool.unify(Nodeid.fromAscii(s));
			final int rp1 = repo.getChangelog().getRevisionIndex(stateParent);
			repo.getManifest().walk(rp1, rp1, m1);
			while ((s = br.readLine()) != null) {
				String[] r = s.split("\\00");
				Path p1fname = pathPool.path(r[3]);
				Nodeid nidP1 = m1.nodeid(p1fname);
				Nodeid nidCA = nodeidPool.unify(Nodeid.fromAscii(r[5]));
				HgFileRevision p1 = new HgFileRevision(repo, nidP1, p1fname);
				HgFileRevision ca;
				if (nidCA == nidP1 && r[3].equals(r[4])) {
					ca = p1;
				} else {
					ca = new HgFileRevision(repo, nidCA, pathPool.path(r[4]));
				}
				HgFileRevision p2;
				if (!wcp2.isNull() || !r[6].equals(r[4])) {
					final Path p2fname = pathPool.path(r[6]);
					Nodeid nidP2 = m2.nodeid(p2fname);
					if (nidP2 == null) {
						assert false : "There's not enough information (or I don't know where to look) in merge/state to find out what's the second parent";
						nidP2 = NULL;
					}
					p2 = new HgFileRevision(repo, nidP2, p2fname);
				} else {
					// no second parent known. no idea what to do here, assume linear merge, use common ancestor as parent
					p2 = ca;
				}
				final Kind k;
				if ("u".equals(r[1])) {
					k = Kind.Unresolved;
				} else if ("r".equals(r[1])) {
					k = Kind.Resolved;
				} else {
					throw new HgBadStateException(r[1]);
				}
				Entry e = new Entry(k, pathPool.path(r[0]), p1, p2, ca);
				result.add(e);
			}
			entries = result.toArray(new Entry[result.size()]);
			br.close();
			pathPool.clear();
		} catch (IOException ex) {
			throw new HgInvalidControlFileException("Merge state read failed", ex, f);
		}
	}

	/**
	 * Repository is in 'merging' state when changeset to be committed got two parents.
	 * This method doesn't tell whether there are (un)resolved conflicts in the working copy,
	 * use {@link #getConflicts()} (which makes sense only when {@link #isStale()} is <code>false</code>). 
	 * @return <code>true</code> when repository is being merged 
	 */
	public boolean isMerging() {
		return !getFirstParent().isNull() && !getSecondParent().isNull() && !isStale();
	}
	
	/**
	 * Merge state file may not match actual working copy due to rollback or undo operations.
	 * Value of {@link #getConflicts()} is reasonable iff this method returned <code>false</code>.
	 *  
	 * @return <code>true</code> when recorded merge state doesn't seem to correspond to present working copy
	 */
	public boolean isStale() {
		if (wcp1 == null) {
			throw new HgBadStateException("Call #refresh() first");
		}
		return !stateParent.isNull() /*there's merge state*/ && !wcp1.equals(stateParent) /*and it doesn't match*/; 
	}

	/**
	 * It's possible for a repository to be in a 'merging' state (@see {@link #isMerging()} without any
	 * conflict to resolve (no merge state information file).
	 * @return first parent of the working copy, never <code>null</code>
	 */
	public Nodeid getFirstParent() {
		if (wcp1 == null) {
			throw new HgBadStateException("Call #refresh() first");
		}
		return wcp1;
	}
	
	/**
	 * @return second parent of the working copy, never <code>null</code>
	 */
	public Nodeid getSecondParent() {
		if (wcp2 == null) {
			throw new HgBadStateException("Call #refresh() first");
		}
		return wcp2;
	}
	
	/**
	 * @return revision of the merge state or {@link Nodeid#NULL} if there's no merge state
	 */
	public Nodeid getStateParent() {
		if (stateParent == null) {
			throw new HgBadStateException("Call #refresh() first");
		}
		return stateParent;
	}

	/**
	 * List of conflicts as recorded in the merge state information file. 
	 * Note, this information is valid unless {@link #isStale()} is <code>true</code>.
	 * 
	 * @return non-<code>null</code> list with both resolved and unresolved conflicts.
	 */
	public List<Entry> getConflicts() {
		return entries == null ? Collections.<Entry>emptyList() : Arrays.asList(entries);
	}
}
