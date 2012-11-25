/*
 * Copyright (c) 2010-2012 TMate Software Ltd
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
import java.io.IOException;
import java.io.StringReader;
import java.lang.ref.SoftReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import org.tmatesoft.hg.core.HgDataStreamException;
import org.tmatesoft.hg.core.HgInvalidControlFileException;
import org.tmatesoft.hg.core.Nodeid;
import org.tmatesoft.hg.core.SessionContext;
import org.tmatesoft.hg.internal.ByteArrayChannel;
import org.tmatesoft.hg.internal.ConfigFile;
import org.tmatesoft.hg.internal.DataAccessProvider;
import org.tmatesoft.hg.internal.Experimental;
import org.tmatesoft.hg.internal.Filter;
import org.tmatesoft.hg.internal.RevlogStream;
import org.tmatesoft.hg.internal.SubrepoManager;
import org.tmatesoft.hg.util.CancelledException;
import org.tmatesoft.hg.util.Pair;
import org.tmatesoft.hg.util.Path;
import org.tmatesoft.hg.util.PathPool;
import org.tmatesoft.hg.util.PathRewrite;
import org.tmatesoft.hg.util.ProgressSupport;



/**
 * Shall be as state-less as possible, all the caching happens outside the repo, in commands/walkers
 * 
 * @author Artem Tikhomirov
 * @author TMate Software Ltd.
 */
public final class HgRepository {

	// if new constants added, consider fixing HgInternals#wrongRevisionIndex
	public static final int TIP = -3;
	public static final int BAD_REVISION = Integer.MIN_VALUE;
	public static final int WORKING_COPY = -2;
	
	public static final String DEFAULT_BRANCH_NAME = "default";

	// temp aux marker method
	public static IllegalStateException notImplemented() {
		return new IllegalStateException("Not implemented");
	}
	
	private final File repoDir; // .hg folder
	private final File workingDir; // .hg/../
	private final String repoLocation;
	private final DataAccessProvider dataAccess;
	private final PathRewrite normalizePath;
	private final PathRewrite dataPathHelper;
	private final PathRewrite repoPathHelper;
	private final SessionContext sessionContext;

	private HgChangelog changelog;
	private HgManifest manifest;
	private HgTags tags;
	private HgBranches branches;
	private HgMergeState mergeState;
	private SubrepoManager subRepos;

	// XXX perhaps, shall enable caching explicitly
	private final HashMap<Path, SoftReference<RevlogStream>> streamsCache = new HashMap<Path, SoftReference<RevlogStream>>();
	
	private final org.tmatesoft.hg.internal.Internals impl;
	private HgIgnore ignore;
	private HgRepoConfig repoConfig;
	
	HgRepository(String repositoryPath) {
		repoDir = null;
		workingDir = null;
		repoLocation = repositoryPath;
		dataAccess = null;
		dataPathHelper = repoPathHelper = null;
		normalizePath = null;
		sessionContext = null;
		impl = null;
	}
	
	HgRepository(SessionContext ctx, String repositoryPath, File repositoryRoot) {
		assert ".hg".equals(repositoryRoot.getName()) && repositoryRoot.isDirectory();
		assert repositoryPath != null; 
		assert repositoryRoot != null;
		assert ctx != null;
		repoDir = repositoryRoot;
		workingDir = repoDir.getParentFile();
		if (workingDir == null) {
			throw new IllegalArgumentException(repoDir.toString());
		}
		impl = new org.tmatesoft.hg.internal.Internals(ctx);
		repoLocation = repositoryPath;
		sessionContext = ctx;
		dataAccess = new DataAccessProvider(ctx);
		impl.parseRequires(this, new File(repoDir, "requires"));
		normalizePath = impl.buildNormalizePathRewrite(); 
		dataPathHelper = impl.buildDataFilesHelper();
		repoPathHelper = impl.buildRepositoryFilesHelper();
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + "[" + getLocation() + (isInvalid() ? "(BAD)" : "") + "]";
	}                         
	
	public String getLocation() {
		return repoLocation;
	}

	public boolean isInvalid() {
		return repoDir == null || !repoDir.exists() || !repoDir.isDirectory();
	}
	
	public HgChangelog getChangelog() {
		if (changelog == null) {
			CharSequence storagePath = repoPathHelper.rewrite("00changelog.i");
			RevlogStream content = resolve(Path.create(storagePath), true);
			changelog = new HgChangelog(this, content);
		}
		return changelog;
	}
	
	public HgManifest getManifest() {
		if (manifest == null) {
			RevlogStream content = resolve(Path.create(repoPathHelper.rewrite("00manifest.i")), true);
			manifest = new HgManifest(this, content);
		}
		return manifest;
	}
	
