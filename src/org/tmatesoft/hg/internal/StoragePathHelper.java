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

import java.util.Arrays;
import java.util.TreeSet;

import org.tmatesoft.hg.util.PathRewrite;

/**
 * @see http://mercurial.selenic.com/wiki/CaseFoldingPlan
 * @see http://mercurial.selenic.com/wiki/fncacheRepoFormat
 * 
 * @author Artem Tikhomirov
 * @author TMate Software Ltd.
 */
class StoragePathHelper implements PathRewrite {
	
	private final boolean store;
	private final boolean fncache;
	private final boolean dotencode;

	public StoragePathHelper(boolean isStore, boolean isFncache, boolean isDotencode) {
		store = isStore;
		fncache = isFncache;
		dotencode = isDotencode;
	}

	// FIXME document what path argument is, whether it includes .i or .d, and whether it's 'normalized' (slashes) or not.
	// since .hg/store keeps both .i files and files without extension (e.g. fncache), guees, for data == false 
	// we shall assume path has extension
	public CharSequence rewrite(CharSequence p) {
		final String STR_STORE = "store/";
		final String STR_DATA = "data/";
		final String STR_DH = "dh/";
		final String reservedChars = "\\:*?\"<>|";
		char[] hexByte = new char[2];
		
		String path = p.toString();
		path = path.replace(".hg/", ".hg.hg/").replace(".i/", ".i.hg/").replace(".d/", ".d.hg/");
		StringBuilder sb = new StringBuilder(path.length() << 1);
		if (store || fncache) {
			// encodefilename
			for (int i = 0; i < path.length(); i++) {
				final char ch = path.charAt(i);
				if (ch >= 'a' && ch <= 'z') {
					sb.append(ch); // POIRAE
				} else if (ch >= 'A' && ch <= 'Z') {
					sb.append('_');
					sb.append(Character.toLowerCase(ch)); // Perhaps, (char) (((int) ch) + 32)? Even better, |= 0x20? 
				} else if (reservedChars.indexOf(ch) != -1) {
					sb.append('~');
					sb.append(toHexByte(ch, hexByte));
				} else if ((ch >= '~' /*126*/ && ch <= 255) || ch < ' ' /*32*/) {
					sb.append('~');
					sb.append(toHexByte(ch, hexByte));
				} else if (ch == '_') {
					sb.append('_');
					sb.append('_');
				} else {
					sb.append(ch);
				}
			}
			// auxencode
			if (fncache) {
				encodeWindowsDeviceNames(sb);
			}
		}
		final int MAX_PATH_LEN = 120;
		if (fncache && (sb.length() + STR_DATA.length() + ".i".length() > MAX_PATH_LEN)) {
			String digest = new DigestHelper().sha1(STR_DATA, path, ".i").asHexString();
			final int DIR_PREFIX_LEN = 8;
			 // not sure why (-4) is here. 120 - 40 = up to 80 for path with ext. dh/ + ext(.i) = 3+2
			final int MAX_DIR_PREFIX = 8 * (DIR_PREFIX_LEN + 1) - 4;
			sb = new StringBuilder(MAX_PATH_LEN);
			for (int i = 0; i < path.length(); i++) {
				final char ch = path.charAt(i);
				if (ch >= 'a' && ch <= 'z') {
					sb.append(ch);
				} else if (ch >= 'A' && ch <= 'Z') {
					sb.append((char) (ch | 0x20)); // lowercase 
				} else if (reservedChars.indexOf(ch) != -1) {
					sb.append('~');
					sb.append(toHexByte(ch, hexByte));
				} else if ((ch >= '~' /*126*/ && ch <= 255) || ch < ' ' /*32*/) {
					sb.append('~');
					sb.append(toHexByte(ch, hexByte));
				} else {
					sb.append(ch);
				}
			}
			encodeWindowsDeviceNames(sb);
			int fnameStart = sb.lastIndexOf("/"); // since we rewrite file names, it never ends with slash (for dirs, I'd pass length-2);
			StringBuilder completeHashName = new StringBuilder(MAX_PATH_LEN);
			completeHashName.append(STR_STORE);
			completeHashName.append(STR_DH);
			if (fnameStart == -1) {
				// no dirs, just long filename
				sb.setLength(MAX_PATH_LEN - 40 /*digest.length()*/ - STR_DH.length() - ".i".length());
				completeHashName.append(sb);
			} else {
				StringBuilder sb2 = new StringBuilder(MAX_PATH_LEN);
				int x = 0;
				do {
					int i = sb.indexOf("/", x);
					final int sb2Len = sb2.length(); 
					if (i-x <= DIR_PREFIX_LEN) { // a b c d e f g h /
						sb2.append(sb, x, i + 1); // with slash
					} else {
						sb2.append(sb, x, x + DIR_PREFIX_LEN);
						// may unexpectedly end with bad character
						final int last = sb2.length()-1;
						char lastChar = sb2.charAt(last); 
						assert lastChar == sb.charAt(x + DIR_PREFIX_LEN - 1);
						if (lastChar == '.' || lastChar == ' ') {
							sb2.setCharAt(last, '_');
						}
						sb2.append('/');
					}
					if (sb2.length()-1 > MAX_DIR_PREFIX) {
						sb2.setLength(sb2Len); // strip off last segment, it's too much
						break;
					}
					x = i+1; 
				} while (x < fnameStart);
				assert sb2.charAt(sb2.length() - 1) == '/';
				int left = MAX_PATH_LEN - sb2.length() - 40 /*digest.length()*/ - STR_DH.length() - ".i".length();
				assert left >= 0;
				fnameStart++; // move from / to actual name
				if (fnameStart + left > sb.length()) {
					// there left less chars in the mangled name that we can fit
					sb2.append(sb, fnameStart, sb.length());
					int stillAvailable = (fnameStart+left) - sb.length();
					// stillAvailable > 0;
					sb2.append(".i", 0, stillAvailable > 2 ? 2 : stillAvailable);
				} else {
					// add as much as we can
					sb2.append(sb, fnameStart, fnameStart+left);
				}
				completeHashName.append(sb2);
			}
			completeHashName.append(digest);
			sb = completeHashName;
		} else if (store) {
			sb.insert(0, STR_STORE + STR_DATA);
		}
		sb.append(".i");
		return sb.toString();
	}
	
