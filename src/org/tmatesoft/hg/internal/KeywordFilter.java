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
import java.util.ArrayList;
import java.util.Date;
import java.util.TreeMap;

import org.tmatesoft.hg.core.HgException;
import org.tmatesoft.hg.core.HgInvalidControlFileException;
import org.tmatesoft.hg.core.Nodeid;
import org.tmatesoft.hg.repo.HgChangelog.RawChangeset;
import org.tmatesoft.hg.repo.HgInternals;
import org.tmatesoft.hg.repo.HgRepository;
import org.tmatesoft.hg.util.Pair;
import org.tmatesoft.hg.util.Path;

/**
 *
 * @author Artem Tikhomirov
 * @author TMate Software Ltd.
 */
public class KeywordFilter implements Filter {
	// present implementation is stateless, however, filter use pattern shall not assume that. In fact, Factory may us that 
	private final HgRepository repo;
	private final boolean isExpanding;
	private final TreeMap<String,String> keywords;
	private final int minBufferLen;
	private final Path path;
	private RawChangeset latestFileCset;

	/**
	 * 
	 * @param hgRepo 
	 * @param path 
	 * @param expand <code>true</code> to expand keywords, <code>false</code> to shrink
	 */
	private KeywordFilter(HgRepository hgRepo, Path p, boolean expand) {
		repo = hgRepo;
		path = p;
		isExpanding = expand;
		keywords = new TreeMap<String,String>();
		keywords.put("Id", "Id");
		keywords.put("Revision", "Revision");
		keywords.put("Author", "Author");
		keywords.put("Date", "Date");
		keywords.put("LastChangedRevision", "LastChangedRevision");
		keywords.put("LastChangedBy", "LastChangedBy");
		keywords.put("LastChangedDate", "LastChangedDate");
		keywords.put("Source", "Source");
		keywords.put("Header", "Header");

		int l = 0;
		for (String s : keywords.keySet()) {
			if (s.length() > l) {
				l = s.length();
			}
		}
		// FIXME later may implement #filter() not to read full kw value (just "$kw:"). However, limit of maxLen + 2 would keep valid.
		// for buffers less then minBufferLen, there are chances #filter() implementation would never end
		// (i.e. for input "$LongestKey"$
		minBufferLen = l + 2 + (isExpanding ? 0 : 120 /*any reasonable constant for max possible kw value length*/);
	}

