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

import org.tmatesoft.hg.repo.HgDataFile;
import org.tmatesoft.hg.repo.HgRepository;
import org.tmatesoft.hg.util.ByteChannel;
import org.tmatesoft.hg.util.CancelledException;
import org.tmatesoft.hg.util.Pair;
import org.tmatesoft.hg.util.Path;

/**
 * Keeps together information about specific file revision
 * 
 * @author Artem Tikhomirov
 * @author TMate Software Ltd.
 */
public final class HgFileRevision {
	private final HgRepository repo;
	private final Nodeid revision;
	private final Path path;
	private Path origin;
	private Boolean isCopy = null; // null means not yet known
	private Pair<Nodeid, Nodeid> parents;

	public HgFileRevision(HgRepository hgRepo, Nodeid rev, Path p) {
		if (hgRepo == null || rev == null || p == null) {
			// since it's package local, it is our code to blame for non validated arguments
			throw new IllegalArgumentException();
		}
		repo = hgRepo;
		revision = rev;
		path = p;
	}

	// this cons shall be used when we know whether p was a copy. Perhaps, shall pass Map<Path,Path> instead to stress orig argument is not optional  
	HgFileRevision(HgRepository hgRepo, Nodeid rev, Path p, Path orig) {
		this(hgRepo, rev, p);
		isCopy = Boolean.valueOf(orig == null);
		origin = orig; 
	}
	
	public Path getPath() {
		return path;
	}

	public Nodeid getRevision() {
		return revision;
	}

	public boolean wasCopied() throws HgException {
		if (isCopy == null) {
			checkCopy();
		}
		return isCopy.booleanValue();
	}
	/**
	 * @return <code>null</code> if {@link #wasCopied()} is <code>false</code>, name of the copy source otherwise.
	 */
	public Path getOriginIfCopy() throws HgException {
		if (wasCopied()) {
			return origin;
		}
		return null;
	}

	/**
	 * Access revisions this file revision originates from.
	 * Note, these revisions are records in the file history, not that of the whole repository (aka changeset revisions) 
	 * In most cases, only one parent revision would be present, only for merge revisions one can expect both.
	 * 
	 * @return parent revisions of this file revision, with {@link Nodeid#NULL} for missing values.
	 */
	public Pair<Nodeid, Nodeid> getParents() throws HgInvalidControlFileException {
		if (parents == null) {
			HgDataFile fn = repo.getFileNode(path);
			int revisionIndex = fn.getRevisionIndex(revision);
			int[] pr = new int[2];
			byte[] p1 = new byte[20], p2 = new byte[20];
			// XXX Revlog#parents is not the best method to use here
			// need smth that gives Nodeids (piped through Pool<Nodeid> from repo's context)
			fn.parents(revisionIndex, pr, p1, p2);
			parents = new Pair<Nodeid, Nodeid>(Nodeid.fromBinary(p1, 0), Nodeid.fromBinary(p2, 0));
		}
		return parents;
	}

	public void putContentTo(ByteChannel sink) throws HgDataStreamException, HgInvalidControlFileException, CancelledException {
		HgDataFile fn = repo.getFileNode(path);
		int revisionIndex = fn.getRevisionIndex(revision);
		fn.contentWithFilters(revisionIndex, sink);
	}

	private void checkCopy() throws HgInvalidControlFileException, HgDataStreamException {
		HgDataFile fn = repo.getFileNode(path);
		if (fn.isCopy()) {
			if (fn.getRevision(0).equals(revision)) {
				// this HgFileRevision represents first revision of the copy
				isCopy = Boolean.TRUE;
				origin = fn.getCopySourceName();
				return;
			}
		}
		isCopy = Boolean.FALSE;
	}
}
