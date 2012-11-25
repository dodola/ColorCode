/*
 * Copyright (c) 2011-2012 TMate Software Ltd
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

import static org.tmatesoft.hg.internal.RequiresFile.*;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.StringTokenizer;

import org.tmatesoft.hg.core.SessionContext;
import org.tmatesoft.hg.repo.HgInternals;
import org.tmatesoft.hg.repo.HgRepoConfig.ExtensionsSection;
import org.tmatesoft.hg.repo.HgRepository;
import org.tmatesoft.hg.util.PathRewrite;

/**
 * Fields/members that shall not be visible  
 * 
 * @author Artem Tikhomirov
 * @author TMate Software Ltd.
 */
public final class Internals {
	
	/**
	 * Allows to specify Mercurial installation directory to detect installation-wide configurations.
	 * Without this property set, hg4j would attempt to deduce this value locating hg executable. 
	 */
	public static final String CFG_PROPERTY_HG_INSTALL_ROOT = "hg4j.hg.install_root";

	/**
	 * Tells repository not to cache files/revlogs
	 * XXX perhaps, need to respect this property not only for data files, but for manifest and changelog as well?
	 * (@see HgRepository#getChangelog and #getManifest())  
	 */
	public static final String CFG_PROPERTY_REVLOG_STREAM_CACHE = "hg4j.repo.disable_revlog_cache";
	
	private int requiresFlags = 0;
	private List<Filter.Factory> filterFactories;
	private final boolean isCaseSensitiveFileSystem;
	private final boolean shallCacheRevlogsInRepo;
	

	public Internals(SessionContext ctx) {
		isCaseSensitiveFileSystem = !runningOnWindows();
		Object p = ctx.getProperty(CFG_PROPERTY_REVLOG_STREAM_CACHE, true);
		shallCacheRevlogsInRepo = p instanceof Boolean ? ((Boolean) p).booleanValue() : Boolean.parseBoolean(String.valueOf(p));
	}
	
	public void parseRequires(HgRepository hgRepo, File requiresFile) {
		try {
			new RequiresFile().parse(this, requiresFile);
		} catch (IOException ex) {
			// FIXME not quite sure error reading requires file shall be silently logged only.
			HgInternals.getContext(hgRepo).getLog().error(getClass(), ex, null);
		}
	}

	public/*for tests, otherwise pkg*/ void setStorageConfig(int version, int flags) {
		requiresFlags = flags;
	}
	
	public PathRewrite buildNormalizePathRewrite() {
		if (runningOnWindows()) {
			return new PathRewrite() {
					
					public CharSequence rewrite(CharSequence p) {
						// TODO handle . and .. (although unlikely to face them from GUI client)
						String path = p.toString();
						path = path.replace('\\', '/').replace("//", "/");
						if (path.startsWith("/")) {
							path = path.substring(1);
						}
						return path;
					}
				};
		} else {
			return new PathRewrite.Empty(); // or strip leading slash, perhaps? 
		}
	}

	// XXX perhaps, should keep both fields right here, not in the HgRepository
	public PathRewrite buildDataFilesHelper() {
		return new StoragePathHelper((requiresFlags & STORE) != 0, (requiresFlags & FNCACHE) != 0, (requiresFlags & DOTENCODE) != 0);
	}

	public PathRewrite buildRepositoryFilesHelper() {
		if ((requiresFlags & STORE) != 0) {
			return new PathRewrite() {
				public CharSequence rewrite(CharSequence path) {
					return "store/" + path;
				}
			};
		} else {
			return new PathRewrite.Empty();
		}
	}

	public List<Filter.Factory> getFilters(HgRepository hgRepo) {
		if (filterFactories == null) {
			filterFactories = new ArrayList<Filter.Factory>();
			ExtensionsSection cfg = hgRepo.getConfiguration().getExtensions();
			if (cfg.isEnabled("eol")) {
				NewlineFilter.Factory ff = new NewlineFilter.Factory();
				ff.initialize(hgRepo);
				filterFactories.add(ff);
			}
			if (cfg.isEnabled("keyword")) {
				KeywordFilter.Factory ff = new KeywordFilter.Factory();
				ff.initialize(hgRepo);
				filterFactories.add(ff);
			}
		}
		return filterFactories;
	}
	
	public void initEmptyRepository(File hgDir) throws IOException {
		hgDir.mkdir();
		FileOutputStream requiresFile = new FileOutputStream(new File(hgDir, "requires"));
		StringBuilder sb = new StringBuilder(40);
		sb.append("revlogv1\n");
		if ((requiresFlags & STORE) != 0) {
			sb.append("store\n");
		}
		if ((requiresFlags & FNCACHE) != 0) {
			sb.append("fncache\n");
		}
		if ((requiresFlags & DOTENCODE) != 0) {
			sb.append("dotencode\n");
		}
		requiresFile.write(sb.toString().getBytes());
		requiresFile.close();
		new File(hgDir, "store").mkdir(); // with that, hg verify says ok.
	}
	
	public boolean isCaseSensitiveFileSystem() {
		return isCaseSensitiveFileSystem;
	}

	public static boolean runningOnWindows() {
		return System.getProperty("os.name").indexOf("Windows") != -1;
	}
	
