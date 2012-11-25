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
package org.tmatesoft.hg.util;

import java.io.File;
import java.util.LinkedList;
import java.util.NoSuchElementException;

/**
 * 
 * @author Artem Tikhomirov
 * @author TMate Software Ltd.
 */
public class FileWalker implements FileIterator {

	private final File startDir;
	private final Path.Source pathHelper;
	private final LinkedList<File> dirQueue;
	private final LinkedList<File> fileQueue;
	private final Path.Matcher scope;
	private RegularFileInfo nextFile;
	private Path nextPath;

	public FileWalker(File dir, Path.Source pathFactory) {
		this(dir, pathFactory, null);
	}

	/**
	 * 
	 * @param dir
	 * @param pathFactory
	 * @param scopeMatcher - this matcher shall be capable to tell not only files of interest, but
	 * also whether directories shall be traversed or not (Paths it gets in {@link Path.Matcher#accept(Path)} may 
	 * point to directories)   
	 */
	public FileWalker(File dir, Path.Source pathFactory, Path.Matcher scopeMatcher) {
		startDir = dir;
		pathHelper = pathFactory;
		dirQueue = new LinkedList<File>();
		fileQueue = new LinkedList<File>();
		scope = scopeMatcher;
		reset();
	}

	public void reset() {
		fileQueue.clear();
		dirQueue.clear();
		dirQueue.add(startDir);
		nextFile = new RegularFileInfo();
		nextPath = null;
	}
	
	public boolean hasNext() {
		return fill();
	}

	public void next() {
		if (!fill()) {
			throw new NoSuchElementException();
		}
		File next = fileQueue.removeFirst();
		nextFile.init(next);
		nextPath = pathHelper.path(next.getPath());
	}

	public Path name() {
		return nextPath;
	}
	
	public FileInfo file() {
		return nextFile;
	}
	
	public boolean inScope(Path file) {
		/* by default, no limits, all files are of interest */
		return scope == null ? true : scope.accept(file); 
	}
	
	// returns non-null
	private File[] listFiles(File f) {
		// in case we need to solve os-related file issues (mac with some encodings?)
		File[] rv = f.listFiles();
		// there are chances directory we query files for is missing (deleted), just treat it as empty
		return rv == null ? new File[0] : rv;
	}

	// return true when fill added any elements to fileQueue. 
	private boolean fill() {
		while (fileQueue.isEmpty()) {
			if (dirQueue.isEmpty()) {
				return false;
			}
			while (!dirQueue.isEmpty()) {
				File dir = dirQueue.removeFirst();
				for (File f : listFiles(dir)) {
					final boolean isDir = f.isDirectory();
					Path path = pathHelper.path(isDir ? ensureTrailingSlash(f.getPath()) : f.getPath());
					if (!inScope(path)) {
						continue;
					}
					if (isDir) {
						if (!".hg/".equals(path.toString())) {
							dirQueue.addLast(f);
						}
					} else {
						fileQueue.addLast(f);
					}
				}
				break;
			}
		}
		return !fileQueue.isEmpty();
	}
	
	private static String ensureTrailingSlash(String dirName) {
		if (dirName.length() > 0) {
			char last = dirName.charAt(dirName.length() - 1);
			if (last == '/' || last == File.separatorChar) {
				return dirName;
			}
			// if path already has platform-specific separator (which, BTW, it shall, according to File#getPath), 
			// add similar, otherwise use our default.
			return dirName.indexOf(File.separatorChar) != -1 ? dirName.concat(File.separator) : dirName.concat("/");
		}
		return dirName;
	}
}
