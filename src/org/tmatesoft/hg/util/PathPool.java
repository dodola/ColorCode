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

import java.lang.ref.SoftReference;
import java.util.WeakHashMap;


/**
 * Produces path from strings and caches result for reuse
 * 
 * @author Artem Tikhomirov
 * @author TMate Software Ltd.
 */
public class PathPool implements Path.Source {
	private final WeakHashMap<String, SoftReference<Path>> cache;
	private final PathRewrite pathRewrite;
	
	public PathPool(PathRewrite rewrite) {
		pathRewrite = rewrite;
		cache = new WeakHashMap<String, SoftReference<Path>>();
	}

	public Path path(String p) {
		p = pathRewrite.rewrite(p).toString();
		return get(p, true);
	}

	// pipes path object through cache to reuse instance, if possible
	// TODO unify with Pool<Path>
	public Path path(Path p) {
		String s = pathRewrite.rewrite(p).toString();
		Path cached = get(s, false);
		if (cached == null) {
			cache.put(s, new SoftReference<Path>(cached = p));
		}
		return cached;
	}

	// XXX what would be parent of an empty path?
	// Path shall have similar functionality
	public Path parent(Path path) {
		if (path.length() == 0) {
			throw new IllegalArgumentException();
		}
		for (int i = path.length() - 2 /*if path represents a dir, trailing char is slash, skip*/; i >= 0; i--) {
			if (path.charAt(i) == '/') {
				return get(path.subSequence(0, i+1).toString(), true);
			}
		}
		return get("", true);
	}
	
	// invoke when path pool is no longer in use, to ease gc work
	public void clear() {
		cache.clear();
	}

	private Path get(String p, boolean create) {
		SoftReference<Path> sr = cache.get(p);
		Path path = sr == null ? null : sr.get();
		if (path == null) {
			if (create) {
				path = Path.create(p);
				cache.put(p, new SoftReference<Path>(path));
			} else if (sr != null) {
				// cached path no longer used, clear cache entry - do not wait for RefQueue to step in
				cache.remove(p);
			}
		} 
		return path;
	}
}