	/**
	 * @param src buffer ready to be read
	 * @return buffer ready to be read and original buffer's position modified to reflect consumed bytes. IOW, if source buffer
	 * on return has remaining bytes, they are assumed not-read (not processed) and next chunk passed to filter is supposed to 
	 * start with them  
	 */
	public ByteBuffer filter(ByteBuffer src) {
		if (src.capacity() < minBufferLen) {
			throw new IllegalStateException(String.format("Need buffer of at least %d bytes to ensure filter won't hang", minBufferLen));
		}
		ByteBuffer rv = null;
		int keywordStart = -1;
		int x = src.position();
		int copyFrom = x; // needs to be updated each time we copy a slice, but not each time we modify source index (x)
		while (x < src.limit()) {
			if (keywordStart == -1) {
				int i = indexOf(src, '$', x, false);
				if (i == -1) {
					if (rv == null) {
						return src;
					} else {
						copySlice(src, copyFrom, src.limit(), rv);
						rv.flip();
						src.position(src.limit());
						return rv;
					}
				}
				keywordStart = i;
				// fall-through
			}
			if (keywordStart >= 0) {
				int i = indexOf(src, '$', keywordStart+1, true);
				if (i == -1) {
					// end of buffer reached
					if (rv == null) {
						if (keywordStart == x) {
							// FIXME in fact, x might be equal to keywordStart and to src.position() here ('$' is first character in the buffer, 
							// and there are no other '$' not eols till the end of the buffer). This would lead to deadlock (filter won't consume any
							// bytes). To prevent this, either shall copy bytes [keywordStart..buffer.limit()) to local buffer and use it on the next invocation,
							// or add lookup of the keywords right after first '$' is found (do not wait for closing '$'). For now, large enough src buffer would be sufficient
							// not to run into such situation
							throw new IllegalStateException("Try src buffer of a greater size");
						}
						rv = ByteBuffer.allocate(keywordStart - copyFrom);
					}
					// copy all from source till latest possible kw start 
					copySlice(src, copyFrom, keywordStart, rv);
					rv.flip();
					// and tell caller we've consumed only to the potential kw start
					src.position(keywordStart);
					return rv;
				} else if (src.get(i) == '$') {
					// end of keyword, or start of a new one.
					String keyword;
					if ((keyword = matchKeyword(src, keywordStart, i)) != null) {
						if (rv == null) {
							// src.remaining(), not .capacity because src is not read, and remaining represents 
							// actual bytes count, while capacity - potential.
							// Factor of 4 is pure guess and a HACK, need to be fixed with re-expanding buffer on demand
							rv = ByteBuffer.allocate(isExpanding ? src.remaining() * 4 : src.remaining());
						}
						copySlice(src, copyFrom, keywordStart+1, rv);
						rv.put(keyword.getBytes());
						if (isExpanding) {
							rv.put((byte) ':');
							rv.put((byte) ' ');
							expandKeywordValue(keyword, rv);
							rv.put((byte) ' ');
						}
						rv.put((byte) '$');
						keywordStart = -1;
						x = i+1;
						copyFrom = x;
						continue;
					} else {
						if (rv != null) {
							// we've already did some substitution, thus need to copy bytes we've scanned. 
							copySlice(src, x, i, rv);
							copyFrom = i;
						} // no else in attempt to avoid rv creation if no real kw would be found  
						keywordStart = i;
						x = i; // '$' at i wasn't consumed, hence x points to i, not i+1. This is to avoid problems with case: "sdfsd $ asdfs $Id$ sdf"
						continue;
					}
				} else {
					assert src.get(i) == '\n' || src.get(i) == '\r';
					// line break
					if (rv != null) {
						copySlice(src, x, i+1, rv);
						copyFrom = i+1;
					}
					x = i+1;
					keywordStart = -1; // Wasn't keyword, really
					continue; // try once again
				}
			}
		}
		if (keywordStart != -1) {
			if (rv == null) {
				// no expansion happened yet, and we have potential kw start
				rv = ByteBuffer.allocate(keywordStart - src.position());
				copySlice(src, src.position(), keywordStart, rv);
			}
			src.position(keywordStart);
		}
		if (rv != null) {
			rv.flip();
			return rv;
		}
		return src;
	}
	
	/**
	 * @param keyword
	 * @param rv
	 */
	private void expandKeywordValue(String keyword, ByteBuffer rv) {
		if ("Id".equals(keyword)) {
			rv.put(identityString().getBytes());
		} else if ("Revision".equals(keyword)) {
			rv.put(revision().getBytes());
		} else if ("Author".equals(keyword)) {
			rv.put(username().getBytes());
		} else if ("Date".equals(keyword)) {
			rv.put(date().getBytes());
		} else {
			throw new IllegalStateException(String.format("Keyword %s is not yet supported", keyword));
		}
	}

	private String matchKeyword(ByteBuffer src, int kwStart, int kwEnd) {
		assert kwEnd - kwStart - 1 > 0;
		assert src.get(kwStart) == src.get(kwEnd) && src.get(kwEnd) == '$';
		char[] chars = new char[kwEnd - kwStart - 1];
		int i;
		for (i = 0; i < chars.length; i++) {
			char c = (char) src.get(kwStart + 1 + i);
			if (c == ':') {
				break;
			}
			chars[i] = c;
		}
		String kw = new String(chars, 0, i);
//		XXX may use subMap to look up keywords based on few available characters (not waiting till closing $)
//		System.out.println(keywords.subMap("I", "J"));
//		System.out.println(keywords.subMap("A", "B"));
//		System.out.println(keywords.subMap("Au", "B"));
		return keywords.get(kw);
	}
	