	public HgTags getTags() throws HgInvalidControlFileException {
		if (tags == null) {
			tags = new HgTags(this);
			HgDataFile hgTags = getFileNode(".hgtags");
			if (hgTags.exists()) {
				for (int i = 0; i <= hgTags.getLastRevision(); i++) { // FIXME in fact, would be handy to have walk(start,end) 
					// method for data files as well, though it looks odd.
					try {
						ByteArrayChannel sink = new ByteArrayChannel();
						hgTags.content(i, sink);
						final String content = new String(sink.toArray(), "UTF8");
						tags.readGlobal(new StringReader(content));
					} catch (CancelledException ex) {
						 // IGNORE, can't happen, we did not configure cancellation
						getContext().getLog().debug(getClass(), ex, null);
					} catch (HgDataStreamException ex) {
						getContext().getLog().error(getClass(), ex, null);
						// FIXME need to react
					} catch (IOException ex) {
						// UnsupportedEncodingException can't happen (UTF8)
						// only from readGlobal. Need to reconsider exceptions thrown from there:
						// BufferedReader wraps String and unlikely to throw IOException, perhaps, log is enough?
						getContext().getLog().error(getClass(), ex, null);
						// XXX need to decide what to do this. failure to read single revision shall not break complete cycle
					}
				}
			}
			File file2read = null;
			try {
				file2read = new File(getWorkingDir(), ".hgtags");
				tags.readGlobal(file2read); // XXX replace with HgDataFile.workingCopy
				file2read = new File(repoDir, "localtags");
				tags.readLocal(file2read);
			} catch (IOException ex) {
				getContext().getLog().error(getClass(), ex, null);
				throw new HgInvalidControlFileException("Failed to read tags", ex, file2read);
			}
		}
		return tags;
	}
	
	public HgBranches getBranches() throws HgInvalidControlFileException {
		if (branches == null) {
			branches = new HgBranches(this);
			branches.collect(ProgressSupport.Factory.get(null));
		}
		return branches;
	}

	@Experimental(reason="Perhaps, shall not cache instance, and provide loadMergeState as it may change often")
	public HgMergeState getMergeState() {
		if (mergeState == null) {
			mergeState = new HgMergeState(this);
		}
		return mergeState;
	}
	
	public HgDataFile getFileNode(String path) {
		CharSequence nPath = normalizePath.rewrite(path);
		CharSequence storagePath = dataPathHelper.rewrite(nPath);
		RevlogStream content = resolve(Path.create(storagePath), false);
		Path p = Path.create(nPath);
		if (content == null) {
			return new HgDataFile(this, p);
		}
		return new HgDataFile(this, p, content);
	}

	public HgDataFile getFileNode(Path path) {
		CharSequence storagePath = dataPathHelper.rewrite(path.toString());
		RevlogStream content = resolve(Path.create(storagePath), false);
		// XXX no content when no file? or HgDataFile.exists() to detect that?
		if (content == null) {
			return new HgDataFile(this, path);
		}
		return new HgDataFile(this, path, content);
	}

	/* clients need to rewrite path from their FS to a repository-friendly paths, and, perhaps, vice versa*/
	public PathRewrite getToRepoPathHelper() {
		return normalizePath;
	}

	/**
	 * @return pair of values, {@link Pair#first()} and {@link Pair#second()} are respective parents, never <code>null</code>.
	 * @throws HgInvalidControlFileException if attempt to read information about working copy parents from dirstate failed 
	 */
	public Pair<Nodeid,Nodeid> getWorkingCopyParents() throws HgInvalidControlFileException {
		return HgDirstate.readParents(this, new File(repoDir, "dirstate"));
	}
	
	/**
	 * @return name of the branch associated with working directory, never <code>null</code>.
	 * @throws HgInvalidControlFileException if attempt to read branch name failed.
	 */
	public String getWorkingCopyBranchName() throws HgInvalidControlFileException {
		return HgDirstate.readBranch(this);
	}

	/**
	 * @return location where user files (shall) reside
	 */
	public File getWorkingDir() {
		return workingDir;
	}
	
	/**
	 * Provides access to sub-repositories defined in this repository. Enumerated  sub-repositories are those directly
	 * known, not recursive collection of all nested sub-repositories.
	 * @return list of all known sub-repositories in this repository, or empty list if none found.
	 */
	public List<HgSubrepoLocation> getSubrepositories() throws HgInvalidControlFileException {
		if (subRepos == null) {
			subRepos = new SubrepoManager(this);
			subRepos.read();
		}
		return subRepos.all();
	}

	
	public HgRepoConfig getConfiguration() /* XXX throws HgInvalidControlFileException? Description of the exception suggests it is only for files under ./hg/*/ {
		if (repoConfig == null) {
			try {
				ConfigFile configFile = impl.readConfiguration(this, getRepositoryRoot());
				repoConfig = new HgRepoConfig(configFile);
			} catch (IOException ex) {
				String m = "Errors while reading user configuration file";
				getContext().getLog().warn(getClass(), ex, m);
				return new HgRepoConfig(new ConfigFile()); // empty config, do not cache, allow to try once again
				//throw new HgInvalidControlFileException(m, ex, null);
			}
		}
		return repoConfig;
	}

