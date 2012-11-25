/*
 * Copyright (c) 2010-2011 TMate Software Ltd
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
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeSet;

import org.tmatesoft.hg.core.HgInvalidControlFileException;
import org.tmatesoft.hg.core.Nodeid;
import org.tmatesoft.hg.internal.DataAccess;
import org.tmatesoft.hg.util.Pair;
import org.tmatesoft.hg.util.Path;
import org.tmatesoft.hg.util.PathPool;
import org.tmatesoft.hg.util.PathRewrite;


/**
 * @see http://mercurial.selenic.com/wiki/DirState
 * @see http://mercurial.selenic.com/wiki/FileFormats#dirstate
 *
 * @author Artem Tikhomirov
 * @author TMate Software Ltd.
 */
public final class HgDirstate /* XXX RepoChangeListener */{
	
	public enum EntryKind {
		Normal, Added, Removed, Merged, // order is being used in code of this class, don't change unless any use is checked
	}

	private final HgRepository repo;
	private final File dirstateFile;
	private final PathPool pathPool;
	private final PathRewrite canonicalPathRewrite;
	private Map<Path, Record> normal;
	private Map<Path, Record> added;
	private Map<Path, Record> removed;
	private Map<Path, Record> merged;
	/* map of canonicalized file names to their originals from dirstate file.
	 * Note, only those canonical names that differ from their dirstate counterpart are recorded here
	 */
	private Map<Path, Path> canonical2dirstateName; 
	private Pair<Nodeid, Nodeid> parents;
	private String currentBranch;
	
	// canonicalPath may be null if we don't need to check for names other than in dirstate
	/*package-local*/ HgDirstate(HgRepository hgRepo, File dirstate, PathPool pathPool, PathRewrite canonicalPath) {
		repo = hgRepo;
		dirstateFile = dirstate; // XXX decide whether file names shall be kept local to reader (see #branches()) or passed from outside
		this.pathPool = pathPool;
		canonicalPathRewrite = canonicalPath;
	}

	/*package-local*/ void read() throws HgInvalidControlFileException {
		normal = added = removed = merged = Collections.<Path, Record>emptyMap();
		parents = new Pair<Nodeid,Nodeid>(Nodeid.NULL, Nodeid.NULL);
		if (canonicalPathRewrite != null) {
			canonical2dirstateName = new HashMap<Path,Path>();
		} else {
			canonical2dirstateName = Collections.emptyMap();
		}
		if (dirstateFile == null || !dirstateFile.exists()) {
			return;
		}
		DataAccess da = repo.getDataAccess().create(dirstateFile);
		if (da.isEmpty()) {
			return;
		}
		// not sure linked is really needed here, just for ease of debug
		normal = new LinkedHashMap<Path, Record>();
		added = new LinkedHashMap<Path, Record>();
		removed = new LinkedHashMap<Path, Record>();
		merged = new LinkedHashMap<Path, Record>();
		try {
			parents = internalReadParents(da);
			// hg init; hg up produces an empty repository where dirstate has parents (40 bytes) only
			while (!da.isEmpty()) {
				final byte state = da.readByte();
				final int fmode = da.readInt();
				final int size = da.readInt();
				final int time = da.readInt();
				final int nameLen = da.readInt();
				String fn1 = null, fn2 = null;
				byte[] name = new byte[nameLen];
				da.readBytes(name, 0, nameLen);
				for (int i = 0; i < nameLen; i++) {
					if (name[i] == 0) {
						fn1 = new String(name, 0, i, "UTF-8"); // XXX unclear from documentation what encoding is used there
						fn2 = new String(name, i+1, nameLen - i - 1, "UTF-8"); // need to check with different system codepages
						break;
					}
				}
				if (fn1 == null) {
					fn1 = new String(name);
				}
				Record r = new Record(fmode, size, time, pathPool.path(fn1), fn2 == null ? null : pathPool.path(fn2));
				if (canonicalPathRewrite != null) {
					Path canonicalPath = pathPool.path(canonicalPathRewrite.rewrite(fn1).toString());
					if (canonicalPath != r.name()) { // == as they come from the same pool
						assert !canonical2dirstateName.containsKey(canonicalPath); // otherwise there's already a file with same canonical name
						// which can't happen for case-insensitive file system (or there's erroneous PathRewrite, perhaps doing smth else)
						canonical2dirstateName.put(canonicalPath, r.name());
					}
					if (fn2 != null) {
						// not sure I need copy origin in the map, I don't seem to use it anywhere,
						// but I guess I'll have to use it some day.
						canonicalPath = pathPool.path(canonicalPathRewrite.rewrite(fn2).toString());
						if (canonicalPath != r.copySource()) {
							canonical2dirstateName.put(canonicalPath, r.copySource());
						}
					}
				}
				if (state == 'n') {
					normal.put(r.name1, r);
				} else if (state == 'a') {
					added.put(r.name1, r);
				} else if (state == 'r') {
					removed.put(r.name1, r);
				} else if (state == 'm') {
					merged.put(r.name1, r);
				} else {
					repo.getContext().getLog().warn(getClass(), "Dirstate record for file %s (size: %d, tstamp:%d) has unknown state '%c'", r.name1, r.size(), r.time, state);
				}
			}
		} catch (IOException ex) {
			throw new HgInvalidControlFileException("Dirstate read failed", ex, dirstateFile); 
		} finally {
			da.done();
		}
	}