	// copies part of the src buffer, [from..to). doesn't modify src position
	static void copySlice(ByteBuffer src, int from, int to, ByteBuffer dst) {
		if (to > src.limit()) {
			throw new IllegalArgumentException("Bad right boundary");
		}
		if (dst.remaining() < to - from) {
			throw new IllegalArgumentException("Not enough room in the destination buffer");
		}
		for (int i = from; i < to; i++) {
			dst.put(src.get(i));
		}
	}

	private static int indexOf(ByteBuffer b, char ch, int from, boolean newlineBreaks) {
		for (int i = from; i < b.limit(); i++) {
			byte c = b.get(i);
			if (ch == c) {
				return i;
			}
			if (newlineBreaks && (c == '\n' || c == '\r')) {
				return i;
			}
		}
		return -1;
	}

	private String identityString() {
		return String.format("%s,v %s %s %s", path, revision(), date(), username());
	}

	private String revision() {
		try {
			// FIXME add cset's nodeid into Changeset class
			int csetRev = repo.getFileNode(path).getChangesetRevisionIndex(HgRepository.TIP);
			return repo.getChangelog().getRevision(csetRev).shortNotation();
		} catch (HgException ex) {
			HgInternals.getContext(repo).getLog().error(getClass(), ex, null);
			return Nodeid.NULL.shortNotation(); // XXX perhaps, might return anything better? Not sure how hg approaches this. 
		}
	}
	
	private String username() {
		try {
			return getChangeset().user();
		} catch (HgException ex) {
			HgInternals.getContext(repo).getLog().error(getClass(), ex, null);
			return "";
		}
	}
	
	private String date() {
		Date d;
		try {
			d = getChangeset().date();
		} catch (HgException ex) {
			HgInternals.getContext(repo).getLog().error(getClass(), ex, null);
			d = new Date(0l);
		}
		return String.format("%tY/%<tm/%<td %<tH:%<tM:%<tS", d);
	}
	
	private RawChangeset getChangeset() throws HgInvalidControlFileException {
		if (latestFileCset == null) {
			// XXX consider use of ChangelogHelper
			int csetRev = repo.getFileNode(path).getChangesetRevisionIndex(HgRepository.TIP);
			latestFileCset = repo.getChangelog().range(csetRev, csetRev).get(0);
		}
		return latestFileCset;
	}

	public static class Factory implements Filter.Factory {
		
		private HgRepository repo;
		private Path.Matcher matcher;

		public void initialize(HgRepository hgRepo) {
			repo = hgRepo;
			ArrayList<String> patterns = new ArrayList<String>();
			for (Pair<String,String> e : hgRepo.getConfiguration().getSection("keyword")) {
				if (!"ignore".equalsIgnoreCase(e.second())) {
					patterns.add(e.first());
				}
			}
			matcher = new PathGlobMatcher(patterns.toArray(new String[patterns.size()]));
			// TODO read and respect keyword patterns from [keywordmaps]
		}

		public Filter create(Path path, Options opts) {
			if (matcher.accept(path)) {
				return new KeywordFilter(repo, path, opts.getDirection() == Filter.Direction.FromRepo);
			}
			return null;
		}
	}

//
//	public static void main(String[] args) throws Exception {
//		FileInputStream fis = new FileInputStream(new File("/temp/kwoutput.txt"));
//		FileOutputStream fos = new FileOutputStream(new File("/temp/kwoutput2.txt"));
//		ByteBuffer b = ByteBuffer.allocate(256);
//		KeywordFilter kwFilter = new KeywordFilter(false);
//		while (fis.getChannel().read(b) != -1) {
//			b.flip(); // get ready to be read
//			ByteBuffer f = kwFilter.filter(b);
//			fos.getChannel().write(f); // XXX in fact, f may not be fully consumed
//			if (b.hasRemaining()) {
//				b.compact();
//			} else {
//				b.clear();
//			}
//		}
//		fis.close();
//		fos.flush();
//		fos.close();
//	}
}
