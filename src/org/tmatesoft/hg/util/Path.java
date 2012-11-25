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

import java.util.Collection;
import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * Identify repository files (not String nor io.File). Convenient for pattern matching. Memory-friendly.
 * 
 * @author Artem Tikhomirov
 * @author TMate Software Ltd.
 */
public final class Path implements CharSequence, Comparable<Path>/*Cloneable? - although clone for paths make no sense*/{
//	private String[] segments;
//	private int flags; // dir, unparsed
	private String path;
	
	/*package-local*/Path(String p) {
		path = p;
	}

	/**
	 * Check if this is directory's path. 
	 * Note, this method doesn't perform any file system operation.
	 * 
	 * @return true when this path points to a directory 
	 */
	public boolean isDirectory() {
		// XXX simple logic for now. Later we may decide to have an explicit factory method to create directory paths
		return path.charAt(path.length() - 1) == '/';
	}

	public int length() {
		return path.length();
	}

	public char charAt(int index) {
		return path.charAt(index);
	}

	public CharSequence subSequence(int start, int end) {
		// new Path if start-end matches boundaries of any subpath
		return path.substring(start, end);
	}
	
	@Override
	public String toString() {
		return path; // CharSequence demands toString() impl
	}
	
	public Iterable<String> segments() {
		class SegSeq implements Iterable<String>, Iterator<String> {
			private int pos; // first char to return

			public Iterator<String> iterator() {
				reset();
				return this;
			}
			public boolean hasNext() {
				return pos < path.length();
			}
			public String next() {
				if (pos >= path.length()) {
					throw new NoSuchElementException();
				}
				int x = path.indexOf('/', pos);
				if (x == -1) {
					String rv = path.substring(pos);
					pos = path.length();
					return rv;
				} else {
					String rv = path.substring(pos, x);
					pos = x+1;
					return rv;
				}
			}
			public void remove() {
				throw new UnsupportedOperationException();
			}

			private void reset() {
				pos = 0;
			}
		};
		return new SegSeq();
	}

	public int compareTo(Path o) {
		return path.compareTo(o.path);
	}
	
	@Override
	public boolean equals(Object obj) {
		if (obj != null && getClass() == obj.getClass()) {
			return this == obj || path.equals(((Path) obj).path);
		}
		return false;
	}
	@Override
	public int hashCode() {
		return path.hashCode();
	}
	
	public enum CompareResult {
		Same, Unrelated, Nested, Parent, /* perhaps, also ImmediateParent, DirectChild? */
	}
	
	/*
	 * a/file and a/dir ?
	 */
	public CompareResult compareWith(Path another) {
		if (another == null) {
			return CompareResult.Unrelated; // XXX perhaps, IAE?
		}
		if (another == this || (another.length() == length() && equals(another))) {
			return CompareResult.Same;
		}
		if (path.startsWith(another.path)) {
			return CompareResult.Nested;
		}
		if (another.path.startsWith(path)) {
			return CompareResult.Parent;
		}
		return CompareResult.Unrelated;
	}

	public static Path create(CharSequence path) {
		if (path == null) {
			throw new IllegalArgumentException();
		}
		if (path instanceof Path) {
			Path o = (Path) path;
			return o;
		}
		String p = path.toString();
		if (p.indexOf('\\') != -1) {
			throw new IllegalArgumentException();
		}
		Path rv = new Path(p);
		return rv;
	}

	/**
	 * Path filter.
	 */
	public interface Matcher {
		boolean accept(Path path);
		
		final class Any implements Matcher {
			public boolean accept(Path path) { return true; }
		}
		class Composite implements Matcher {
			private final Path.Matcher[] elements;
			
			public Composite(Collection<Path.Matcher> matchers) {
				elements = matchers.toArray(new Path.Matcher[matchers.size()]);
			}

			public boolean accept(Path path) {
				for (Path.Matcher m : elements) {
					if (m.accept(path)) {
						return true;
					}
				}
				return false;
			}
		}
	}

	/**
	 * Factory for paths
	 */
	public interface Source {
		Path path(String p);
	}

	/**
	 * Straightforward {@link Source} implementation that creates new Path instance for each supplied string
	 */
	public static class SimpleSource implements Source {
		private final PathRewrite normalizer;

		public SimpleSource(PathRewrite pathRewrite) {
			if (pathRewrite == null) {
				throw new IllegalArgumentException();
			}
			normalizer = pathRewrite;
		}

		public Path path(String p) {
			return Path.create(normalizer.rewrite(p));
		}
	}
}