	// shall be of use only for internal classes 
	/*package-local*/ File getRepositoryRoot() {
		return repoDir;
	}
	
	// FIXME remove once NPE in HgWorkingCopyStatusCollector.areTheSame is solved
	/*package-local, debug*/String getStoragePath(HgDataFile df) {
		return dataPathHelper.rewrite(df.getPath().toString()).toString();
	}

	// XXX package-local, unless there are cases when required from outside (guess, working dir/revision walkers may hide dirstate access and no public visibility needed)
	// XXX consider passing Path pool or factory to produce (shared) Path instead of Strings
	/*package-local*/ final HgDirstate loadDirstate(PathPool pathPool) throws HgInvalidControlFileException {
		PathRewrite canonicalPath = null;
		if (!impl.isCaseSensitiveFileSystem()) {
			canonicalPath = new PathRewrite() {

				public CharSequence rewrite(CharSequence path) {
					return path.toString().toLowerCase();
				}
			};
		}
		HgDirstate ds = new HgDirstate(this, new File(repoDir, "dirstate"), pathPool, canonicalPath);
		ds.read();
		return ds;
	}

	/**
	 * Access to configured set of ignored files.
	 * @see HgIgnore#isIgnored(Path)
	 */
	public HgIgnore getIgnore() /*throws HgInvalidControlFileException */{
		// TODO read config for additional locations
		if (ignore == null) {
			ignore = new HgIgnore();
			File ignoreFile = new File(getWorkingDir(), ".hgignore");
			try {
				final List<String> errors = ignore.read(ignoreFile);
				if (errors != null) {
					getContext().getLog().warn(getClass(), "Syntax errors parsing .hgignore:\n%s", errors);
				}
			} catch (IOException ex) {
				final String m = "Error reading .hgignore file";
				getContext().getLog().warn(getClass(), ex, m);
//				throw new HgInvalidControlFileException(m, ex, ignoreFile);
			}
		}
		return ignore;
	}

	/*package-local*/ DataAccessProvider getDataAccess() {
		return dataAccess;
	}

	/**
	 * Perhaps, should be separate interface, like ContentLookup
	 * path - repository storage path (i.e. one usually with .i or .d)
	 */
	/*package-local*/ RevlogStream resolve(Path path, boolean shallFakeNonExistent) {
		final SoftReference<RevlogStream> ref = streamsCache.get(path);
		RevlogStream cached = ref == null ? null : ref.get();
		if (cached != null) {
			return cached;
		}
		File f = new File(repoDir, path.toString());
		if (f.exists()) {
			RevlogStream s = new RevlogStream(dataAccess, f);
			if (impl.shallCacheRevlogs()) {
				streamsCache.put(path, new SoftReference<RevlogStream>(s));
			}
			return s;
		} else {
			if (shallFakeNonExistent) {
				try {
					File fake = File.createTempFile(f.getName(), null);
					fake.deleteOnExit();
					return new RevlogStream(dataAccess, fake);
				} catch (IOException ex) {
					getContext().getLog().info(getClass(), ex, null);
				}
			}
		}
		return null; // XXX empty stream instead?
	}
	
	/*package-local*/ List<Filter> getFiltersFromRepoToWorkingDir(Path p) {
		return instantiateFilters(p, new Filter.Options(Filter.Direction.FromRepo));
	}

	/*package-local*/ List<Filter> getFiltersFromWorkingDirToRepo(Path p) {
		return instantiateFilters(p, new Filter.Options(Filter.Direction.ToRepo));
	}
	
	/*package-local*/ File getFile(HgDataFile dataFile) {
		return new File(getWorkingDir(), dataFile.getPath().toString());
	}
	
	/*package-local*/ SessionContext getContext() {
		return sessionContext;
	}

	private List<Filter> instantiateFilters(Path p, Filter.Options opts) {
		List<Filter.Factory> factories = impl.getFilters(this);
		if (factories.isEmpty()) {
			return Collections.emptyList();
		}
		ArrayList<Filter> rv = new ArrayList<Filter>(factories.size());
		for (Filter.Factory ff : factories) {
			Filter f = ff.create(p, opts);
			if (f != null) {
				rv.add(f);
			}
		}
		return rv;
	}
}
