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

import org.tmatesoft.hg.internal.Experimental;

/**
 * Memory-friendly alternative to HashSet. With slightly worse performance than that of HashSet, uses n * sizeof(HashMap.Entry) less memory 
 * (i.e. for set of 50k elements saves more than 1 Mb of memory). Besides, elements of this set can be obtained (not only queried for presence) -  
 * the option essential for canonical mappings (aka Pool)
 * 
 * @author Artem Tikhomirov
 * @author TMate Software Ltd.
 */
@Experimental
public class DirectHashSet<T> {
	
	private Object[] table;
	private int size;
	private int threshold;

	public DirectHashSet() {
		this (16);
	}

	public DirectHashSet(int capacity) {
        int result = 2;
        while (result < capacity) {
        	result <<= 1;
        }
		table = new Object[result];
		threshold = result - (result >>> 2);
	}

	/**
	 * Add element to the set.
	 * @param o element, shall not be <code>null</code>
	 * @return previous element from the set equal to the argument
	 */
	@SuppressWarnings("unchecked")
	public T put(T o) {
		final int h = hash(o);
		final int mask = table.length - 1;
		int i = h & mask;
		Object t;
		while ((t = table[i]) != null) {
			if (t.equals(o)) {
				table[i] = o;
				return (T) t;
			}
			i = (i+1) & mask;
		}
		table[i] = o;
		if (++size >= threshold) {
			resize();
		}
		return null;
	}
	
	/**
	 * Query set for the element.
	 * @param o element, shall not be <code>null</code>
	 * @return element from the set, if present
	 */
	@SuppressWarnings("unchecked")
	public T get(T o) {
		final int h = hash(o);
		final int mask = table.length - 1;
		int i = h & mask;
		Object t;
		while ((t = table[i]) != null) {
			if (t == o || t.equals(o)) {
				return (T) t;
			}
			i = (i+1) & mask;
		}
		return null;
	}
	
	public int size() {
		return size;
	}
	
	public void clear() {
		Object[] t = table;
		for (int i = 0, top = t.length; i < top; i++) {
			t[i] = null;
		}
		size = 0;
	}
	
	private void resize() {
		final int newSize = table.length << 1;
		final int newMask = newSize - 1;
		Object[] newTable = new Object[newSize];
		for (int i = 0, size = table.length; i < size; i++) {
			Object t = table[i];
			if (t != null) {
				table[i] = null;
				int x = hash(t) & newMask;
				while (newTable[x] != null) {
					x = (x+1) & newMask;
				}
				newTable[x] = t;
			}
		}
		table = newTable;
		threshold = newSize - (newSize >>> 2);
	}

	private static int hash(Object o) {
		int h = o.hashCode();
//		return h;
		// HashMap.newHash()
		h ^= (h >>> 20) ^ (h >>> 12);
        return h ^ (h >>> 7) ^ (h >>> 4);
	}

}
