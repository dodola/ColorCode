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

import static java.lang.Math.max;
import static java.lang.Math.min;
import static org.tmatesoft.hg.internal.Filter.Direction.FromRepo;
import static org.tmatesoft.hg.internal.Filter.Direction.ToRepo;
import static org.tmatesoft.hg.internal.KeywordFilter.copySlice;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Map;

import org.tmatesoft.hg.core.HgBadStateException;
import org.tmatesoft.hg.repo.HgInternals;
import org.tmatesoft.hg.repo.HgRepository;
import org.tmatesoft.hg.util.Adaptable;
import org.tmatesoft.hg.util.Path;

/**
 *
 * @author Artem Tikhomirov
 * @author TMate Software Ltd.
 */
public class NewlineFilter implements Filter, Preview, Adaptable {

	// if processInconsistent is false, filter simply pass incorrect newline characters (single \r or \r\n on *nix and single \n on Windows) as is,
	// i.e. doesn't try to convert them into appropriate newline characters. 
	// XXX revisit if Keyword extension behaves differently - WTF???
	private final boolean processInconsistent;
	private final boolean winToNix;
	
	// NOTE, if processInconsistent == true, foundCRLF and foundLoneLF are not initialized
	private boolean foundLoneLF = false;
	private boolean foundCRLF = false;

	// next two factory methods for test purposes
	public static NewlineFilter createWin2Nix(boolean processMixed) {
		return new NewlineFilter(!processMixed, 0);
	}
	
	public static NewlineFilter createNix2Win(boolean processMixed) {
		return new NewlineFilter(!processMixed, 1);
	}

	private NewlineFilter(boolean onlyConsistent, int transform) {
		winToNix = transform == 0;
		processInconsistent = !onlyConsistent;
	}

	public ByteBuffer filter(ByteBuffer src) {
		if (!processInconsistent && !previewDone) {
			throw new HgBadStateException("This filter requires preview operation prior to actual filtering when eol.only-consistent is true");
		}
		if (!processInconsistent && foundLoneLF && foundCRLF) {
			// do not process inconsistent newlines
			return src;
		}
		if (winToNix) {
			if (!processInconsistent && !foundCRLF) {
				// no reason to process if no CRLF in the data stream
				return src;
			}
			return win2nix(src);
		} else {
			if (!processInconsistent && !foundLoneLF) {
				return src;
			}
			return nix2win(src);
		}
	}
	
	public <T> T getAdapter(Class<T> adapterClass) {
		// conditionally through getAdapter 
		if (Preview.class == adapterClass) {
			// when processInconsistent is false, we need to preview data stream to ensure line terminators are consistent.
			// otherwise, no need to look into the stream
			if (!processInconsistent) {
				return adapterClass.cast(this);
			}
		}
		return null;
	}
	
	private boolean prevBufLastByteWasCR = false;
	private boolean previewDone = false;

	public void preview(ByteBuffer src) {
		previewDone = true; // guard
		if (processInconsistent) {
			// gonna handle them anyway, no need to check. TODO Do not implement Preview directly, but rather 
			return;
		}
		if (foundLoneLF && foundCRLF) {
			// already know it's inconsistent
			return;
		}
		final byte CR = (byte) '\r';
		final byte LF = (byte) '\n';
		int x = src.position();
		while (x < src.limit()) {
			int in = indexOf(LF, src, x);
			if (in == -1) {
				// no line feed, but what if it's CRLF broken in the middle?
				prevBufLastByteWasCR = CR == src.get(src.limit() - 1);
				return;
			}
			if (in == 0) {
				if (prevBufLastByteWasCR) {
					foundCRLF = true;
				} else {
					foundLoneLF = true;
				}
			} else { // in > 0 && in >= x
				if (src.get(in - 1) == CR) {
					foundCRLF = true;
				} else {
					foundLoneLF = true;
				}
			}
			if (foundCRLF && foundLoneLF) {
				return;
			}
			x = in + 1;
		}
	}

