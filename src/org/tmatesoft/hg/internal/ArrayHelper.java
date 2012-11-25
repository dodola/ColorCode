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

/**
 * Internal alternative to Arrays.sort to build reversed index along with sorting
 * 
 * @author Artem Tikhomirov
 * @author TMate Software Ltd.
 */
public class ArrayHelper {
	private int[] reverse;

	@SuppressWarnings("unchecked")
	public void sort(Comparable<?>[] a) {
//		Object[] aux = (Object[]) a.clone();
		reverse = new int[a.length];
		sort1((Comparable<Object>[])a, 0, a.length);
		for (int i = 0; i < reverse.length; i++) {
			// element that was not moved don't have an index in reverse.
			// perhaps, can do it inside sort alg?
			// Alternatively, may start with filling reverse[] array with initial indexes and
			// avoid != 0 comparisons in #swap altogether?
			if (reverse[i] == 0) {
				reverse[i] = i+1;
			}
		}
	}

	/**
	 * Slightly modified version of Arrays.sort1(int[], int, int) quicksort alg (just to deal with Object[])
	 */
    private void sort1(Comparable<Object> x[], int off, int len) {
    	// Insertion sort on smallest arrays
    	if (len < 7) {
    	    for (int i=off; i<len+off; i++)
    			for (int j=i; j>off && x[j-1].compareTo(x[j]) > 0; j--)
    			    swap(x, j, j-1);
    	    return;
    	}

    	// Choose a partition element, v
    	int m = off + (len >> 1);       // Small arrays, middle element
    	if (len > 7) {
    	    int l = off;
    	    int n = off + len - 1;
    	    if (len > 40) {        // Big arrays, pseudomedian of 9
    			int s = len/8;
	    		l = med3(x, l,     l+s, l+2*s);
	    		m = med3(x, m-s,   m,   m+s);
	    		n = med3(x, n-2*s, n-s, n);
    	    }
    	    m = med3(x, l, m, n); // Mid-size, med of 3
    	}
    	Comparable<Object> v = x[m];

    	// Establish Invariant: v* (<v)* (>v)* v*
    	int a = off, b = a, c = off + len - 1, d = c;
    	while(true) {
    	    while (b <= c && x[b].compareTo(v) <= 0) {
    			if (x[b] == v)
    			    swap(x, a++, b);
    			b++;
    	    }
    	    while (c >= b && x[c].compareTo(v) >= 0) {
    			if (x[c] == v)
    			    swap(x, c, d--);
    			c--;
    	    }
    	    if (b > c)
    			break;
    	    swap(x, b++, c--);
    	}

    	// Swap partition elements back to middle
    	int s, n = off + len;
    	s = Math.min(a-off, b-a  );  vecswap(x, off, b-s, s);
    	s = Math.min(d-c,   n-d-1);  vecswap(x, b,   n-s, s);

    	// Recursively sort non-partition-elements
    	if ((s = b-a) > 1)
    	    sort1(x, off, s);
    	if ((s = d-c) > 1)
    	    sort1(x, n-s, s);
    }

    /**
     * Swaps x[a .. (a+n-1)] with x[b .. (b+n-1)].
     */
    private void vecswap(Object[] x, int a, int b, int n) {
		for (int i=0; i<n; i++, a++, b++) {
		    swap(x, a, b);
		}
    }

    /**
     * Returns the index of the median of the three indexed integers.
     */
    private static int med3(Comparable<Object>[] x, int a, int b, int c) {
	return (x[a].compareTo(x[b]) < 0 ?
		(x[b].compareTo(x[c]) < 0 ? b : x[a].compareTo(x[c]) < 0 ? c : a) :
		(x[b].compareTo(x[c]) > 0 ? b : x[a].compareTo(x[c]) > 0 ? c : a));
    }


	/**
	 * @return the reverse
	 */
	public int[] getReverse() {
		return reverse;
	}

	/**
	 * Swaps x[a] with x[b].
	 */
	private void swap(Object[] x, int a, int b) {
		Object t = x[a];
		x[a] = x[b];
		x[b] = t;
		int z1 = reverse[a] != 0 ? reverse[a] : a+1;
		int z2 = reverse[b] != 0 ? reverse[b] : b+1;
		reverse[b] = z1;
		reverse[a] = z2;
	}
}
