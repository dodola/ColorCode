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
import java.util.ArrayList;
import java.util.Formatter;

/**
 * @see http://mercurial.selenic.com/wiki/BundleFormat, in Changelog group description
 * 
 * range [start..end] in original source gets replaced with data of length (do not keep, use data.length instead)
 * range [end(i)..start(i+1)] is copied from the source
 * 
 * @author Artem Tikhomirov
 * @author TMate Software Ltd.
 */
public final class Patch {
	private final IntVector starts, ends;
	private final ArrayList<byte[]> data;

	private static byte[] generate(int c) {
		byte[] rv = new byte[c];
		for (int i = 0; i < c; i++) {
			byte x = (byte) ('a' + i);
			rv[i] = x;
		}
		return rv;
	}

	public static void main(String[] args) {
		Patch p1 = new Patch(), p2 = new Patch();
		// simple cases (one element in either patch)
		// III: (1,10 20) & (5,15,15) p2End from [p1End..p1AppliedEnd] (i.e. within p1 range but index is past p2 end index) 
		//  II: (1,10,7) & (3,15,15) insideP2 = true and no more p1 entries
		//  II: (1,1,10) & (3,11,15)
		// independent: (1,10,10) & (15,25,10);  (15, 25, 10) & (1, 10, 10) 
		//   I: (15, 25, 10) & (10, 20, 10). result: [10, 20, 10] [20, 25, 5]
		//  IV: (15, 25, 10) & (10, 30, 20)
		// 
		// cycle with insideP2
		//
		// cycle with insideP1
		//
		// multiple elements in patches (offsets)
		p1.add(15, 25, generate(10));
		p2.add(10, 30, generate(20));
		System.out.println("p1: " + p1);
		System.out.println("p2: " + p2);
		Patch r = p1.apply(p2);
		System.out.println("r: " + r);
	}

	public Patch() {
		starts = new IntVector();
		ends = new IntVector();
		data = new ArrayList<byte[]>();
	}
	
	public String toString() {
		StringBuilder sb = new StringBuilder();
		Formatter f = new Formatter(sb);
		for (int i = 0; i < count(); i++) {
			f.format("[%d, %d, %d] ", starts.get(i), ends.get(i), data.get(i).length);
		}
		return sb.toString();
	}
	
	public int count() {
		return data.size();
	}

	// number of bytes this patch will add (or remove, if negative) from the base revision
	private int patchSizeDelta() {
		int rv = 0;
		int prevEnd = 0;
		for (int i = 0, x = data.size(); i < x; i++) {
			final int start = starts.get(i);
			final int len = data.get(i).length;
			rv += start - prevEnd; // would copy from original
			rv += len; // and add new
			prevEnd = ends.get(i);
		}
		rv -= prevEnd;
		return rv;
	}
	
	public byte[] apply(DataAccess baseRevisionContent, int outcomeLen) throws IOException {
		if (outcomeLen == -1) {
			outcomeLen = baseRevisionContent.length() + patchSizeDelta();
		}
		int prevEnd = 0, destIndex = 0;
		byte[] rv = new byte[outcomeLen];
		for (int i = 0, x = data.size(); i < x; i++) {
			final int start = starts.get(i);
			baseRevisionContent.seek(prevEnd);
			// copy source bytes that were not modified (up to start of the record)
			baseRevisionContent.readBytes(rv, destIndex, start - prevEnd);
			destIndex += start - prevEnd;
			// insert new data from the patch, if any
			byte[] d = data.get(i);
			System.arraycopy(d, 0, rv, destIndex, d.length);
			destIndex += d.length;
			prevEnd = ends.get(i);
		}
		baseRevisionContent.seek(prevEnd);
		// copy everything in the source past last record's end
		baseRevisionContent.readBytes(rv, destIndex, (int) (baseRevisionContent.length() - prevEnd));
		return rv;
	}
	
	public void clear() {
		starts.clear();
		ends.clear();
		data.clear();
	}
	
	/**
	 * Initialize instance from stream. Any previous patch information (i.e. if instance if reused) is cleared first.
	 * Read up to the end of DataAccess and interpret data as patch records.
	 */
	public void read(DataAccess da) throws IOException {
		clear();
		while (!da.isEmpty()) {
			readOne(da);
		}
	}

	/**
	 * Caller is responsible to ensure stream got some data to read
	 */
	public void readOne(DataAccess da) throws IOException {
		int s = da.readInt();
		int e = da.readInt();
		int len = da.readInt();
		byte[] src = new byte[len];
		da.readBytes(src, 0, len);
		starts.add(s);
		ends.add(e);
		data.add(src);
	}

	private void add(Patch p, int i) {
		add(p.starts.get(i), p.ends.get(i), p.data.get(i));
	}

	private void add(int start, int end, byte[] d) {
		starts.add(start);
		ends.add(end);
		data.add(d);
	}
	
