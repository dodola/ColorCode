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

import static org.tmatesoft.hg.repo.HgRepository.*;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.net.InetAddress;
import java.net.UnknownHostException;

import org.tmatesoft.hg.core.HgInvalidControlFileException;
import org.tmatesoft.hg.core.HgInvalidRevisionException;
import org.tmatesoft.hg.core.SessionContext;
import org.tmatesoft.hg.internal.Experimental;
import org.tmatesoft.hg.internal.RelativePathRewrite;
import org.tmatesoft.hg.util.FileIterator;
import org.tmatesoft.hg.util.FileWalker;
import org.tmatesoft.hg.util.Path;
import org.tmatesoft.hg.util.PathPool;
import org.tmatesoft.hg.util.PathRewrite;


/**
 * DO NOT USE THIS CLASS, INTENDED FOR TESTING PURPOSES.
 * 
 * This class gives access to repository internals, and holds methods that I'm not confident have to be widely accessible
 * Debug helper, to access otherwise restricted (package-local) methods
 * 
 * @author Artem Tikhomirov
 * @author TMate Software Ltd.
 */
@Experimental(reason="Perhaps, shall split methods with debug purpose from methods that are experimental API")
public class HgInternals {

	private final HgRepository repo;

	public HgInternals(HgRepository hgRepo) {
		repo = hgRepo;
	}

	public HgDirstate getDirstate() throws HgInvalidControlFileException {
		return repo.loadDirstate(new PathPool(new PathRewrite.Empty()));
	}
	
	// tests
	public HgDirstate createDirstate(boolean caseSensitiveFileSystem) throws HgInvalidControlFileException {
		PathRewrite canonicalPath = null;
		if (!caseSensitiveFileSystem) {
			canonicalPath = new PathRewrite() {

				public CharSequence rewrite(CharSequence path) {
					return path.toString().toLowerCase();
				}
			};
		}
		HgDirstate ds = new HgDirstate(repo, new File(repo.getRepositoryRoot(), "dirstate"), new PathPool(new PathRewrite.Empty()), canonicalPath);
		ds.read();
		return ds;
	}
	
	public Path[] checkKnown(HgDirstate dirstate, Path[] toCheck) {
		Path[] rv = new Path[toCheck.length];
		for (int i = 0; i < toCheck.length; i++) {
			rv[i] = dirstate.known(toCheck[i]);
		}
		return rv;
	}

	public static File getRepositoryDir(HgRepository hgRepo) {
		return hgRepo.getRepositoryRoot();
	}
	
	public static HgIgnore newHgIgnore(Reader source) throws IOException {
		HgIgnore hgIgnore = new HgIgnore();
		BufferedReader br = source instanceof BufferedReader ? (BufferedReader) source : new BufferedReader(source);
		hgIgnore.read(br);
		br.close();
		return hgIgnore;
	}

	// in fact, need a setter for this anyway, shall move to internal.Internals perhaps?
	public String getNextCommitUsername() {
		String hgUser = System.getenv("HGUSER");
		if (hgUser != null && hgUser.trim().length() > 0) {
			return hgUser.trim();
		}
		String configValue = repo.getConfiguration().getStringValue("ui", "username", null);
		if (configValue != null) {
			return configValue;
		}
		String email = System.getenv("EMAIL");
		if (email != null && email.trim().length() > 0) {
			return email;
		}
		String username = System.getProperty("user.name");
		try {
			String hostname = InetAddress.getLocalHost().getHostName();
			return username + '@' + hostname; 
		} catch (UnknownHostException ex) {
			return username;
		}
	}
	
	@Experimental(reason="Don't want to expose io.File from HgRepository; need to create FileIterator for working dir. Need a place to keep that code")
	/*package-local*/ FileIterator createWorkingDirWalker(Path.Matcher workindDirScope) {
		File repoRoot = repo.getWorkingDir();
		Path.Source pathSrc = new Path.SimpleSource(new PathRewrite.Composite(new RelativePathRewrite(repoRoot), repo.getToRepoPathHelper()));
		// Impl note: simple source is enough as files in the working dir are all unique
		// even if they might get reused (i.e. after FileIterator#reset() and walking once again),
		// path caching is better to be done in the code which knows that path are being reused 
		return new FileWalker(repoRoot, pathSrc, workindDirScope);
	}
	
	// expose othewise package-local information primarily to use in our own o.t.hg.core package
	public static SessionContext getContext(HgRepository repo) {
		return repo.getContext();
	}


	// Convenient check of revision index for validity (not all negative values are wrong as long as we use negative constants)
	public static boolean wrongRevisionIndex(int rev) {
		return rev < 0 && rev != TIP && rev != WORKING_COPY && rev != BAD_REVISION; 
	}

	// throws HgInvalidRevisionException or IllegalArgumentException if [start..end] range is not a subrange of [0..lastRevision]
	public static void checkRevlogRange(int start, int end, int lastRevision) throws HgInvalidRevisionException {
		if (start < 0 || start > lastRevision) {
			final String m = String.format("Bad left range boundary %d in [0..%d]", start, lastRevision);
			throw new HgInvalidRevisionException(m, null, start).setRevisionIndex(start, 0, lastRevision);
		}
		if (end < 0 || end > lastRevision) {
			final String m = String.format("Bad right range boundary %d in [0..%d]", end, lastRevision);
			throw new HgInvalidRevisionException(m, null, end).setRevisionIndex(end, 0, lastRevision);
		}
		if (end < start) {
			throw new IllegalArgumentException(String.format("Bad range [%d..%d]", start, end));
		}
	}
}
