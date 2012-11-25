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

import java.io.File;
import java.util.Date;

import org.tmatesoft.hg.internal.ChangelogHelper;
import org.tmatesoft.hg.repo.HgChangelog.RawChangeset;
import org.tmatesoft.hg.util.Path;

/**
 * Repository file status and extra handy information.
 *
 * @author Artem Tikhomirov
 * @author TMate Software Ltd.
 */
public class HgStatus {

	public enum Kind {
		Modified, Added, Removed, Missing, Unknown, Clean, Ignored
		// I'd refrain from changing order of these constants, just in case someone (erroneously, of course ;), uses .ordinal()
	};

	private final HgStatus.Kind kind;
	private final Path path;
	private final Path origin;
	private final ChangelogHelper logHelper;
		
	HgStatus(HgStatus.Kind kind, Path path, ChangelogHelper changelogHelper) {
		this(kind, path, null, changelogHelper);
	}

	HgStatus(HgStatus.Kind kind, Path path, Path copyOrigin, ChangelogHelper changelogHelper) {
		this.kind = kind;
		this.path  = path;
		origin = copyOrigin;
		logHelper = changelogHelper;
	}

	public HgStatus.Kind getKind() {
		return kind;
	}

	public Path getPath() {
		return path;
	}

	public Path getOriginalPath() {
		return origin;
	}

	public boolean isCopy() {
		return origin != null;
	}

	/**
	 * @return <code>null</code> if author for the change can't be deduced (e.g. for clean files it's senseless)
	 */
	public String getModificationAuthor() throws HgInvalidControlFileException {
		RawChangeset cset = logHelper.findLatestChangeWith(path);
		if (cset == null) {
			if (kind == Kind.Modified || kind == Kind.Added || kind == Kind.Removed /*&& RightBoundary is TIP*/) {
				// perhaps, also for Kind.Missing?
				return logHelper.getNextCommitUsername();
			}
		} else {
			return cset.user();
		}
		return null;
	}

	public Date getModificationDate() throws HgInvalidControlFileException {
		RawChangeset cset = logHelper.findLatestChangeWith(path);
		if (cset == null) {
			File localFile = new File(logHelper.getRepo().getWorkingDir(), path.toString());
			if (localFile.canRead()) {
				return new Date(localFile.lastModified());
			}
			// FIXME check dirstate and/or local file for tstamp
			return new Date(); // what's correct 
		} else {
			return cset.date();
		}
	}
}