	private ByteBuffer win2nix(ByteBuffer src) {
		int lookupStart = src.position(); // source index
		ByteBuffer dst = null;
		final byte CR = (byte) '\r';
		final byte LF = (byte) '\n';
		while (lookupStart < src.limit()) {
			// x, lookupStart, ir and in are absolute positions within src buffer, which is never read with modifying operations
			int ir = indexOf(CR, src, lookupStart);
			int in = indexOf(LF, src, lookupStart);
			if (in != -1) {
				if (ir == -1 || ir > in) {
					// lone LF. CR, if present, goes after LF, process up to that lone, closest LF; let next iteration decide what to do with CR@ir
					if (!processInconsistent && foundCRLF) {
						assert foundLoneLF == true : "preview() shall initialize this";
						fail(src, in);
					}
					dst = consume(src, lookupStart, in+1, dst);
					lookupStart = in + 1;
				} else {
					// ir < in
					if (onlyCRup2limit(src, ir, in)) {
						// CR...CRLF;
						if (!processInconsistent && foundLoneLF) {
							assert foundCRLF == true : "preview() shall initialize this";
							fail(src, ir);
						}
						dst = consume(src, lookupStart, ir, dst);
						dst.put(LF);
						lookupStart = in+1;
					} else {
						// CR...CR...^CR....LF
						dst = consume(src, lookupStart, ir+1, dst);
						// although can search for ^CR, here I copy CR one by one as I don't expect huge sequences of CR to optimize for
						lookupStart = ir+1;
					}
				}
			} else {
				// no newlines
				if (ir != -1 && onlyCRup2limit(src, ir, src.limit())) {
					// \r as last character(s) is the only case we care about when there're no LF found
					// cases like \r\r\r<EOB>\n shall be handled like \r\n, hence onlyCRup2limit
					dst = consume(src, lookupStart, ir, dst);
					lookupStart = src.limit() - 1; // leave only last CR for next buffer
				} else {
					// consume all. don't create a copy of src if there's no dst yet
					if (dst != null) {
						copySlice(src, lookupStart, src.limit(), dst);
						lookupStart = src.limit();
					}
				}
				break;
			}
		}
		src.position(lookupStart); // mark we've consumed up to x
		return dst == null ? src : (ByteBuffer) dst.flip();
	}
	
	// true if [from..limit) are CR
	private static boolean onlyCRup2limit(ByteBuffer src, int from, int limit) {
		// extended version of (ir+1 == src.limit()): check all in [ir..src.limit) are CR
		for (int i = from; i < limit; i++) {
			if (src.get(i) != '\r') {
				return false;
			}
		}
		return true;
	}
	private static ByteBuffer consume(ByteBuffer src, int from, int to, ByteBuffer dst) {
		if (dst == null) {
			dst = ByteBuffer.allocate(src.remaining());
		}
		copySlice(src, from, to, dst);
		return dst;
	}

	private ByteBuffer nix2win(ByteBuffer src) {
		int x = src.position();
		ByteBuffer dst = null;
		final byte CR = (byte) '\r';
		final byte LF = (byte) '\n';
		while (x < src.limit()) {
			int in = indexOf(LF, src, x);
			if (in != -1) {
				if (in > x && src.get(in - 1) == CR) {
					// found CRLF
					if (!processInconsistent && foundLoneLF) {
						assert foundCRLF == true : "preview() shall initialize this";
						fail(src, in-1);
					}
					if (dst == null) {
						dst = ByteBuffer.allocate(src.remaining() * 2);
					}
					copySlice(src, x, in+1, dst);
					x = in + 1;
				} else {
					// found stand-alone LF, need to output CRLF
					if (!processInconsistent && foundCRLF) {
						assert foundLoneLF == true : "preview() shall initialize this";
						fail(src, in);
					}
					if (dst == null) {
						dst = ByteBuffer.allocate(src.remaining() * 2);
					}
					copySlice(src, x, in, dst);
					dst.put(CR);
					dst.put(LF);
					x = in + 1;
				}
			} else {
				// no newlines (no LF), just copy what left
				if (dst != null) {
					copySlice(src, x, src.limit(), dst);
					x = src.limit();
				}
				break;
			}
		}
		src.position(x);
		return dst == null ? src : (ByteBuffer) dst.flip();
	}


	// Test: nlFilter.fail(ByteBuffer.wrap(new "test string".getBytes()), 5);
	private void fail(ByteBuffer b, int pos) {
		StringBuilder sb = new StringBuilder();
		for (int i = max(pos-10, 0), x = min(pos + 10, b.limit()); i < x; i++) {
			sb.append(String.format("%02x ", b.get(i)));
		}
		throw new HgBadStateException(String.format("Inconsistent newline characters in the stream %s (char 0x%x, local index:%d)", sb.toString(), b.get(pos), pos));
	}

	private static int indexOf(byte ch, ByteBuffer b, int from) {
		return indexOf(ch, b, from, b.limit());
	}

	// looks up in buf[from..to)
	private static int indexOf(byte ch, ByteBuffer b, int from, int to) {
		for (int i = from; i < to; i++) {
			byte c = b.get(i);
			if (ch == c) {
				return i;
			}
		}
		return -1;
	}