	/**
	 * For Unix, returns installation root, which is the parent directory of the hg executable (or symlink) being run.
	 * For Windows, it's Mercurial installation directory itself 
	 * @param ctx 
	 */
	private static File findHgInstallRoot(SessionContext ctx) {
		// let clients to override Hg install location 
		String p = (String) ctx.getProperty(CFG_PROPERTY_HG_INSTALL_ROOT, null);
		if (p != null) {
			return new File(p);
		}
		StringTokenizer st = new StringTokenizer(System.getenv("PATH"), System.getProperty("path.separator"), false);
		final boolean runsOnWin = runningOnWindows();
		while (st.hasMoreTokens()) {
			String pe = st.nextToken();
			File execCandidate = new File(pe, runsOnWin ? "hg.exe" : "hg");
			if (execCandidate.exists() && execCandidate.isFile()) {
				File execDir = execCandidate.getParentFile();
				// e.g. on Unix runs "/shared/tools/bin/hg", directory of interest is "/shared/tools/" 
				return runsOnWin ? execDir : execDir.getParentFile();
			}
		}
		return null;
	}
	
	/**
	 * @see http://www.selenic.com/mercurial/hgrc.5.html
	 */
	public ConfigFile readConfiguration(HgRepository hgRepo, File repoRoot) throws IOException {
		ConfigFile configFile = new ConfigFile();
		File hgInstallRoot = findHgInstallRoot(HgInternals.getContext(hgRepo)); // may be null
		//
		if (runningOnWindows()) {
			if (hgInstallRoot != null) {
				for (File f : getWindowsConfigFilesPerInstall(hgInstallRoot)) {
					configFile.addLocation(f);
				}
			}
			LinkedHashSet<String> locations = new LinkedHashSet<String>();
			locations.add(System.getenv("USERPROFILE"));
			locations.add(System.getenv("HOME"));
			locations.remove(null);
			for (String loc : locations) {
				File location = new File(loc);
				configFile.addLocation(new File(location, "Mercurial.ini"));
				configFile.addLocation(new File(location, ".hgrc"));
			}
		} else {
			if (hgInstallRoot != null) {
				File d = new File(hgInstallRoot, "etc/mercurial/hgrc.d/");
				if (d.isDirectory() && d.canRead()) {
					for (File f : listConfigFiles(d)) {
						configFile.addLocation(f);
					}
				}
				configFile.addLocation(new File(hgInstallRoot, "etc/mercurial/hgrc"));
			}
			// same, but with absolute paths
			File d = new File("/etc/mercurial/hgrc.d/");
			if (d.isDirectory() && d.canRead()) {
				for (File f : listConfigFiles(d)) {
					configFile.addLocation(f);
				}
			}
			configFile.addLocation(new File("/etc/mercurial/hgrc"));
			configFile.addLocation(new File(System.getenv("HOME"), ".hgrc"));
		}
		// last one, overrides anything else
		// <repo>/.hg/hgrc
		configFile.addLocation(new File(repoRoot, "hgrc"));
		return configFile;
	}
	
	private static List<File> getWindowsConfigFilesPerInstall(File hgInstallDir) {
		File f = new File(hgInstallDir, "Mercurial.ini");
		if (f.exists()) {
			return Collections.singletonList(f);
		}
		f = new File(hgInstallDir, "hgrc.d/");
		if (f.canRead() && f.isDirectory()) {
			return listConfigFiles(f);
		}
		// FIXME query registry, e.g. with
		// Runtime.exec("reg query HKLM\Software\Mercurial")
		//
		f = new File("C:\\Mercurial\\Mercurial.ini");
		if (f.exists()) {
			return Collections.singletonList(f);
		}
		return Collections.emptyList();
	}
	
	private static List<File> listConfigFiles(File dir) {
		assert dir.canRead();
		assert dir.isDirectory();
		final File[] allFiles = dir.listFiles();
		// File is Comparable, lexicographically by default
		Arrays.sort(allFiles);
		ArrayList<File> rv = new ArrayList<File>(allFiles.length);
		for (File f : allFiles) {
			if (f.getName().endsWith(".rc")) {
				rv.add(f);
			}
		}
		return rv;
	}
	
	public static File getInstallationConfigurationFileToWrite(SessionContext ctx) {
		File hgInstallRoot = findHgInstallRoot(ctx); // may be null
		// choice of which hgrc to pick here is according to my own pure discretion
		if (hgInstallRoot != null) {
			// use this location only if it's writable
			File cfg = new File(hgInstallRoot, runningOnWindows() ? "Mercurial.ini" : "etc/mercurial/hgrc");
			if (cfg.canWrite() || cfg.getParentFile().canWrite()) {
				return cfg;
			}
		}
		// fallback
		if (runningOnWindows()) {
			if (hgInstallRoot == null) {
				return new File("C:\\Mercurial\\Mercurial.ini");
			} else {
				// yes, we tried this file already (above) and found it non-writable
				// let caller fail with can't write
				return new File(hgInstallRoot, "Mercurial.ini");
			}
		} else {
			return new File("/etc/mercurial/hgrc");
		}
	}

	public static File getUserConfigurationFileToWrite(SessionContext ctx) {
		LinkedHashSet<String> locations = new LinkedHashSet<String>();
		final boolean runsOnWindows = runningOnWindows();
		if (runsOnWindows) {
			locations.add(System.getenv("USERPROFILE"));
		}
		locations.add(System.getenv("HOME"));
		locations.remove(null);
		for (String loc : locations) {
			File location = new File(loc);
			File rv = new File(location, ".hgrc");
			if (rv.exists() && rv.canWrite()) {
				return rv;
			}
			if (runsOnWindows) {
				rv = new File(location, "Mercurial.ini");
				if (rv.exists() && rv.canWrite()) {
					return rv;
				}
			}
		}
		// fallback to default, let calling code fail with Exception if can't write
		return new File(System.getProperty("user.home"), ".hgrc");
	}

	public boolean shallCacheRevlogs() {
		return shallCacheRevlogsInRepo;
	}
}
