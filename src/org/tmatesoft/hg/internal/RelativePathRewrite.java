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

import java.io.File;

import org.tmatesoft.hg.util.PathRewrite;

/**
 *
 * @author Artem Tikhomirov
 * @author TMate Software Ltd.
 */
public class RelativePathRewrite implements PathRewrite {
	
	private final String rootPath;

	public RelativePathRewrite(File root) {
		this(root.getPath());
	}
	
	public RelativePathRewrite(String rootPath) {
		this.rootPath = rootPath;
	}

	public CharSequence rewrite(CharSequence p) {
		String path = p == null ? null : p.toString();
		if (path != null && path.startsWith(rootPath)) {
			if (path.length() == rootPath.length()) {
				return "";
			}
			return path.substring(rootPath.length() + 1);
		}
		return path;
	}
}
