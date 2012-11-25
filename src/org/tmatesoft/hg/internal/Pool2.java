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

import org.tmatesoft.hg.util.DirectHashSet;


/**
 *
 * @author Artem Tikhomirov
 * @author TMate Software Ltd.
 */
public class Pool2<T> {
	private final DirectHashSet<T> unify = new DirectHashSet<T>();
	
	public Pool2() {
	}
	
	public Pool2(int sizeHint) {
	}
	
	public T unify(T t) {
		T rv = unify.get(t);
		if (rv == null) {
			// first time we see a new value
			unify.put(t);
			rv = t;
		}
		return rv;
	}
	
	public boolean contains(T t) {
		return unify.get(t) != null;
	}
	
	public void record(T t) {
		unify.put(t);
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
		sb.append(Pool2.class.getSimpleName());
		sb.append('@');
		sb.append(Integer.toString(System.identityHashCode(this)));
		sb.append(' ');
		sb.append(unify.toString());
		return sb.toString();
	}
}
