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

import java.io.File;

import org.tmatesoft.hg.core.HgBadStateException;
import org.tmatesoft.hg.core.HgInvalidFileException;
import org.tmatesoft.hg.internal.Experimental;
import org.tmatesoft.hg.util.Path;

/**
 * WORK IN PROGRESS, DO NOT USE
 * @author Artem Tikhomirov
 * @author TMate Software Ltd.
 */
@Experimental(reason="Work in progress")
public class HgSubrepoLocation {
	
	private final HgRepository owner;
	private final Kind kind;
	private final Path location;
	private final String source;
	private final String revInfo;

	public enum Kind { Hg, SVN, Git, }
	
	public HgSubrepoLocation(HgRepository parentRepo, String repoLocation, String actualLocation, Kind type, String revision) {
		owner = parentRepo;
		location = Path.create(repoLocation);
		source = actualLocation;
		kind = type;
		revInfo = revision;
	}
	
	// as defined in .hgsub, key value
	public Path getLocation() {
		return location;
	}

	// value from .hgsub
	public String getSource() {
		return source;
	}
	
	public Kind getType() {
		return kind;
	}
	
	public String getRevision() {
		return revInfo;
	}

	/**
	 * @return whether this sub repository is known only locally
	 */
	public boolean isCommitted() {
		return revInfo != null;
	}
	
	/**
	 * @return <code>true</code> when there are local changes in the sub repository
	 */
	public boolean hasChanges() {
		throw HgRepository.notImplemented();
	}
	
//	public boolean isLocal() {
//	}
	
	public HgRepository getOwner() {
		return owner;
	}

	public HgRepository getRepo() throws HgInvalidFileException {
		if (kind != Kind.Hg) {
			throw new HgBadStateException();
		}
		return new HgLookup().detect(new File(owner.getWorkingDir(), source));
	}
}
