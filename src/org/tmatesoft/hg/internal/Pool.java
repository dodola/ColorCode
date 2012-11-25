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

import java.util.HashMap;

/**
 * Instance pooling.
 * 
 * @author Artem Tikhomirov
 * @author TMate Software Ltd.
 */
public class Pool<T> {
	private final HashMap<T,T> unify;
	
	public Pool() {
		unify = new HashMap<T, T>();
	}
	
	public Pool(int sizeHint) {
		if (sizeHint <= 0) {
			unify = new HashMap<T, T>();
		} else {
			unify = new HashMap<T, T>(sizeHint * 4 / 3, 0.75f);
		}
	}
	
	public T unify(T t) {
		T rv = unify.get(t);
		if (rv == null) {
			// first time we see a new value
			unify.put(t, t);
			rv = t;
		}
		return rv;
	}
	
	public boolean contains(T t) {
		return unify.containsKey(t);
	}
	
	public void record(T t) {
		unify.put(t, t);
	}
	
	public void clear() {
		unify.clear();
	}

	public int size() {
		return unify.size();
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append(Pool.class.getSimpleName());
		sb.append('<');
		if (!unify.isEmpty()) {
			sb.append(unify.keySet().iterator().next().getClass().getName());
		}
		sb.append('>');
		sb.append(':');
		sb.append(unify.size());
		return sb.toString();
	}
}