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
package org.tmatesoft.hg.internal;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * relevant parts of DataInput, non-stream nature (seek operation), explicit check for end of data.
 * convenient skip (+/- bytes)
 * Primary goal - effective file read, so that clients don't need to care whether to call few 
 * distinct getInt() or readBytes(totalForFewInts) and parse themselves instead in an attempt to optimize.  
 * 
 * @author Artem Tikhomirov
 * @author TMate Software Ltd.
 */
public class DataAccess {
	public boolean isEmpty() {
		return true;
	}
	public int length() {
		return 0;
	}
	/**
	 * get this instance into initial state
	 * @throws IOException
	 * @return <code>this</code> for convenience
	 */
	public DataAccess reset() throws IOException {
		// nop, empty instance is always in the initial state
		return this;
	}
	// absolute positioning
	public void seek(int offset) throws IOException {
		throw new UnsupportedOperationException();
	}
	// relative positioning
	public void skip(int bytes) throws IOException {
		throw new UnsupportedOperationException();
	}
	// shall be called once this object no longer needed
	public void done() {
		// no-op in this empty implementation
	}
	public int readInt() throws IOException {
		byte[] b = new byte[4];
		readBytes(b, 0, 4);
		return b[0] << 24 | (b[1] & 0xFF) << 16 | (b[2] & 0xFF) << 8 | (b[3] & 0xFF);
	}
	public long readLong() throws IOException {
		byte[] b = new byte[8];
		readBytes(b, 0, 8);
		int i1 = b[0] << 24 | (b[1] & 0xFF) << 16 | (b[2] & 0xFF) << 8 | (b[3] & 0xFF);
		int i2 = b[4] << 24 | (b[5] & 0xFF) << 16 | (b[6] & 0xFF) << 8 | (b[7] & 0xFF);
		return ((long) i1) << 32 | ((long) i2 & 0xFFFFFFFFl);
	}
	public void readBytes(byte[] buf, int offset, int length) throws IOException {
		throw new UnsupportedOperationException();
	}
	// reads bytes into ByteBuffer, up to its limit or total data length, whichever smaller
	// FIXME perhaps, in DataAccess paradigm (when we read known number of bytes, we shall pass specific byte count to read) 
	public void readBytes(ByteBuffer buf) throws IOException {
//		int toRead = Math.min(buf.remaining(), (int) length());
//		if (buf.hasArray()) {
//			readBytes(buf.array(), buf.arrayOffset(), toRead);
//		} else {
//			byte[] bb = new byte[toRead];
//			readBytes(bb, 0, bb.length);
//			buf.put(bb);
//		}
		// FIXME optimize to read as much as possible at once
		while (!isEmpty() && buf.hasRemaining()) {
			buf.put(readByte());
		}
	}
	public byte readByte() throws IOException {
		throw new UnsupportedOperationException();
	}

	// XXX decide whether may or may not change position in the DataAccess
	// FIXME exception handling is not right, just for the sake of quick test
	public byte[] byteArray() throws IOException {
		reset();
		byte[] rv = new byte[length()];
		readBytes(rv, 0, rv.length);
		return rv;
	}
}
