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

import org.tmatesoft.hg.internal.ManifestRevision;
import org.tmatesoft.hg.repo.HgDataFile;
import org.tmatesoft.hg.repo.HgInternals;
import org.tmatesoft.hg.repo.HgRepository;
import org.tmatesoft.hg.util.Path;
import org.tmatesoft.hg.util.Status;

/**
 * Primary purpose is to provide information about file revisions at specific changeset. Multiple {@link #check(Path)} calls 
 * are possible once {@link #changeset(Nodeid)} (and optionally, {@link #followRenames(boolean)}) were set.
 * 
 * <p>Sample:
 * <pre><code>
 *   HgFileInformer i = new HgFileInformer(hgRepo).changeset(Nodeid.fromString("<40 digits>")).followRenames(true);
 *   if (i.check(file)) {
 *   	HgCatCommand catCmd = new HgCatCommand(hgRepo).revision(i.getFileRevision());
 *   	catCmd.execute(...);
 *   	...
 *   }
 * </pre></code>
 *
 * FIXME need better name. It's more about manifest of specific changeset, rather than informing (about) files
 * TODO may add #manifest(Nodeid) to select manifest according to its revision (not only changeset revision as it's now) 
 * 
 * @author Artem Tikhomirov
 * @author TMate Software Ltd.
 */
public class HgFileInformer {

	private final HgRepository repo;
	private boolean followRenames;
	private Nodeid cset;
	private ManifestRevision cachedManifest;
	private HgFileRevision fileRevision;
	private boolean renamed;
	private Status checkResult;

	public HgFileInformer(HgRepository hgRepo) {
		repo = hgRepo;
	}

	/**
	 * Select specific changelog revision
	 * 
	 * @param nid changeset identifier
	 * @return <code>this</code> for convenience
	 */
	public HgFileInformer changeset(Nodeid nid) {
		if (nid == null || nid.isNull()) {
			throw new IllegalArgumentException(); 
		}
		cset = nid;
		cachedManifest = null;
		fileRevision = null;
		return this;
	}

	/**
	 * Whether to check file origins, default is false (look up only the name supplied)
	 *
	 * @param follow <code>true</code> to check copy/rename origin of the file if it is a copy.
	 * @return <code>this</code> for convenience
	 */
	public HgFileInformer followRenames(boolean follow) {
		followRenames = follow;
		fileRevision = null;
		return this;
	}

	/**
	 * Shortcut to perform {@link #check(Path)} and {@link #exists()}. Result of the check may be accessed via {@link #getCheckStatus()}.
	 * 
	 * @param file name of the file in question
	 * @return <code>true</code> if file is known at the selected changeset.
	 * @throws IllegalArgumentException if {@link #changeset(Nodeid)} not specified or file argument is bad.
	 * @throws HgInvalidControlFileException if access to revlog index/data entry failed
	 */
	public boolean checkExists(Path file) throws HgInvalidControlFileException {
		check(file);
		if (!checkResult.isOk() && checkResult.getException() instanceof HgInvalidControlFileException) {
			throw (HgInvalidControlFileException) checkResult.getException();
		}
		return checkResult.isOk() && exists();
	}

	/**
	 * Find file (or its origin, if {@link #followRenames(boolean)} was set to <code>true</code>) among files known at specified {@link #changeset(Nodeid)}.
	 * 
	 * @param file name of the file in question
	 * @return status object that describes outcome, {@link Status#isOk() Ok} status indicates successful completion of the operation, but doesn't imply
	 * file existence, use {@link #exists()} for that purpose. Message of the status may provide further hints on what exactly had happened. 
	 * @throws IllegalArgumentException if {@link #changeset(Nodeid)} not specified or file argument is bad.
	 */
	public Status check(Path file) {
		fileRevision = null;
		checkResult = null;
		renamed = false;
		if (cset == null || file == null || file.isDirectory()) {
			throw new IllegalArgumentException();
		}
		HgDataFile dataFile = repo.getFileNode(file);
		if (!dataFile.exists()) {
			checkResult = new Status(Status.Kind.OK, String.format("File named %s is not known in the repository", file));
			return checkResult;
		}
		Nodeid toExtract = null;
		try {
			if (cachedManifest == null) {
				int csetRev = repo.getChangelog().getRevisionIndex(cset);
				cachedManifest = new ManifestRevision(null, null); // XXX how about context and cached manifest revisions
				repo.getManifest().walk(csetRev, csetRev, cachedManifest);
				// cachedManifest shall be meaningful - changelog.getRevisionIndex() above ensures we've got version that exists.
			}
			toExtract = cachedManifest.nodeid(file);
			if (toExtract == null && followRenames) {
				while (toExtract == null && dataFile.isCopy()) {
					renamed = true;
					file = dataFile.getCopySourceName();
					dataFile = repo.getFileNode(file);
					toExtract = cachedManifest.nodeid(file);
				}
			}
		} catch (HgInvalidControlFileException ex) {
			checkResult = new Status(Status.Kind.ERROR, "", ex);
			return checkResult;
		} catch (HgDataStreamException ex) {
			checkResult = new Status(Status.Kind.ERROR, "Follow copy/rename failed", ex);
			HgInternals.getContext(repo).getLog().warn(getClass(), ex, checkResult.getMessage());
			return checkResult;
		}
		if (toExtract != null) {
			fileRevision = new HgFileRevision(repo, toExtract, dataFile.getPath());
			checkResult = new Status(Status.Kind.OK, String.format("File %s, revision %s found at changeset %s", dataFile.getPath(), toExtract.shortNotation(), cset.shortNotation()));
			return checkResult;
		} 
		checkResult = new Status(Status.Kind.OK, String.format("File %s nor its origins were known at repository %s revision", file, cset.shortNotation()));
		return checkResult;
	}
	
	/**
	 * Re-get latest check status object
	 */
	public Status getCheckStatus() {
		assertCheckRan();
		return checkResult;
	}
	
	/**
	 * @return result of the last {@link #check(Path)} call.
	 */
	public boolean exists() {
		assertCheckRan();
		return fileRevision != null;
	}
	
	/**
	 * @return <code>true</code> if checked file was known by another name at the time of specified changeset.
	 */
	public boolean hasAnotherName() {
		assertCheckRan();
		return renamed;
	}

	/**
	 * @return holder for file revision information
	 */
	public HgFileRevision getFileRevision() {
		assertCheckRan();
		return fileRevision;
	}

	/**
	 * Name of the checked file as it was known at the time of the specified changeset.
	 * 
	 * @return handy shortcut for <code>getFileRevision().getPath()</code>
	 */
	public Path filename() {
		assertCheckRan();
		return fileRevision.getPath();
	}
	
	/**
	 * Revision of the checked file
	 * 
	 * @return handy shortcut for <code>getFileRevision().getRevision()</code>
	 */
	public Nodeid revision() {
		assertCheckRan();
		return fileRevision.getRevision();
	}

	private void assertCheckRan() {
		if (checkResult == null) {
			throw new HgBadStateException("Shall invoke #check(Path) first");
		}
	}
}