	private static Pair<Nodeid, Nodeid> internalReadParents(DataAccess da) throws IOException {
		byte[] parents = new byte[40];
		da.readBytes(parents, 0, 40);
		Nodeid n1 = Nodeid.fromBinary(parents, 0);
		Nodeid n2 = Nodeid.fromBinary(parents, 20);
		parents = null;
		return new Pair<Nodeid, Nodeid>(n1, n2);
	}
	
	/**
	 * @return pair of working copy parents, with {@link Nodeid#NULL} for missing values.
	 */
	public Pair<Nodeid,Nodeid> parents() {
		assert parents != null; // instance not initialized with #read()
		return parents;
	}
	
	/**
	 * @return pair of parents, both {@link Nodeid#NULL} if dirstate is not available
	 */
	/*package-local*/ static Pair<Nodeid, Nodeid> readParents(HgRepository repo, File dirstateFile) throws HgInvalidControlFileException {
		// do not read whole dirstate if all we need is WC parent information
		if (dirstateFile == null || !dirstateFile.exists()) {
			return new Pair<Nodeid,Nodeid>(NULL, NULL);
		}
		DataAccess da = repo.getDataAccess().create(dirstateFile);
		if (da.isEmpty()) {
			return new Pair<Nodeid,Nodeid>(NULL, NULL);
		}
		try {
			return internalReadParents(da);
		} catch (IOException ex) {
			throw new HgInvalidControlFileException("Error reading working copy parents from dirstate", ex, dirstateFile);
		} finally {
			da.done();
		}
	}
	
	/**
	 * FIXME move to a better place, e.g. WorkingCopy container that tracks both dirstate and branches (and, perhaps, undo, lastcommit and other similar information)
	 * @return branch associated with the working directory
	 */
	public String branch() throws HgInvalidControlFileException {
		// XXX is it really proper place for the method?
		if (currentBranch == null) {
			currentBranch = readBranch(repo);
		}
		return currentBranch;
	}
	
	/**
	 * XXX is it really proper place for the method?
	 * @return branch associated with the working directory
	 */
	/*package-local*/ static String readBranch(HgRepository repo) throws HgInvalidControlFileException {
		String branch = HgRepository.DEFAULT_BRANCH_NAME;
		File branchFile = new File(repo.getRepositoryRoot(), "branch");
		if (branchFile.exists()) {
			try {
				BufferedReader r = new BufferedReader(new FileReader(branchFile));
				String b = r.readLine();
				if (b != null) {
					b = b.trim().intern();
				}
				branch = b == null || b.length() == 0 ? HgRepository.DEFAULT_BRANCH_NAME : b;
				r.close();
			} catch (FileNotFoundException ex) {
				repo.getContext().getLog().debug(HgDirstate.class, ex, null); // log verbose debug, exception might be legal here 
				// IGNORE
			} catch (IOException ex) {
				throw new HgInvalidControlFileException("Error reading file with branch information", ex, branchFile);
			}
		}
		return branch;
	}