	private static byte[] subarray(byte[] d, int start, int end) {
		byte[] r = new byte[end-start+1];
		System.arraycopy(d, start, r, 0, r.length);
		return r;
	}

	/**
	 * Modify this patch with subsequent patch 
	 */
	private /*SHALL BE PUBLIC ONCE TESTING ENDS*/ Patch apply(Patch another) {
		Patch r = new Patch();
		int p1TotalAppliedDelta = 0; // value to add to start and end indexes of the older patch to get their values as if
		// in the patched text, iow, directly comparable with respective indexes from the newer patch.
		int p1EntryStart = 0, p1EntryEnd = 0, p1EntryLen = 0;
		byte[] p1Data = null;
		boolean insideP1entry = false;
		int p2 = 0, p1 = 0;
		final int p2Max = another.count(), p1Max = this.count();
L0:		for (; p2 < p2Max; p2++) {
			int p2EntryStart = another.starts.get(p2);
			int p2EntryEnd = another.ends.get(p2);
			final int p2EntryRange = p2EntryEnd - p2EntryStart;
			final byte[] p2Data = another.data.get(p2);
			boolean insideP2entry = false;
			int p2EntryStartOffset = -1;
			///
			p1EntryStart = p1EntryEnd = p1EntryLen = 0;
			p1Data = null;
			
L1:			while (p1 < p1Max) {
				if (!insideP1entry) {
					p1EntryStart = starts.get(p1);
					p1EntryEnd = ends.get(p1);
					p1Data = data.get(p1);
					p1EntryLen = p1Data.length;
				}// else keep values

				final int p1EntryDelta = p1EntryLen - (p1EntryEnd - p1EntryStart); // number of actually inserted(+) or deleted(-) chars
				final int p1EntryAppliedStart = p1TotalAppliedDelta + p1EntryStart;
				final int p1EntryAppliedEnd = p1EntryAppliedStart + p1EntryLen; // end of j'th patch entry in the text which is source for p2
				
				if (insideP2entry) {
					if (p2EntryEnd < p1EntryAppliedStart) {
						r.add(p2EntryStart - p2EntryStartOffset, p2EntryEnd - p1TotalAppliedDelta, p2Data);
						insideP2entry = false;
						continue L0; 
					}
					if (p2EntryEnd >= p1EntryAppliedEnd) {
						// when p2EntryEnd == p1EntryAppliedEnd, I assume p1TotalAppliedDelta can't be used for p2EntryEnd to get it to p1 range, but rather shall be
						// augmented with current p1 entry and at the next p1 entry (likely to hit p1EntryAppliedStart > p2EntryEnd above) would do the rest 
						insideP1entry = false;
						p1++;
						p1TotalAppliedDelta += p1EntryDelta;
						continue L1;
					}
					// p1EntryAppliedStart <= p2EntryEnd < p1EntryAppliedEnd
					r.add(p2EntryStart - p2EntryStartOffset, p2EntryEnd - p1TotalAppliedDelta, p2Data);
					p1EntryStart = p2EntryEnd - p1TotalAppliedDelta;
					final int p1DataPartShift = p2EntryEnd - p1EntryAppliedStart + 1;
					if (p1DataPartShift >= p1EntryLen) {
						p1EntryLen = 0;
						p1Data = new byte[0];
					} else {
						p1EntryLen -= p1DataPartShift;
						p1Data = subarray(p1Data, p1DataPartShift, p1Data.length);
					}
					insideP1entry = true;
					insideP2entry = false;
					continue L0;
				}

				if (p1EntryAppliedStart < p2EntryStart) {
					if (p1EntryAppliedEnd <= p2EntryStart) { // p1EntryAppliedEnd in fact index of the first char *after* patch
						// completely independent, copy and continue
						r.add(p1EntryStart, p1EntryEnd, p1Data);
						insideP1entry = false;
						p1++;
						// fall-through to get p1TotalAppliedDelta incremented
					} else { // SKETCH: II or III
						// remember, p1EntryDelta may be negative
						// shall break j'th entry into few 
						// fix p1's end/length
						// p1EntryAppliedStart < p2EntryStart < p1EntryAppliedEnd
						int s = p2EntryStart - p1TotalAppliedDelta; // p2EntryStart in p1 scale. Is within p1 range
						if (s > p1EntryEnd) {
							s = p1EntryEnd;
						}
						int p1DataPartEnd = p2EntryStart - p1EntryAppliedStart; // index, not count. <= (p1EntryEnd-p1EntryStart).
						// add what left from p1
						if (p1DataPartEnd < p1EntryLen) {
							r.add(p1EntryStart, s, subarray(p1Data, 0, p1DataPartEnd)); 
						} else {
							p1DataPartEnd = p1EntryLen-1; // record factual number of p1 bytes we consumed.
							r.add(p1EntryStart, s, p1Data);
						}
						p1TotalAppliedDelta += p1DataPartEnd - (s - p1EntryStart); // (s2 - (s1+delta)) - (s2 - delta - s1) = s2-s1-delta-s2+delta+s1 = 0, unless p1DataPartEnd >= p1Data.length
						p1EntryLen -= (p1DataPartEnd+1); 
						if (p2EntryEnd < p1EntryAppliedEnd) {
							// SKETCH: III
							insideP1entry = true;
							// p2 completely fits into changes of p1
							int e = p2EntryEnd - p1TotalAppliedDelta; // p2EntryEnd in p1 scale
							if (e > p1EntryEnd) {
								// any index past p1 end shall be calculated with respect to p1 end, thus it's unsafe to go past p1 end (there may be more p1 entries there)   
								e = p1EntryEnd;
							}
							r.add(s, e, p2Data); // add p2
							// modify p1 leftover
							p1EntryStart = e;
							if (p2EntryRange >= p1EntryLen) {
								p1EntryLen = 0;
								p1Data = new byte[0];
							} else {
								p1Data = subarray(p1Data, p1DataPartEnd + p2EntryRange, p1Data.length-1 /*up to the last one*/);
								p1EntryLen -= p2EntryRange;
							}
							// p2 is handled, but there are leftovers of p1
							continue L0;
						} else { // p2EntryEnd >= p1EntryAppliedEnd
							// SKETCH: II
							insideP1entry = false;
							p1++;
							if (p1EntryAppliedStart + p1EntryDelta >= p2EntryEnd) {
								// here we know next p1 entry would be past p2 entry and thus can put p2 right away
								r.add(p2EntryStart - p1TotalAppliedDelta, p1EntryEnd, p2Data);
								p1TotalAppliedDelta += p1EntryDelta;
								continue L0;
							} else {
								// there are chances there are more p1 entries till p2 ends
								insideP2entry = true;
								p2EntryStartOffset = p1TotalAppliedDelta;
								// p2EntryEnd is past delta, no chances for p1Data leftovers to be in use
								// p2 processing is not over, need to fix end, depending on what else fits into p2 range (if nothing, can put p2.end right away)
								// fall-through to get p1TotalAppliedDelta incremented;
							}
						}
					}
				} else { // p1EntryAppliedStart >= p2EntryStart
					if (p2EntryEnd < p1EntryAppliedStart) {
						// newer patch completely fits between two older patches 
						r.add(p2EntryStart - p1TotalAppliedDelta, p2EntryEnd - p1TotalAppliedDelta, p2Data);
						// SHALL NOT increment p1TotalAppliedDelta as we didn't use any of p1
						continue L0; // next p2 
					} else { // p2EntryEnd >= p1EntryAppliedStart
						// SKETCH: I or IV
						// p2EntryEnd is either  < p1EntryAppliedEnd or past it
						if (p2EntryEnd <= p1EntryAppliedEnd) {
							// SKETCH: I: copy p2, strip p1 to start from p2EntryEnd, next i (p2)
							insideP1entry = true;
							int e = p2EntryEnd - p1TotalAppliedDelta;
							if (e > p1EntryEnd) {
								e = p1EntryEnd; // added by analogy with above. Is needed?
							}
							r.add(p2EntryStart - p1TotalAppliedDelta, e, p2Data);
							p1EntryStart = e;
							int p1DataShift = p2EntryEnd - p1EntryAppliedStart;
							if (p1DataShift >= p1EntryLen) {
								p1EntryLen = 0;
								p1Data = new byte[0];
							} else {
								p1EntryLen -= p1DataShift;
								p1Data = subarray(p1Data, p1DataShift, p1Data.length - 1);
							}
							// p1TotalAppliedDelta would get incremented once this modified p1 is handled
							continue L0; // next p2;
						} else {
							// p2EntryEnd > p1EntryAppliedEnd
							// SKETCH IV: skip (rest of) p1 completely, continue the same unless  found p1 with start or end past p2EntryEnd.
							insideP1entry = false;
							p1++;
							insideP2entry = true;
							p2EntryStartOffset = p1TotalAppliedDelta;
							// fall-through to get p1TotalAppliedDelta incremented
						}
					}
				}
				p1TotalAppliedDelta += p1EntryDelta;
			} // while (p1 < p1Max)
			{
				// no more p1 entries, shall close p2 (if it's handled, code above jumps directly to L0)
				// regardless of whether insideP2 is .t
				int s = p2EntryStart;
				// p2EntryStartOffset != -1 when we started p2 entry processing, but not completed
				// if we handled last p1 entry but didn't start with p2 entry processing, it's -1 and regular p1 delta shall be used
				s -= p2EntryStartOffset == -1 ? p1TotalAppliedDelta : p2EntryStartOffset;
				r.add(s, p2EntryEnd - p1TotalAppliedDelta, p2Data);
			}
		}
		if (p1 < p1Max && insideP1entry) {
			r.add(p1EntryStart, p1EntryEnd, p1Data);
			p1++;
		}
		while (p1 < p1Max) {
			r.add(this, p1);
			p1++;
		};
		return r;
	}
}