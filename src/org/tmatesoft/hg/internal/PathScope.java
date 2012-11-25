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

import java.util.ArrayList;

import org.tmatesoft.hg.util.Path;

/**
 * @author Artem Tikhomirov
 * @author TMate Software Ltd.
 */
public class PathScope implements Path.Matcher {
	private final Path[] files;
	private final Path[] dirs;
	private final boolean recursiveDirs;

	public PathScope(boolean recursiveDirs, Path... paths) {
		if (paths == null) {
			throw new IllegalArgumentException();
		}
		this.recursiveDirs = recursiveDirs;
		ArrayList<Path> f = new ArrayList<Path>(5);
		ArrayList<Path> d = new ArrayList<Path>(5);
		for (Path p : paths) {
			if (p.isDirectory()) {
				d.add(p);
			} else {
				f.add(p);
			}
		}
		files = f.toArray(new Path[f.size()]);
		dirs = d.toArray(new Path[d.size()]);
	}

	public boolean accept(Path path) {
		if (path.isDirectory()) {
			// either equals to or parent of a directory we know about. 
			// If recursiveDirs, accept also if nested to one of our directories.
			// If one of configured files is nested under the path, accept.
			for (Path d : dirs) {
				switch(d.compareWith(path)) {
				case Same : return true;
				case Nested : return true;
				case Parent : return recursiveDirs;
				}
			}
			for (Path f : files) {
				if (f.compareWith(path) == Path.CompareResult.Nested) {
					return true;
				}
			}
		} else {
			for (Path d : dirs) {
				if (d.compareWith(path) == Path.CompareResult.Parent) {
					return true;
				}
			}
			for (Path f : files) {
				if (f.equals(path)) {
					return true;
				}
			}
			// either lives in a directory in out scope
			// or there's a file that matches the path
		}
		// TODO Auto-generated method stub
		return false;
	}
}