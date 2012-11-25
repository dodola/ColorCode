/*
 * Copyright (c) 2010-2011 TMate Software Ltd
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
package org.tmatesoft.hg.core;

import static org.tmatesoft.hg.internal.DigestHelper.toHexString;

import java.util.Arrays;

import org.tmatesoft.hg.internal.DigestHelper;



/**
 * A 20-bytes (40 characters) long hash value to identify a revision.
 * @see http://mercurial.selenic.com/wiki/Nodeid
 *
 * @author Artem Tikhomirov
 * @author TMate Software Ltd.
 *
 */
public final class Nodeid implements Comparable<Nodeid> {
	
	/**
	 * <b>nullid</b>, empty root revision.
	 */
	public static final Nodeid NULL = new Nodeid(new byte[20], false);

	private final byte[] binaryData; 

	/**
	 * @param binaryRepresentation - array of exactly 20 bytes
	 * @param shallClone - true if array is subject to future modification and shall be copied, not referenced
	 * @throws IllegalArgumentException if supplied binary representation doesn't correspond to 20 bytes of sha1 digest 
	 */
	public Nodeid(byte[] binaryRepresentation, boolean shallClone) {
		// 5 int fields => 32 bytes
		// byte[20] => 48 bytes (16 bytes is Nodeid with one field, 32 bytes for byte[20] 
		if (binaryRepresentation == null || binaryRepresentation.length != 20) {
			throw new IllegalArgumentException();
		}
		/*
		 * byte[].clone() is not reflected when ran with -agentlib:hprof=heap=sites
		 * thus not to get puzzled why there are N Nodeids and much less byte[] instances,
		 * may use following code to see N byte[] as well.
		 *
		if (shallClone) {
			binaryData = new byte[20];
			System.arraycopy(binaryRepresentation, 0, binaryData, 0, 20);
		} else {
			binaryData = binaryRepresentation;
		}
		*/
		binaryData = shallClone ? binaryRepresentation.clone() : binaryRepresentation;
	}

	@Override
	public int hashCode() {
		// digest (part thereof) seems to be nice candidate for the hashCode
		byte[] b = binaryData;
		return b[0] << 24 | (b[1] & 0xFF) << 16 | (b[2] & 0xFF) << 8 | (b[3] & 0xFF);
	}
	
	@Override
	public boolean equals(Object o) {
		if (o == this) {
			return true;
		}
		if (o instanceof Nodeid) {
			return equalsTo(((Nodeid) o).binaryData);
		}
		return false;
	}

	public boolean equalsTo(byte[] buf) {
		return Arrays.equals(this.binaryData, buf);
	}
	
	public int compareTo(Nodeid o) {
		if (this == o) {
			return 0;
		}
		for (int i = 0; i < 20; i++) {
			if (binaryData[i] != o.binaryData[i]) {
				// if we need truly ascending sort, need to respect byte sign 
				// return (binaryData[i] & 0xFF) < (o.binaryData[i] & 0xFF) ? -1 : 1;
				// however, for our purposes partial sort is pretty enough
				return binaryData[i] < o.binaryData[i] ? -1 : 1;
			}
		}
		return 0;
	}

	/**
	 * Complete string representation of this Nodeid.
	 */
	@Override
	public String toString() {
		// XXX may want to output just single 0 for the NULL id?
		return toHexString(binaryData, 0, binaryData.length);
	}

	public String shortNotation() {
		return toHexString(binaryData, 0, 6);
	}
	
	public boolean isNull() {
		if (this == NULL) {
			return true;
		}
		for (int i = 0; i < 20; i++) {
			if (this.binaryData[i] != 0) {
				return false;
			}
		}
		return true;
	}

	// copy 
	public byte[] toByteArray() {
		return binaryData.clone();
	}

	/**
	 * Factory for {@link Nodeid Nodeids}.
	 * Primary difference with cons is handling of NULL id (this method returns constant) and control over array 
	 * duplication - this method always makes a copy of an array passed
	 * @param binaryRepresentation - byte array of a length at least offset + 20
	 * @param offset - index in the array to start from
	 * @throws IllegalArgumentException when arguments don't select 20 bytes
	 */
	public static Nodeid fromBinary(byte[] binaryRepresentation, int offset) {
		if (binaryRepresentation == null || binaryRepresentation.length - offset < 20) {
			throw new IllegalArgumentException();
		}
		int i = 0;
		while (i < 20 && binaryRepresentation[offset+i] == 0) i++;
		if (i == 20) {
			return NULL;
		}
		if (offset == 0 && binaryRepresentation.length == 20) {
			return new Nodeid(binaryRepresentation, true);
		}
		byte[] b = new byte[20]; // create new instance if no other reasonable guesses possible
		System.arraycopy(binaryRepresentation, offset, b, 0, 20);
		return new Nodeid(b, false);
	}

	/**
	 * Parse encoded representation.
	 * 
	 * @param asciiRepresentation - encoded form of the Nodeid.
	 * @return object representation
	 * @throws IllegalArgumentException when argument doesn't match encoded form of 20-bytes sha1 digest. 
	 */
	public static Nodeid fromAscii(String asciiRepresentation) {
		if (asciiRepresentation.length() != 40) {
			throw new IllegalArgumentException();
		}
		// XXX is better impl for String possible?
		return fromAscii(asciiRepresentation.toCharArray(), 0, 40);
	}
	
	/**
	 * Parse encoded representation. Similar to {@link #fromAscii(String)}.
	 */
	public static Nodeid fromAscii(byte[] asciiRepresentation, int offset, int length) {
		if (length != 40) {
			throw new IllegalArgumentException();
		}
		byte[] data = new byte[20];
		boolean zeroBytes = DigestHelper.ascii2bin(asciiRepresentation, offset, length, data);
		if (zeroBytes) {
			return NULL;
		}
		return new Nodeid(data, false);
	}
	
	public static Nodeid fromAscii(char[] asciiRepresentation, int offset, int length) {
		byte[] b = new byte[length];
		for (int i = 0; i < b.length; i++) {
			b[i] = (byte) asciiRepresentation[offset+i];
		}
		return fromAscii(b, 0, b.length);
	}
}
