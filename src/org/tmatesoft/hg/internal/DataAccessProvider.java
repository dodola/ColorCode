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

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;

import org.tmatesoft.hg.core.HgBadStateException;
import org.tmatesoft.hg.core.SessionContext;

/**
 * 
 * @author Artem Tikhomirov
 * @author TMate Software Ltd.
 */
public class DataAccessProvider {
	/**
	 * Boundary to start using file memory mapping instead of regular file access, in bytes.  
	 * Set to 0 to indicate mapping files into memory shall not be used.
	 * If set to -1, file of any size would be mapped in memory.
	 */
	public static final String CFG_PROPERTY_MAPIO_LIMIT				= "hg4j.dap.mapio_limit";
	public static final String CFG_PROPERTY_MAPIO_BUFFER_SIZE		= "hg4j.dap.mapio_buffer";
	public static final String CFG_PROPERTY_FILE_BUFFER_SIZE		= "hg4j.dap.file_buffer";

	private final int mapioMagicBoundary;
	private final int bufferSize;
	private final SessionContext context;

	public DataAccessProvider(SessionContext ctx) {
		this(ctx, getConfigOption(ctx, CFG_PROPERTY_MAPIO_LIMIT, 100 * 1024), getConfigOption(ctx, CFG_PROPERTY_FILE_BUFFER_SIZE, 8 * 1024));
	}
	
	private static int getConfigOption(SessionContext ctx, String optName, int defaultValue) {
		Object v = ctx.getProperty(optName, defaultValue);
		if (false == v instanceof Number) {
			v = Integer.parseInt(v.toString());
		}
		return ((Number) v).intValue();
	}

	public DataAccessProvider(SessionContext ctx, int mapioBoundary, int regularBufferSize) {
		context = ctx;
		mapioMagicBoundary = mapioBoundary == 0 ? Integer.MAX_VALUE : mapioBoundary;
		bufferSize = regularBufferSize;
	}

	public DataAccess create(File f) {
		if (!f.exists()) {
			return new DataAccess();
		}
		try {
			FileChannel fc = new FileInputStream(f).getChannel();
			int flen = (int) fc.size();
			if (fc.size() - flen != 0) {
				throw new HgBadStateException("Files greater than 2Gb are not yet supported");
			}
			if (flen > mapioMagicBoundary) {
				// TESTS: bufLen of 1024 was used to test MemMapFileAccess
				return new MemoryMapFileAccess(fc, flen, getConfigOption(context, CFG_PROPERTY_MAPIO_BUFFER_SIZE, 100*1024 /*same as default boundary*/));
			} else {
				// XXX once implementation is more or less stable,
				// may want to try ByteBuffer.allocateDirect() to see
				// if there's any performance gain. 
				boolean useDirectBuffer = false; // XXX might be another config option
				// TESTS: bufferSize of 100 was used to check buffer underflow states when readBytes reads chunks bigger than bufSize
				return new FileAccess(fc, flen, bufferSize, useDirectBuffer);
			}
		} catch (IOException ex) {
			// unlikely to happen, we've made sure file exists.
			context.getLog().error(getClass(), ex, null);
		}
		return new DataAccess(); // non-null, empty.
	}

	// DOESN'T WORK YET 
	private static class MemoryMapFileAccess extends DataAccess {
		private FileChannel fileChannel;
		private final int size;
		private long position = 0; // always points to buffer's absolute position in the file
		private final int memBufferSize;
		private MappedByteBuffer buffer;

		public MemoryMapFileAccess(FileChannel fc, int channelSize, int bufferSize) {
			fileChannel = fc;
			size = channelSize;
			memBufferSize = bufferSize > channelSize ? channelSize : bufferSize; // no reason to waste memory more than there's data 
		}

		@Override
		public boolean isEmpty() {
			return position + (buffer == null ? 0 : buffer.position()) >= size;
		}
		
		@Override
		public int length() {
			return size;
		}
		
		@Override
		public DataAccess reset() throws IOException {
			seek(0);
			return this;
		}
		
		@Override
		public void seek(int offset) {
			assert offset >= 0;
			// offset may not necessarily be further than current position in the file (e.g. rewind) 
			if (buffer != null && /*offset is within buffer*/ offset >= position && (offset - position) < buffer.limit()) {
				buffer.position((int) (offset - position));
			} else {
				position = offset;
				buffer = null;
			}
		}

		@Override
		public void skip(int bytes) throws IOException {
			assert bytes >= 0;
			if (buffer == null) {
				position += bytes;
				return;
			}
			if (buffer.remaining() > bytes) {
				buffer.position(buffer.position() + bytes);
			} else {
				position += buffer.position() + bytes;
				buffer = null;
			}
		}

