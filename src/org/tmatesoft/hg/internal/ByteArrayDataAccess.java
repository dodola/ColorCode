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

import java.io.IOException;


/**
 *   
 * @author Artem Tikhomirov
 * @author TMate Software Ltd.
 */
public class ByteArrayDataAccess extends DataAccess {

	private final byte[] data;
	private final int offset;
	private final int length;
	private int pos;

	public ByteArrayDataAccess(byte[] data) {
		this(data, 0, data.length);
	}

	public ByteArrayDataAccess(byte[] data, int offset, int length) {
		this.data = data;
		this.offset = offset;
		this.length = length;
		pos = 0;
	}
	
	@Override
	public byte readByte() throws IOException {
		if (pos >= length) {
			throw new IOException();
		}
		return data[offset + pos++];
	}
	@Override
	public void readBytes(byte[] buf, int off, int len) throws IOException {
		if (len > (this.length - pos)) {
			throw new IOException();
		}
		System.arraycopy(data, pos, buf, off, len);
		pos += len;
	}

	@Override
	public ByteArrayDataAccess reset() {
		pos = 0;
		return this;
	}
	@Override
	public int length() {
		return length;
	}
	@Override
	public void seek(int offset) {
		pos = (int) offset;
	}
	@Override
	public void skip(int bytes) throws IOException {
		seek(pos + bytes);
	}
	@Override
	public boolean isEmpty() {
		return pos >= length;
	}
	
	//
	
	// when byte[] needed from DA, we may save few cycles and some memory giving this (otherwise unsafe) access to underlying data
	@Override
	public byte[] byteArray() {
		return data;
	}
}
