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

import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.tmatesoft.hg.util.Path;
import org.tmatesoft.hg.util.Path.Matcher;

/**
 *
 * @author Artem Tikhomirov
 * @author TMate Software Ltd.
 */
public class PathRegexpMatcher implements Matcher {
	private Pattern[] patterns;
	
	// disjunction, matches if any pattern found
	// uses pattern.find(), not pattern.matches()
	public PathRegexpMatcher(Pattern... p) {
		if (p == null) {
			throw new IllegalArgumentException();
		}
		patterns = p;
	}
	
	public PathRegexpMatcher(String... p) throws PatternSyntaxException {
		this(compile(p));
	}
	
	private static Pattern[] compile(String[] p) throws PatternSyntaxException {
		// deliberately do no check for null, let it fail
		Pattern[] rv = new Pattern[p.length];
		int i = 0;
		for (String s : p) {
			rv[i++] = Pattern.compile(s);
		}
		return rv;
	}

	public boolean accept(Path path) {
		for (Pattern p : patterns) {
			if (p.matcher(path).find()) {
				return true;
			}
		}
		return false;
	}
}