	// new, modifiable collection
	/*package-local*/ TreeSet<Path> all() {
		assert normal != null;
		TreeSet<Path> rv = new TreeSet<Path>();
		@SuppressWarnings("unchecked")
		Map<Path, Record>[] all = new Map[] { normal, added, removed, merged };
		for (int i = 0; i < all.length; i++) {
			for (Record r : all[i].values()) {
				rv.add(r.name1);
			}
		}
		return rv;
	}
	
	/*package-local*/ Record checkNormal(Path fname) {
		return internalCheck(normal, fname);
	}

	/*package-local*/ Record checkAdded(Path fname) {
		return internalCheck(added, fname);
	}
	/*package-local*/ Record checkRemoved(Path fname) {
		return internalCheck(removed, fname);
	}
	/*package-local*/ Record checkMerged(Path fname) {
		return internalCheck(merged, fname);
	}

	
	// return non-null if fname is known, either as is, or its canonical form. in latter case, this canonical form is return value
	/*package-local*/ Path known(Path fname) {
		Path fnameCanonical = null;
		if (canonicalPathRewrite != null) {
			fnameCanonical = pathPool.path(canonicalPathRewrite.rewrite(fname).toString());
			if (fnameCanonical != fname && canonical2dirstateName.containsKey(fnameCanonical)) {
				// we know right away there's name in dirstate with alternative canonical form
				return canonical2dirstateName.get(fnameCanonical);
			}
		}
		@SuppressWarnings("unchecked")
		Map<Path, Record>[] all = new Map[] { normal, added, removed, merged };
		for (int i = 0; i < all.length; i++) {
			if (all[i].containsKey(fname)) {
				return fname;
			}
			if (fnameCanonical != null && all[i].containsKey(fnameCanonical)) {
				return fnameCanonical;
			}
		}
		return null;
	}

	private Record internalCheck(Map<Path, Record> map, Path fname) {
		Record rv = map.get(fname);
		if (rv != null || canonicalPathRewrite == null) {
			return rv;
		}
		Path fnameCanonical = pathPool.path(canonicalPathRewrite.rewrite(fname).toString());
		if (fnameCanonical != fname) {
			// case when fname = /a/B/c, and dirstate is /a/b/C 
			if (canonical2dirstateName.containsKey(fnameCanonical)) {
				return map.get(canonical2dirstateName.get(fnameCanonical));
			}
			// try canonical directly, fname = /a/B/C, dirstate has /a/b/c
			if ((rv = map.get(fnameCanonical)) != null) {
				return rv;
			}
		}
		return null;
	}

	public void walk(Inspector inspector) {
		assert normal != null;
		@SuppressWarnings("unchecked")
		Map<Path, Record>[] all = new Map[] { normal, added, removed, merged };
		for (int i = 0; i < all.length; i++) {
			EntryKind k = EntryKind.values()[i];
			for (Record r : all[i].values()) {
				if (!inspector.next(k, r)) {
					return;
				}
			}
		}
	}

	public interface Inspector {
		/**
		 * Invoked for each entry in the directory state file
		 * @param kind file record kind
		 * @param entry file record. Note, do not cache instance as it may be reused between the calls
		 * @return <code>true</code> to indicate further records are still of interest, <code>false</code> to stop iteration
		 */
		boolean next(EntryKind kind, Record entry);
	}

	public static final class Record implements Cloneable {
		private final int mode, size, time;
		// Dirstate keeps local file size (i.e. that with any filters already applied). 
		// Thus, can't compare directly to HgDataFile.length()
		private final Path name1, name2;

		/*package-local*/ Record(int fmode, int fsize, int ftime, Path name1, Path name2) {
			mode = fmode;
			size = fsize;
			time = ftime;
			this.name1 = name1;
			this.name2 = name2;
			
		}

		public Path name() {
			return name1;
		}

		/**
		 * @return non-<code>null</code> for copy/move
		 */
		public Path copySource() {
			return name2;
		}

		public int modificationTime() {
			return time;
		}

		public int size() {
			return size;
		}
		
		public int mode() {
			return mode;
		}
		
		@Override
		public Record clone()  {
			try {
				return (Record) super.clone();
			} catch (CloneNotSupportedException ex) {
				throw new InternalError(ex.toString());
			}
		}
	}
}
