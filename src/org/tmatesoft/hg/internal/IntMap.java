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

import java.util.NoSuchElementException;


/**
 * Map implementation that uses plain int keys and performs with log n effectiveness.
 * 
 * @author Artem Tikhomirov
 * @author TMate Software Ltd.
 */
public class IntMap<V> {

	private int[] keys;
	private Object[] values;
	private int size;
	
	public IntMap(int size) {
		keys = new int[size <= 0 ? 16 : size];
		values = new Object[keys.length];
	}
	
	public int size() {
		return size;
	}
	
	public int firstKey() {
		if (size == 0) {
			throw new NoSuchElementException();
		}
		return keys[0];
	}

	public int lastKey() {
		if (size == 0) {
			throw new NoSuchElementException();
		}
		return keys[size-1];
	}
	
	public void trimToSize() {
		if (size < keys.length) {
			int[] newKeys = new int[size];
			Object[] newValues = new Object[size];
			System.arraycopy(keys, 0, newKeys, 0, size);
			System.arraycopy(values, 0, newValues, 0, size);
			keys = newKeys;
			values = newValues;
		}
	}

	public void put(int key, V value) {
		int ix = binarySearch(keys, size, key);
		if (ix < 0) {
			final int insertPoint = -ix - 1;
			assert insertPoint <= size; // can't be greater, provided binarySearch didn't malfunction.
			if (size == keys.length) {
				int capInc = size >>> 2; // +25%
				int newCapacity = size + (capInc < 2 ? 2 : capInc) ;
				int[] newKeys = new int[newCapacity];
				Object[] newValues = new Object[newCapacity];
				System.arraycopy(keys, 0, newKeys, 0, insertPoint);
				System.arraycopy(keys, insertPoint, newKeys, insertPoint+1, keys.length - insertPoint);
				System.arraycopy(values, 0, newValues, 0, insertPoint);
				System.arraycopy(values, insertPoint, newValues, insertPoint+1, values.length - insertPoint);
				keys = newKeys;
				values = newValues;
			} else {
				// arrays got enough capacity
				if (insertPoint != size) {
					System.arraycopy(keys, insertPoint, keys, insertPoint+1, keys.length - insertPoint - 1);
					System.arraycopy(values, insertPoint, values, insertPoint+1, values.length - insertPoint - 1);
				}
				// else insertPoint is past known elements, no need to copy arrays
			}
			keys[insertPoint] = key;
			values[insertPoint] = value;
			size++;
		} else {
			values[ix] = value;
		}
	}
	
	public boolean containsKey(int key) {
		return binarySearch(keys, size, key) >= 0;
	}

	@SuppressWarnings("unchecked")
	public V get(int key) {
		int ix = binarySearch(keys, size, key);
		if (ix >= 0) {
			return (V) values[ix];
		}
		return null;
	}
	
	public void remove(int key) {
		int ix = binarySearch(keys, size, key);
		if (ix >= 0) {
			if (ix <= size - 1) {
				System.arraycopy(keys, ix+1, keys, ix, size - ix - 1);
				System.arraycopy(values, ix+1, values, ix, size - ix - 1);
			} // if ix points to last element, no reason to attempt a copy
			size--;
			keys[size] = 0;
			values[size] = null;
		}
	}
	
	/**
	 * Forget first N entries (in natural order) in the map.
	 */
	@Experimental
	public void removeFromStart(int count) {
		if (count > 0 && count <= size) {
			if (count < size) {
				System.arraycopy(keys, count, keys, 0, size - count);
				System.arraycopy(values, count, values, 0, size - count);
			}
			for (int i = size - count; i < size; i++) {
				keys[i] = 0;
				values[i] = null;
			}
			size -= count;
		} 
	}
	
	

	// copy of Arrays.binarySearch, with upper search limit as argument
	private static int binarySearch(int[] a, int high, int key) {
		int low = 0;
		high--;

		while (low <= high) {
			int mid = (low + high) >> 1;
			int midVal = a[mid];

			if (midVal < key)
				low = mid + 1;
			else if (midVal > key)
				high = mid - 1;
			else
				return mid; // key found
		}
		return -(low + 1); // key not found.
	}
}
