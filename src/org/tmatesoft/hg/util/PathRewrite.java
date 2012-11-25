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

import java.util.LinkedList;
import java.util.List;

/**
 * File names often need transformations, like Windows-style path to Unix or human-readable data file name to storage location.  
 * 
 * @author Artem Tikhomirov
 * @author TMate Software Ltd.
 */
public interface PathRewrite {

	public CharSequence rewrite(CharSequence path);
	
	public static class Empty implements PathRewrite {
		public CharSequence rewrite(CharSequence path) {
			return path;
		}
	}

	public class Composite implements PathRewrite {
		private List<PathRewrite> chain;

		public Composite(PathRewrite... e) {
			LinkedList<PathRewrite> r = new LinkedList<PathRewrite>();
			for (int i = 0; e != null && i < e.length; i++) {
				r.addLast(e[i]);
			}
			chain = r;
		}
		public Composite chain(PathRewrite e) {
			chain.add(e);
			return this;
		}

		public CharSequence rewrite(CharSequence path) {
			for (PathRewrite pr : chain) {
				path = pr.rewrite(path);
			}
			return path;
		}
	}
}
