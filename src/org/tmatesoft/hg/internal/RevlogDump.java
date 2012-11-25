/*
 * Copyright (c) 2010-2012 TMate Software Ltd
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

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.DataInput;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.zip.Inflater;

/**
 * Utility to test/debug/troubleshoot
 *  
 * @author Artem Tikhomirov
 * @author TMate Software Ltd.
 */
public class RevlogDump {

	/**
	 * Takes 3 command line arguments - 
	 *   repository path, 
	 *   path to index file (i.e. store/data/hello.c.i) in the repository (relative) 
	 *   and "dumpData" whether to print actual content or just revlog headers 
	 */
	public static void main(String[] args) throws Exception {
		String repo = "/temp/hg/hello/.hg/";
		String filename = "store/00changelog.i";
//		String filename = "store/data/hello.c.i";
//		String filename = "store/data/docs/readme.i";
		boolean dumpDataFull = true;
		boolean dumpDataStats = false;
		if (args.length > 1) {
			repo = args[0];
			filename = args[1];
			dumpDataFull = args.length > 2 ? "dumpData".equals(args[2]) : false;
			dumpDataStats = args.length > 2 ? "dumpDataStats".equals(args[2]) : false;
		}
		final boolean needRevData = dumpDataFull || dumpDataStats; 
		//
		DataInputStream dis = new DataInputStream(new BufferedInputStream(new FileInputStream(new File(repo, filename))));
		DataInput di = dis;
		dis.mark(10);
		int versionField = di.readInt();
		dis.reset();
		final int INLINEDATA = 1 << 16;
		
		final boolean inlineData = (versionField & INLINEDATA) != 0;
		System.out.printf("%#8x, inline: %b\n", versionField, inlineData);
		FileChannel dataStream = null; 
		if (!inlineData && needRevData) {
			dataStream = new FileInputStream(new File(repo, filename.substring(0, filename.length()-2) + ".d")).getChannel();
		}
		System.out.println("Index    Offset      Flags     Packed     Actual   Base Rev   Link Rev  Parent1  Parent2     nodeid");
		int entryIndex = 0;
		while (dis.available() > 0) {
			long l = di.readLong();
			long offset = entryIndex == 0 ? 0 : (l >>> 16);
			int flags = (int) (l & 0X0FFFF);
			int compressedLen = di.readInt();
			int actualLen = di.readInt();
			int baseRevision = di.readInt();
			int linkRevision = di.readInt();
			int parent1Revision = di.readInt();
			int parent2Revision = di.readInt();
			byte[] buf = new byte[32];
			di.readFully(buf, 12, 20);
			dis.skipBytes(12); 
			// CAN'T USE skip() here without extra precautions. E.g. I ran into situation when 
			// buffer was 8192 and BufferedInputStream was at position 8182 before attempt to skip(12). 
			// BIS silently skips available bytes and leaves me two extra bytes that ruin the rest of the code.
			System.out.printf("%4d:%14d %6X %10d %10d %10d %10d %8d %8d     %040x\n", entryIndex, offset, flags, compressedLen, actualLen, baseRevision, linkRevision, parent1Revision, parent2Revision, new BigInteger(buf));
			String resultString;
			byte[] data = new byte[compressedLen];
			if (inlineData) {
				di.readFully(data);
			} else if (needRevData) {
				dataStream.position(offset);
				dataStream.read(ByteBuffer.wrap(data));
			}
			if (needRevData) {
				if (compressedLen == 0) {
					resultString = "<NO DATA>";
				} else {
					if (data[0] == 0x78 /* 'x' */) {
						Inflater zlib = new Inflater();
						zlib.setInput(data, 0, compressedLen);
						byte[] result = new byte[actualLen*2];
						int resultLen = zlib.inflate(result);
						zlib.end();
						resultString = buildString(result, 0, resultLen, baseRevision != entryIndex, dumpDataFull);
					} else if (data[0] == 0x75 /* 'u' */) {
						resultString = buildString(data, 1, data.length - 1, baseRevision != entryIndex, dumpDataFull);
					} else {
						resultString = buildString(data, 0, data.length, baseRevision != entryIndex, dumpDataFull);
					}
				}
				System.out.println(resultString);
			}
			entryIndex++;
		}
		dis.close();
		if (dataStream != null) {
			dataStream.close();
		}
		//
	}
	
	private static String buildString(byte[] data, int offset, int len, boolean isPatch, boolean completeDataDump) throws IOException, UnsupportedEncodingException {
		if (isPatch) {
			DataInputStream dis = new DataInputStream(new ByteArrayInputStream(data, offset, len));
			StringBuilder sb = new StringBuilder();
			sb.append("<PATCH>:\n");
			while (dis.available() > 0) {
				int s = dis.readInt();
				int e = dis.readInt();
				int l = dis.readInt();
				sb.append(String.format("%d..%d, %d", s, e, l));
				if (completeDataDump) {
					byte[] src = new byte[l];
					dis.read(src, 0, l);
					sb.append(":");
					sb.append(new String(src, 0, l, "UTF-8"));
				} else {
					dis.skipBytes(l);
				}
				sb.append('\n');
			}
			return sb.toString();
		} else {
			if (completeDataDump) {
				return new String(data, offset, len, "UTF-8");
			}
			return String.format("<DATA>:%d bytes", len-offset);
		}
	}
}
