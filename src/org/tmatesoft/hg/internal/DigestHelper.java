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
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import org.tmatesoft.hg.core.HgBadStateException;
import org.tmatesoft.hg.core.Nodeid;


/**
 * <pre>
 * DigestHelper dh;
 * dh.sha1(...).asHexString();
 *  or 
 * dh = dh.sha1(...);
 * nodeid.equalsTo(dh.asBinary());
 * </pre>
 *
 * @author Artem Tikhomirov
 * @author TMate Software Ltd.
 */
public class DigestHelper {
	private MessageDigest sha1;
	private byte[] digest;

	public DigestHelper() {
	}
	
	private MessageDigest getSHA1() {
		if (sha1 == null) {
			try {
				sha1 = MessageDigest.getInstance("SHA-1");
			} catch (NoSuchAlgorithmException ex) {
				// could hardly happen, JDK from Sun always has sha1.
				throw new HgBadStateException(ex);
			}
		}
		return sha1;
	}


	public DigestHelper sha1(Nodeid nodeid1, Nodeid nodeid2, byte[] data) {
		return sha1(nodeid1.toByteArray(), nodeid2.toByteArray(), data);
	}

	//  sha1_digest(min(p1,p2) ++ max(p1,p2) ++ final_text)
	public DigestHelper sha1(byte[] nodeidParent1, byte[] nodeidParent2, byte[] data) {
		MessageDigest alg = getSHA1();
		if ((nodeidParent1[0] & 0x00FF) < (nodeidParent2[0] & 0x00FF)) { 
			alg.update(nodeidParent1);
			alg.update(nodeidParent2);
		} else {
			alg.update(nodeidParent2);
			alg.update(nodeidParent1);
		}
		digest = alg.digest(data);
		assert digest.length == 20;
		return this;
	}
	
	public String asHexString() {
		if (digest == null) {
			throw new IllegalStateException("Shall init with sha1() call first");
		}
		return toHexString(digest, 0, digest.length);
	}
	
	// by reference, be careful not to modify (or #clone() if needed)
	public byte[] asBinary() {
		if (digest == null) {
			throw new IllegalStateException("Shall init with sha1() call first");
		}
		return digest; 
	}

	// XXX perhaps, digest functions should throw an exception, as it's caller responsibility to deal with eof, etc
	public DigestHelper sha1(InputStream is /*ByteBuffer*/) throws IOException {
		MessageDigest alg = getSHA1();
		byte[] buf = new byte[1024];
		int c;
		while ((c = is.read(buf)) != -1) {
			alg.update(buf, 0, c);
		}
		digest = alg.digest();
		return this;
	}
	
	public DigestHelper sha1(CharSequence... seq) {
		MessageDigest alg = getSHA1();
		for (CharSequence s : seq) {
			byte[] b = s.toString().getBytes();
			alg.update(b);
		}
		digest = alg.digest();
		return this;
	}

	public static String toHexString(byte[] data, final int offset, final int count) {
		char[] result = new char[count << 1];
		final String hexDigits = "0123456789abcdef";
		final int end = offset+count;
		for (int i = offset, j = 0; i < end; i++) {
			result[j++] = hexDigits.charAt((data[i] >>> 4) & 0x0F);
			result[j++] = hexDigits.charAt(data[i] & 0x0F);
		}
		return new String(result);
	}

	public static boolean ascii2bin(byte[] ascii, int offset, int len, byte[] binary) {
		assert len % 2 == 0;
		assert binary.length >= len >>> 1;

		boolean zeroBytes = true;
		for (int i = 0, j = offset; i < len >>> 1; i++) {
			int b = ascii[j++] & 0xCF; // -0x30 to get decimal digit out from their char, and to uppercase if a letter 
			int hiNibble = b > 64 ? b - 55 : b;
			b = ascii[j++] & 0xCF;
			int lowNibble = b > 64 ? b - 55 : b;
			if (hiNibble >= 16 || lowNibble >= 16) {
				throw new IllegalArgumentException(String.format("Characters '%c%c' (%1$d and %2$d) at index %d are not valid hex digits", ascii[j-2], ascii[j-1], j-2));
			}
			b = (((hiNibble << 4) | lowNibble) & 0xFF);
			binary[i] = (byte) b;
			zeroBytes = zeroBytes && b == 0;
		}
		return zeroBytes;
	}
}
