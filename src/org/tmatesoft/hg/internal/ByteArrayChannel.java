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

import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.List;

import org.tmatesoft.hg.util.ByteChannel;

/**
 *
 * @author Artem Tikhomirov
 * @author TMate Software Ltd.
 */
public class ByteArrayChannel implements ByteChannel {
	private final List<ByteBuffer> buffers;
	private ByteBuffer target;
	private byte[] result;
	
	public ByteArrayChannel() {
		this(-1);
	}
	
	public ByteArrayChannel(int size) {
		if (size == -1) {
			buffers = new LinkedList<ByteBuffer>();
		} else {
			if (size < 0) {
				throw new IllegalArgumentException(String.valueOf(size));
			}
			buffers = null;
			target = ByteBuffer.allocate(size);
		}
	}

	// TODO document what happens on write after toArray() in each case
	public int write(ByteBuffer buffer) {
		int rv = buffer.remaining();
		if (buffers == null) {
			target.put(buffer);
		} else {
			ByteBuffer copy = ByteBuffer.allocate(rv);
			copy.put(buffer);
			buffers.add(copy);
		}
		return rv;
	}

	public byte[] toArray() {
		if (result != null) {
			return result;
		}
		if (buffers == null) {
			assert target.hasArray();
			// int total = target.position();
			// System.arraycopy(target.array(), new byte[total]);
			// I don't want to duplicate byte[] for now
			// although correct way of doing things is to make a copy and discard target
			return target.array();
		} else {
			int total = 0;
			for (ByteBuffer bb : buffers) {
				bb.flip();
				total += bb.limit();
			}
			result = new byte[total];
			int off = 0;
			for (ByteBuffer bb : buffers) {
				bb.get(result, off, bb.limit());
				off += bb.limit();
			}
			buffers.clear();
			return result;
		}
	}
}