		private void fill() throws IOException {
			if (buffer != null) {
				position += buffer.position(); 
			}
			long left = size - position;
			buffer = fileChannel.map(FileChannel.MapMode.READ_ONLY, position, left < memBufferSize ? left : memBufferSize);
		}

		@Override
		public void readBytes(byte[] buf, int offset, int length) throws IOException {
			if (buffer == null || !buffer.hasRemaining()) {
				fill();
			}
			// XXX in fact, we may try to create a MappedByteBuffer of exactly length size here, and read right away
			while (length > 0) {
				int tail = buffer.remaining();
				if (tail == 0) {
					throw new IOException();
				}
				if (tail >= length) {
					buffer.get(buf, offset, length);
				} else {
					buffer.get(buf, offset, tail);
					fill();
				}
				offset += tail;
				length -= tail;
			}
		}

		@Override
		public byte readByte() throws IOException {
			if (buffer == null || !buffer.hasRemaining()) {
				fill();
			}
			if (buffer.hasRemaining()) {
				return buffer.get();
			}
			throw new IOException();
		}

		@Override
		public void done() {
			buffer = null;
			if (fileChannel != null) {
				try {
					fileChannel.close();
				} catch (IOException ex) {
					StreamLogFacility.newDefault().debug(getClass(), ex, null);
				}
				fileChannel = null;
			}
		}
	}

	// (almost) regular file access - FileChannel and buffers.
	private static class FileAccess extends DataAccess {
		private FileChannel fileChannel;
		private final int size;
		private ByteBuffer buffer;
		private int bufferStartInFile = 0; // offset of this.buffer in the file.

		public FileAccess(FileChannel fc, int channelSize, int bufferSizeHint, boolean useDirect) {
			fileChannel = fc;
			size = channelSize;
			final int capacity = size < bufferSizeHint ? size : bufferSizeHint;
			buffer = useDirect ? ByteBuffer.allocateDirect(capacity) : ByteBuffer.allocate(capacity);
			buffer.flip(); // or .limit(0) to indicate it's empty
		}
		
		@Override
		public boolean isEmpty() {
			return bufferStartInFile + buffer.position() >= size;
		}
		
		@Override
		public int length() {
			return size;
		}
		
		@Override
		public DataAccess reset() throws IOException {
			seek(0);
			return this;
		}
		
		@Override
		public void seek(int offset) throws IOException {
			if (offset > size) {
				throw new IllegalArgumentException();
			}
			if (offset < bufferStartInFile + buffer.limit() && offset >= bufferStartInFile) {
				buffer.position((int) (offset - bufferStartInFile));
			} else {
				// out of current buffer, invalidate it (force re-read) 
				// XXX or ever re-read it right away?
				bufferStartInFile = offset;
				buffer.clear();
				buffer.limit(0); // or .flip() to indicate we switch to reading
				fileChannel.position(offset);
			}
		}

		@Override
		public void skip(int bytes) throws IOException {
			final int newPos = buffer.position() + bytes;
			if (newPos >= 0 && newPos < buffer.limit()) {
				// no need to move file pointer, just rewind/seek buffer 
				buffer.position(newPos);
			} else {
				//
				seek(bufferStartInFile + newPos);
			}
		}

		private boolean fill() throws IOException {
			if (!buffer.hasRemaining()) {
				bufferStartInFile += buffer.limit();
				buffer.clear();
				if (bufferStartInFile < size) { // just in case there'd be any exception on EOF, not -1 
					fileChannel.read(buffer);
					// may return -1 when EOF, but empty will reflect this, hence no explicit support here   
				}
				buffer.flip();
			}
			return buffer.hasRemaining();
		}

		@Override
		public void readBytes(byte[] buf, int offset, int length) throws IOException {
			if (!buffer.hasRemaining()) {
				fill();
			}
			while (length > 0) {
				int tail = buffer.remaining();
				if (tail == 0) {
					throw new IOException(); // shall not happen provided stream contains expected data and no attempts to read past isEmpty() == true are made.
				}
				if (tail >= length) {
					buffer.get(buf, offset, length);
				} else {
					buffer.get(buf, offset, tail);
					fill();
				}
				offset += tail;
				length -= tail;
			}
		}

		@Override
		public byte readByte() throws IOException {
			if (buffer.hasRemaining()) {
				return buffer.get();
			}
			if (fill()) {
				return buffer.get();
			}
			throw new IOException();
		}

		@Override
		public void done() {
			if (buffer != null) {
				buffer = null;
			}
			if (fileChannel != null) {
				try {
					fileChannel.close();
				} catch (IOException ex) {
					StreamLogFacility.newDefault().debug(getClass(), ex, null);
				}
				fileChannel = null;
			}
		}
	}
}