	private void encodeWindowsDeviceNames(StringBuilder sb) {
		char[] hexByte = new char[2];
		int x = 0; // last segment start
		final TreeSet<String> windowsReservedFilenames = new TreeSet<String>();
		windowsReservedFilenames.addAll(Arrays.asList("con prn aux nul com1 com2 com3 com4 com5 com6 com7 com8 com9 lpt1 lpt2 lpt3 lpt4 lpt5 lpt6 lpt7 lpt8 lpt9".split(" "))); 
		do {
			int i = sb.indexOf("/", x);
			if (i == -1) {
				i = sb.length();
			}
			// windows reserved filenames are at least of length 3 
			if (i - x >= 3) {
				boolean found = false;
				if (i-x == 3 || i-x == 4) {
					found = windowsReservedFilenames.contains(sb.subSequence(x, i));
				} else if (sb.charAt(x+3) == '.') { // implicit i-x > 3
					found = windowsReservedFilenames.contains(sb.subSequence(x, x+3));
				} else if (i-x > 4 && sb.charAt(x+4) == '.') {
					found = windowsReservedFilenames.contains(sb.subSequence(x, x+4));
				}
				if (found) {
					sb.insert(x+3, toHexByte(sb.charAt(x+2), hexByte));
					sb.setCharAt(x+2, '~');
					i += 2;
				}
			}
			if (dotencode && (sb.charAt(x) == '.' || sb.charAt(x) == ' ')) {
				sb.insert(x+1, toHexByte(sb.charAt(x), hexByte));
				sb.setCharAt(x, '~'); // setChar *after* charAt/insert to get ~2e, not ~7e for '.'
				i += 2;
			}
			x = i+1;
		} while (x < sb.length());
	}

	private static char[] toHexByte(int ch, char[] buf) {
		assert buf.length > 1;
		final String hexDigits = "0123456789abcdef";
		buf[0] = hexDigits.charAt((ch & 0x00F0) >>> 4);
		buf[1] = hexDigits.charAt(ch & 0x0F);
		return buf;
	}
}