	public static class Factory implements Filter.Factory {
		private boolean processOnlyConsistent = true;
		private Path.Matcher lfMatcher;
		private Path.Matcher crlfMatcher;
		private Path.Matcher binMatcher;
		private Path.Matcher nativeMatcher;
		private String nativeRepoFormat;
		private String nativeOSFormat;

		public void initialize(HgRepository hgRepo) {
			processOnlyConsistent = hgRepo.getConfiguration().getBooleanValue("eol", "only-consistent", true);
			File cfgFile = new File(hgRepo.getWorkingDir(), ".hgeol");
			if (!cfgFile.canRead()) {
				return;
			}
			// XXX if .hgeol is not checked out, we may get it from repository
//			HgDataFile cfgFileNode = hgRepo.getFileNode(".hgeol");
//			if (!cfgFileNode.exists()) {
//				return;
//			}
			// XXX perhaps, add HgDataFile.hasWorkingCopy and workingCopyContent()?
			ConfigFile hgeol = new ConfigFile();
			try {
				hgeol.addLocation(cfgFile);
			} catch (IOException ex) {
				HgInternals.getContext(hgRepo).getLog().warn(getClass(), ex, null);
			}
			nativeRepoFormat = hgeol.getSection("repository").get("native");
			if (nativeRepoFormat == null) {
				nativeRepoFormat = "LF";
			}
			final String os = System.getProperty("os.name"); // XXX need centralized set of properties
			nativeOSFormat = os.indexOf("Windows") != -1 ? "CRLF" : "LF";
			// I assume pattern ordering in .hgeol is not important
			ArrayList<String> lfPatterns = new ArrayList<String>();
			ArrayList<String> crlfPatterns = new ArrayList<String>();
			ArrayList<String> nativePatterns = new ArrayList<String>();
			ArrayList<String> binPatterns = new ArrayList<String>();
			for (Map.Entry<String,String> e : hgeol.getSection("patterns").entrySet()) {
				if ("CRLF".equals(e.getValue())) {
					crlfPatterns.add(e.getKey());
				} else if ("LF".equals(e.getValue())) {
					lfPatterns.add(e.getKey());
				} else if ("native".equals(e.getValue())) {
					nativePatterns.add(e.getKey());
				} else if ("BIN".equals(e.getValue())) {
					binPatterns.add(e.getKey());
				} else {
					HgInternals.getContext(hgRepo).getLog().warn(getClass(), "Can't recognize .hgeol entry: %s for %s", e.getValue(), e.getKey());
				}
			}
			if (!crlfPatterns.isEmpty()) {
				crlfMatcher = new PathGlobMatcher(crlfPatterns.toArray(new String[crlfPatterns.size()]));
			}
			if (!lfPatterns.isEmpty()) {
				lfMatcher = new PathGlobMatcher(lfPatterns.toArray(new String[lfPatterns.size()]));
			}
			if (!binPatterns.isEmpty()) {
				binMatcher = new PathGlobMatcher(binPatterns.toArray(new String[binPatterns.size()]));
			}
			if (!nativePatterns.isEmpty()) {
				nativeMatcher = new PathGlobMatcher(nativePatterns.toArray(new String[nativePatterns.size()]));
			}
		}

		public Filter create(Path path, Options opts) {
			if (binMatcher == null && crlfMatcher == null && lfMatcher == null && nativeMatcher == null) {
				// not initialized - perhaps, no .hgeol found
				return null;
			}
			if (binMatcher != null && binMatcher.accept(path)) {
				return null;
			}
			if (crlfMatcher != null && crlfMatcher.accept(path)) {
				return new NewlineFilter(processOnlyConsistent, 1);
			} else if (lfMatcher != null && lfMatcher.accept(path)) {
				return new NewlineFilter(processOnlyConsistent, 0);
			} else if (nativeMatcher != null && nativeMatcher.accept(path)) {
				if (nativeOSFormat.equals(nativeRepoFormat)) {
					return null;
				}
				if (opts.getDirection() == FromRepo) {
					int transform = "CRLF".equals(nativeOSFormat) ? 1 : 0;
					return new NewlineFilter(processOnlyConsistent, transform);
				} else if (opts.getDirection() == ToRepo) {
					int transform = "CRLF".equals(nativeOSFormat) ? 0 : 1;
					return new NewlineFilter(processOnlyConsistent, transform);
				}
				return null;
			}
			return null;
		}
	}
}
