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

import org.tmatesoft.hg.core.HgInvalidControlFileException;
import org.tmatesoft.hg.repo.HgChangelog.RawChangeset;
import org.tmatesoft.hg.repo.HgDataFile;
import org.tmatesoft.hg.repo.HgInternals;
import org.tmatesoft.hg.repo.HgRepository;
import org.tmatesoft.hg.util.Path;

/**
 *
 * @author Artem Tikhomirov
 * @author TMate Software Ltd.
 */
public class ChangelogHelper {
	private final int leftBoundary;
	private final HgRepository repo;
	private final IntMap<RawChangeset> cache = new IntMap<RawChangeset>(32);
	private String nextCommitAuthor;

	/**
	 * @param hgRepo
	 * @param leftBoundaryRevision walker never visits revisions with local numbers less than specified,
	 * IOW only revisions [leftBoundaryRevision..TIP] are considered.
	 */
	public ChangelogHelper(HgRepository hgRepo, int leftBoundaryRevision) {
		repo = hgRepo;
		leftBoundary = leftBoundaryRevision;
	}
	
	/**
	 * @return the repo
	 */
	public HgRepository getRepo() {
		return repo;
	}

	/**
	 * Walks changelog in reverse order
	 * @param file
	 * @return changeset where specified file is mentioned among affected files, or <code>null</code> if none found up to leftBoundary
	 */
	public RawChangeset findLatestChangeWith(Path file) throws HgInvalidControlFileException {
		HgDataFile df = repo.getFileNode(file);
		if (!df.exists()) {
			return null;
		}
		int changelogRev = df.getChangesetRevisionIndex(HgRepository.TIP);
		if (changelogRev >= leftBoundary) {
			// the method is likely to be invoked for different files, 
			// while changesets might be the same. Cache 'em not to read too much. 
			RawChangeset cs = cache.get(changelogRev);
			if (cs == null) {
				cs = repo.getChangelog().range(changelogRev, changelogRev).get(0);
				cache.put(changelogRev, cs);
			}
			return cs;
		}
		return null;
	}

	public String getNextCommitUsername() {
		if (nextCommitAuthor == null) {
			nextCommitAuthor = new HgInternals(repo).getNextCommitUsername();
		}
		return nextCommitAuthor;
	}
}
