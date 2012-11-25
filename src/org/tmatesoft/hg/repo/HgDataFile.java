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
package org.tmatesoft.hg.repo;

import static org.tmatesoft.hg.repo.HgInternals.wrongRevisionIndex;
import static org.tmatesoft.hg.repo.HgRepository.*;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.tmatesoft.hg.core.HgDataStreamException;
import org.tmatesoft.hg.core.HgException;
import org.tmatesoft.hg.core.HgInvalidControlFileException;
import org.tmatesoft.hg.core.HgInvalidRevisionException;
import org.tmatesoft.hg.core.HgLogCommand;
import org.tmatesoft.hg.core.Nodeid;
import org.tmatesoft.hg.internal.DataAccess;
import org.tmatesoft.hg.internal.FilterByteChannel;
import org.tmatesoft.hg.internal.FilterDataAccess;
import org.tmatesoft.hg.internal.IntMap;
import org.tmatesoft.hg.internal.RevlogStream;
import org.tmatesoft.hg.util.ByteChannel;
import org.tmatesoft.hg.util.CancelSupport;
import org.tmatesoft.hg.util.CancelledException;
import org.tmatesoft.hg.util.LogFacility;
import org.tmatesoft.hg.util.Pair;
import org.tmatesoft.hg.util.Path;
import org.tmatesoft.hg.util.ProgressSupport;



/**
 * ? name:HgFileNode?
 *
 * @author Artem Tikhomirov
 * @author TMate Software Ltd.
 */
public class HgDataFile extends Revlog {

	// absolute from repo root?
	// slashes, unix-style?
	// repo location agnostic, just to give info to user, not to access real storage
	private final Path path;
	private Metadata metadata; // get initialized on first access to file content.
	
	/*package-local*/HgDataFile(HgRepository hgRepo, Path filePath, RevlogStream content) {
		super(hgRepo, content);
		path = filePath;
	}

	/*package-local*/HgDataFile(HgRepository hgRepo, Path filePath) {
		super(hgRepo);
		path = filePath;
	}

	// exists is not the best name possible. now it means no file with such name was ever known to the repo.
	// it might be confused with files existed before but lately removed. 
	public boolean exists() {
		return content != null; // XXX need better impl
	}

	// human-readable (i.e. "COPYING", not "store/data/_c_o_p_y_i_n_g.i")
	public Path getPath() {
		return path; // hgRepo.backresolve(this) -> name? In this case, what about hashed long names?
	}

	/**
	 * Handy shorthand for {@link #length(int) length(getRevisionIndex(nodeid))}
	 *
	 * @param nodeid revision of the file
	 * 
	 * @return size of the file content at the given revision
	 * @throws HgInvalidRevisionException if supplied nodeid doesn't identify any revision from this revlog  
	 * @throws HgDataStreamException if attempt to access file metadata failed
	 * @throws HgInvalidControlFileException if access to revlog index/data entry failed
	 */
	public int length(Nodeid nodeid) throws HgDataStreamException, HgInvalidControlFileException, HgInvalidRevisionException {
		return length(getRevisionIndex(nodeid));
	}
	
	/**
 	 * @param fileRevisionIndex - revision local index, non-negative. From predefined constants, only {@link HgRepository#TIP} makes sense. 
	 * @return size of the file content at the revision identified by local revision number.
	 * @throws HgInvalidRevisionException if supplied argument doesn't represent revision index in this revlog
	 * @throws HgDataStreamException if attempt to access file metadata failed
	 * @throws HgInvalidControlFileException if access to revlog index/data entry failed
	 */
	public int length(int fileRevisionIndex) throws HgDataStreamException, HgInvalidControlFileException, HgInvalidRevisionException {
		// TODO support WORKING_COPY constant
		if (metadata == null || !metadata.checked(fileRevisionIndex)) {
			checkAndRecordMetadata(fileRevisionIndex);
		}
		final int dataLen = content.dataLength(fileRevisionIndex);
		if (metadata.known(fileRevisionIndex)) {
			return dataLen - metadata.dataOffset(fileRevisionIndex);
		}
		return dataLen;
	}

	/**
	 * Reads content of the file from working directory. If file present in the working directory, its actual content without
	 * any filters is supplied through the sink. If file does not exist in the working dir, this method provides content of a file 
	 * as if it would be refreshed in the working copy, i.e. its corresponding revision 
	 * (XXX according to dirstate? file tip?) is read from the repository, and filters repo -> working copy get applied.
	 *     
	 * @param sink where to pipe content to
	 * @throws HgDataStreamException to indicate troubles reading repository file
	 * @throws CancelledException if execution of the operation was cancelled
	 */
	public void workingCopy(ByteChannel sink) throws HgDataStreamException, HgInvalidControlFileException, CancelledException {
		File f = getRepo().getFile(this);
		if (f.exists()) {
			final CancelSupport cs = CancelSupport.Factory.get(sink);
			final ProgressSupport progress = ProgressSupport.Factory.get(sink);
			final long flength = f.length();
			final int bsize = (int) Math.min(flength, 32*1024);
			progress.start((int) (flength > Integer.MAX_VALUE ? flength >>> 15 /*32 kb buf size*/ : flength));
			ByteBuffer buf = ByteBuffer.allocate(bsize);
			FileChannel fc = null;
			try {
				fc = new FileInputStream(f).getChannel();
				while (fc.read(buf) != -1) {
					cs.checkCancelled();
					buf.flip();
					int consumed = sink.write(buf);
					progress.worked(flength > Integer.MAX_VALUE ? 1 : consumed);
					buf.compact();
				}
			} catch (IOException ex) {
				throw new HgDataStreamException(getPath(), ex);
			} finally {
				progress.done();
				if (fc != null) {
					try {
						fc.close();
					} catch (IOException ex) {
						getRepo().getContext().getLog().info(getClass(), ex, null);
					}
				}
			}
		} else {
			final Pair<Nodeid, Nodeid> wcParents = getRepo().getWorkingCopyParents();
			Nodeid p = wcParents.first().isNull() ? wcParents.second() : wcParents.first();
			if (p.isNull()) {
				// no dirstate parents - no content 
				return;
			}
			final HgChangelog clog = getRepo().getChangelog();
			// common case to avoid searching complete changelog for nodeid match
			final Nodeid tipRev = clog.getRevision(TIP);
			final int csetRevIndex;
			if (tipRev.equals(p)) {
				csetRevIndex = clog.getLastRevision();
			} else {
				// bad luck, need to search honestly
				csetRevIndex = getRepo().getChangelog().getRevisionIndex(p);
			}
			Nodeid fileRev = getRepo().getManifest().getFileRevision(csetRevIndex, getPath());
			final int fileRevIndex = getRevisionIndex(fileRev);
			contentWithFilters(fileRevIndex, sink);
		}
	}
	
//	public void content(int revision, ByteChannel sink, boolean applyFilters) throws HgDataStreamException, IOException, CancelledException {
//		byte[] content = content(revision);
//		final CancelSupport cancelSupport = CancelSupport.Factory.get(sink);
//		final ProgressSupport progressSupport = ProgressSupport.Factory.get(sink);
//		ByteBuffer buf = ByteBuffer.allocate(512);
//		int left = content.length;
//		progressSupport.start(left);
//		int offset = 0;
//		cancelSupport.checkCancelled();
//		ByteChannel _sink = applyFilters ? new FilterByteChannel(sink, getRepo().getFiltersFromRepoToWorkingDir(getPath())) : sink;
//		do {
//			buf.put(content, offset, Math.min(left, buf.remaining()));
//			buf.flip();
//			cancelSupport.checkCancelled();
//			// XXX I may not rely on returned number of bytes but track change in buf position instead.
//			int consumed = _sink.write(buf);
//			buf.compact();
//			offset += consumed;
//			left -= consumed;
//			progressSupport.worked(consumed);
//		} while (left > 0);
//		progressSupport.done(); // XXX shall specify whether #done() is invoked always or only if completed successfully.
//	}
	
	/*XXX not sure distinct method contentWithFilters() is the best way to do, perhaps, callers shall add filters themselves?*/
	public void contentWithFilters(int revision, ByteChannel sink) throws HgDataStreamException, HgInvalidControlFileException, CancelledException, HgInvalidRevisionException {
		if (revision == WORKING_COPY) {
			workingCopy(sink); // pass un-mangled sink
		} else {
			content(revision, new FilterByteChannel(sink, getRepo().getFiltersFromRepoToWorkingDir(getPath())));
		}
	}

	/**
	 * 
 	 * @param fileRevisionIndex - revision local index, non-negative. From predefined constants, {@link HgRepository#TIP} and {@link HgRepository#WORKING_COPY} make sense. 
	 * @param sink
	 * @throws HgDataStreamException FIXME EXCEPTIONS
	 * @throws HgInvalidControlFileException if access to revlog index/data entry failed
	 * @throws CancelledException if execution of the operation was cancelled
	 * @throws HgInvalidRevisionException if supplied argument doesn't represent revision index in this revlog
	 */
	public void content(int fileRevisionIndex, ByteChannel sink) throws HgDataStreamException, HgInvalidControlFileException, CancelledException, HgInvalidRevisionException {
		// for data files need to check heading of the file content for possible metadata
		// @see http://mercurial.selenic.com/wiki/FileFormats#data.2BAC8-
		if (fileRevisionIndex == TIP) {
			fileRevisionIndex = getLastRevision();
		}
		if (fileRevisionIndex == WORKING_COPY) {
			// sink is supposed to come into workingCopy without filters
			// thus we shall not get here (into #content) from #contentWithFilters(WC)
			workingCopy(sink);
			return;
		}
		if (wrongRevisionIndex(fileRevisionIndex) || fileRevisionIndex == BAD_REVISION) {
			throw new HgInvalidRevisionException(fileRevisionIndex);
		}
		if (sink == null) {
			throw new IllegalArgumentException();
		}
		if (metadata == null) {
			metadata = new Metadata();
		}
		ErrorHandlingInspector insp;
		final LogFacility lf = getRepo().getContext().getLog();
		if (metadata.none(fileRevisionIndex)) {
			insp = new ContentPipe(sink, 0, lf);
		} else if (metadata.known(fileRevisionIndex)) {
			insp = new ContentPipe(sink, metadata.dataOffset(fileRevisionIndex), lf);
		} else {
			// do not know if there's metadata
			insp = new MetadataInspector(metadata, lf, getPath(), new ContentPipe(sink, 0, lf));
		}
		insp.checkCancelled();
		super.content.iterate(fileRevisionIndex, fileRevisionIndex, true, insp);
		try {
			insp.checkFailed(); // XXX is there real need to throw IOException from ContentPipe?
		} catch (HgDataStreamException ex) {
			throw ex;
		} catch (IOException ex) {
			throw new HgDataStreamException(getPath(), ex).setRevisionIndex(fileRevisionIndex);
		} catch (HgException ex) {
			// shall not happen, unless we changed ContentPipe or its subclass
			throw new HgDataStreamException(getPath(), ex.getClass().getName(), ex);
		}
	}
	
	private static class HistoryNode {
		int changeset;
		Nodeid cset;
		HistoryNode parent1, parent2;
		List<HistoryNode> children;

		HistoryNode(int cs, HistoryNode p1, HistoryNode p2) {
			changeset = cs;
			parent1 = p1;
			parent2 = p2;
			if (p1 != null) {
				p1.addChild(this);
			}
			if (p2 != null) {
				p2.addChild(this);
			}
		}
		
		Nodeid changesetRevision() {
			assert cset != null : "we initialize all csets prior to use";
			return cset;
		}

		void addChild(HistoryNode child) {
			if (children == null) {
				children = new ArrayList<HistoryNode>(2);
			}
			children.add(child);
		}
	}
	
	/**
	 * @deprecated use {@link HgLogCommand#execute(org.tmatesoft.hg.core.HgChangesetTreeHandler)} instead
	 */
	@Deprecated
	public void history(HgChangelog.TreeInspector inspector) throws HgInvalidControlFileException{
		final CancelSupport cancelSupport = CancelSupport.Factory.get(inspector);
		try {
			final boolean[] needsSorting = { false };
			final HistoryNode[] completeHistory = new HistoryNode[getRevisionCount()];
			final int[] commitRevisions = new int[completeHistory.length];
			RevlogStream.Inspector insp = new RevlogStream.Inspector() {
				public void next(int revisionNumber, int actualLen, int baseRevision, int linkRevision, int parent1Revision, int parent2Revision, byte[] nodeid, DataAccess data) {
					if (revisionNumber > 0) {
						if (commitRevisions[revisionNumber-1] > linkRevision) {
							needsSorting[0] = true;
						}
					}
					commitRevisions[revisionNumber] = linkRevision;
					HistoryNode p1 = null, p2 = null;
					if (parent1Revision != -1) {
						p1 = completeHistory[parent1Revision];
					}
					if (parent2Revision != -1) {
						p2 = completeHistory[parent2Revision];
					}
					completeHistory[revisionNumber] = new HistoryNode(linkRevision, p1, p2);
				}
			};
			content.iterate(0, getLastRevision(), false, insp);
			cancelSupport.checkCancelled();
			if (needsSorting[0]) {
				Arrays.sort(commitRevisions);
			}
			// read changeset revisions at once (to avoid numerous changelog.getRevision reads)
			// but just nodeids, not RawChangeset (changelog.iterate(data=false)
			ArrayList<Nodeid> changesetRevisions = new ArrayList<Nodeid>(commitRevisions.length);
			getRepo().getChangelog().getRevisionsInternal(changesetRevisions, commitRevisions);
			cancelSupport.checkCancelled();
			// assign them to corresponding HistoryNodes
			for (int i = 0; i < completeHistory.length; i++ ) {
				final HistoryNode n = completeHistory[i];
				if (needsSorting[0]) {
					int x = Arrays.binarySearch(commitRevisions, n.changeset);
					assert x >= 0;
					n.cset = changesetRevisions.get(x);
				} else {
					// commit revisions were not sorted, may use original index directly
					n.cset = changesetRevisions.get(i);
				}
			}
			cancelSupport.checkCancelled();
			// XXX shall sort completeHistory according to changeset numbers?
			for (int i = 0; i < completeHistory.length; i++ ) {
				final HistoryNode n = completeHistory[i];
				HistoryNode p;
				Nodeid p1, p2;
				if ((p = n.parent1) != null) {
					p1 = p.changesetRevision();
				} else {
					p1 = Nodeid.NULL;
				}
				if ((p= n.parent2) != null) {
					p2 = p.changesetRevision();
				} else {
					p2 = Nodeid.NULL;
				}
				final Pair<Nodeid, Nodeid> parentChangesets = new Pair<Nodeid, Nodeid>(p1, p2);
				final List<Nodeid> childChangesets;
				if (n.children == null) {
					childChangesets = Collections.emptyList();
				} else {
					Nodeid[] revisions = new Nodeid[n.children.size()];
					int j = 0;
					for (HistoryNode hn : n.children) {
						revisions[j++] = hn.changesetRevision();
					}
					childChangesets = Arrays.asList(revisions);
				}
				inspector.next(n.changesetRevision(), parentChangesets, childChangesets);
				cancelSupport.checkCancelled();
			}
		} catch (CancelledException ex) {
			return;
		}
	}
	
	public void history(HgChangelog.Inspector inspector) throws HgInvalidControlFileException {
		history(0, getLastRevision(), inspector);
	}

	public void history(int start, int end, HgChangelog.Inspector inspector) throws HgInvalidRevisionException, HgInvalidControlFileException {
		if (!exists()) {
			throw new IllegalStateException("Can't get history of invalid repository file node"); 
		}
		final int last = getLastRevision();
		if (end == TIP) {
			end = last;
		}
		if (start == TIP) {
			start = last;
		}
		HgInternals.checkRevlogRange(start, end, last);

		final int[] commitRevisions = new int[end - start + 1];
		final boolean[] needsSorting = { false };
		RevlogStream.Inspector insp = new RevlogStream.Inspector() {
			int count = 0;
			public void next(int revisionNumber, int actualLen, int baseRevision, int linkRevision, int parent1Revision, int parent2Revision, byte[] nodeid, DataAccess data) {
				if (count > 0) {
					if (commitRevisions[count -1] > linkRevision) {
						needsSorting[0] = true;
					}
				}
				commitRevisions[count++] = linkRevision;
			}
		};
		content.iterate(start, end, false, insp);
		final HgChangelog changelog = getRepo().getChangelog();
		if (needsSorting[0]) {
			// automatic tools (svnmerge?) produce unnatural file history
			// (e.g. cpython/Lib/doctest.py, revision 164 points to cset 63509, 165 - to 38453) 
			Arrays.sort(commitRevisions);
		}
		changelog.rangeInternal(inspector, commitRevisions);
	}
	
	/**
	 * For a given revision of the file (identified with revision index), find out index of the corresponding changeset.
	 *
	 * @return changeset revision index
	 * @throws HgInvalidRevisionException if supplied argument doesn't represent revision index in this revlog
	 * @throws HgInvalidControlFileException if access to revlog index/data entry failed
	 */
	public int getChangesetRevisionIndex(int revision) throws HgInvalidControlFileException, HgInvalidRevisionException {
		return content.linkRevision(revision);
	}
	/**
	 * @deprecated use {@link #getChangesetRevisionIndex(int)} instead
	 */
	@Deprecated
	public int getChangesetLocalRevision(int revision) throws HgInvalidControlFileException, HgInvalidRevisionException {
		return getChangesetRevisionIndex(revision);
	}

	/**
	 * Complements {@link #getChangesetRevisionIndex(int)} to get changeset revision that corresponds to supplied file revision
	 * 
	 * @param nid revision of the file
	 * @return changeset revision
	 * @throws HgInvalidRevisionException if supplied argument doesn't represent revision index in this revlog
	 * @throws HgInvalidControlFileException if access to revlog index/data entry failed
	 */
	public Nodeid getChangesetRevision(Nodeid nid) throws HgInvalidControlFileException, HgInvalidRevisionException {
		int changelogRevision = getChangesetRevisionIndex(getRevisionIndex(nid));
		return getRepo().getChangelog().getRevision(changelogRevision);
	}

	/**
	 * 
	 * @return
	 * @throws HgDataStreamException if attempt to access file metadata failed
	 */
	public boolean isCopy() throws HgDataStreamException {
		if (metadata == null || !metadata.checked(0)) {
			checkAndRecordMetadata(0);
		}
		if (!metadata.known(0)) {
			return false;
		}
		return metadata.find(0, "copy") != null;
	}

	/**
	 * Get name of the file this one was copied from.
	 * 
	 * @return name of the file origin
	 * @throws HgDataStreamException if attempt to access file metadata failed
	 * @throws UnsupportedOperationException if this file doesn't represent a copy ({@link #isCopy()} was false)
	 */
	public Path getCopySourceName() throws HgDataStreamException {
		if (isCopy()) {
			return Path.create(metadata.find(0, "copy"));
		}
		throw new UnsupportedOperationException(); // XXX REVISIT, think over if Exception is good (clients would check isCopy() anyway, perhaps null is sufficient?)
	}
	
	public Nodeid getCopySourceRevision() throws HgDataStreamException {
		if (isCopy()) {
			return Nodeid.fromAscii(metadata.find(0, "copyrev")); // XXX reuse/cache Nodeid
		}
		throw new UnsupportedOperationException();
	}
	
	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder(getClass().getSimpleName());
		sb.append('(');
		sb.append(getPath());
		sb.append(')');
		return sb.toString();
	}
	
	private void checkAndRecordMetadata(int localRev) throws HgDataStreamException {
		// content() always initializes metadata.
		// FIXME this is expensive way to find out metadata, distinct RevlogStream.Iterator would be better.
		// Alternatively, may parameterize MetadataContentPipe to do prepare only.
		// For reference, when throwing CancelledException, hg status -A --rev 3:80 takes 70 ms
		// however, if we just consume buffer instead (buffer.position(buffer.limit()), same command takes ~320ms
		// (compared to command-line counterpart of 190ms)
		try {
			content(localRev, new ByteChannel() { // No-op channel
				public int write(ByteBuffer buffer) throws IOException, CancelledException {
					throw new CancelledException();
				}
			});
		} catch (CancelledException ex) {
			// it's ok, we did that
		} catch (HgInvalidControlFileException ex) {
			throw new HgDataStreamException(getPath(), ex);
		}
	}

	private static final class MetadataEntry {
		private final String entry;
		private final int valueStart;

		// key may be null
		/*package-local*/MetadataEntry(String key, String value) {
			if (key == null) {
				entry = value;
				valueStart = -1; // not 0 to tell between key == null and key == ""
			} else {
				entry = key + value;
				valueStart = key.length();
			}
		}
		/*package-local*/boolean matchKey(String key) {
			return key == null ? valueStart == -1 : key.length() == valueStart && entry.startsWith(key);
		}
//		uncomment once/if needed
//		public String key() {
//			return entry.substring(0, valueStart);
//		}
		public String value() {
			return valueStart == -1 ? entry : entry.substring(valueStart);
		}
	}

	private static class Metadata {
		private static class Record {
			public final int offset;
			public final MetadataEntry[] entries;
			
			public Record(int off, MetadataEntry[] entr) {
				offset = off;
				entries = entr;
			}
		}
		// XXX sparse array needed
		private final IntMap<Record> entries = new IntMap<Record>(5);
		
		private final Record NONE = new Record(-1, null); // don't want statics

		// true when there's metadata for given revision
		boolean known(int revision) {
			Record i = entries.get(revision);
			return i != null && NONE != i;
		}

		// true when revision has been checked for metadata presence.
		public boolean checked(int revision) {
			return entries.containsKey(revision);
		}

		// true when revision has been checked and found not having any metadata
		boolean none(int revision) {
			Record i = entries.get(revision);
			return i == NONE;
		}

		// mark revision as having no metadata.
		void recordNone(int revision) {
			Record i = entries.get(revision);
			if (i == NONE) {
				return; // already there
			} 
			if (i != null) {
				throw new IllegalStateException(String.format("Trying to override Metadata state for revision %d (known offset: %d)", revision, i));
			}
			entries.put(revision, NONE);
		}

		// since this is internal class, callers are supposed to ensure arg correctness (i.e. ask known() before)
		int dataOffset(int revision) {
			return entries.get(revision).offset;
		}
		void add(int revision, int dataOffset, Collection<MetadataEntry> e) {
			assert !entries.containsKey(revision);
			entries.put(revision, new Record(dataOffset, e.toArray(new MetadataEntry[e.size()])));
		}

		String find(int revision, String key) {
			for (MetadataEntry me : entries.get(revision).entries) {
				if (me.matchKey(key)) {
					return me.value();
				}
			}
			return null;
		}
	}

	private static class MetadataInspector extends ErrorHandlingInspector implements RevlogStream.Inspector {
		private final Metadata metadata;
		private final RevlogStream.Inspector delegate;
		private final Path fname; // need these only for error reporting
		private final LogFacility log;

		public MetadataInspector(Metadata _metadata, LogFacility logFacility, Path file, RevlogStream.Inspector chain) {
			metadata = _metadata;
			fname = file;
			log = logFacility;
			delegate = chain;
			setCancelSupport(CancelSupport.Factory.get(chain));
		}

		public void next(int revisionNumber, int actualLen, int baseRevision, int linkRevision, int parent1Revision, int parent2Revision, byte[] nodeid, DataAccess data) throws HgException {
			try {
				final int daLength = data.length();
				if (daLength < 4 || data.readByte() != 1 || data.readByte() != 10) {
					metadata.recordNone(revisionNumber);
					data.reset();
				} else {
					ArrayList<MetadataEntry> _metadata = new ArrayList<MetadataEntry>();
					int offset = parseMetadata(data, daLength, _metadata);
					metadata.add(revisionNumber, offset, _metadata);
					// da is in prepared state (i.e. we consumed all bytes up to metadata end).
					// However, it's not safe to assume delegate won't call da.reset() for some reason,
					// and we need to ensure predictable result.
					data.reset();
					data = new FilterDataAccess(data, offset, daLength - offset);
				}
				if (delegate != null) {
					delegate.next(revisionNumber, actualLen, baseRevision, linkRevision, parent1Revision, parent2Revision, nodeid, data);
				}
			} catch (IOException ex) {
				recordFailure(ex);
			} catch (HgDataStreamException ex) {
				recordFailure(ex.setRevisionIndex(revisionNumber));
			}
		}

		private int parseMetadata(DataAccess data, final int daLength, ArrayList<MetadataEntry> _metadata) throws IOException, HgDataStreamException {
			int lastEntryStart = 2;
			int lastColon = -1;
			// XXX in fact, need smth like ByteArrayBuilder, similar to StringBuilder,
			// which can't be used here because we can't convert bytes to chars as we read them
			// (there might be multi-byte encoding), and we need to collect all bytes before converting to string 
			ByteArrayOutputStream bos = new ByteArrayOutputStream();
			String key = null, value = null;
			boolean byteOne = false;
			boolean metadataIsComplete = false;
			for (int i = 2; i < daLength; i++) {
				byte b = data.readByte();
				if (b == '\n') {
					if (byteOne) { // i.e. \n follows 1
						lastEntryStart = i+1;
						metadataIsComplete = true;
						// XXX is it possible to have here incomplete key/value (i.e. if last pair didn't end with \n)
						// if yes, need to set metadataIsComplete to true in that case as well
						break;
					}
					if (key == null || lastColon == -1 || i <= lastColon) {
						log.error(getClass(), "Missing key in file revision metadata at index %d", i);
					}
					value = new String(bos.toByteArray()).trim();
					bos.reset();
					_metadata.add(new MetadataEntry(key, value));
					key = value = null;
					lastColon = -1;
					lastEntryStart = i+1;
					continue;
				} 
				// byteOne has to be consumed up to this line, if not yet, consume it
				if (byteOne) {
					// insert 1 we've read on previous step into the byte builder
					bos.write(1);
					byteOne = false;
					// fall-through to consume current byte
				}
				if (b == (int) ':') {
					assert value == null;
					key = new String(bos.toByteArray());
					bos.reset();
					lastColon = i;
				} else if (b == 1) {
					byteOne = true;
				} else {
					bos.write(b);
				}
			}
			// data.isEmpty is not reliable, renamed files of size==0 keep only metadata
			if (!metadataIsComplete) {
				// XXX perhaps, worth a testcase (empty file, renamed, read or ask ifCopy
				throw new HgDataStreamException(fname, "Metadata is not closed properly", null);
			}
			return lastEntryStart;
		}

		@Override
		public void checkFailed() throws HgException, IOException, CancelledException {
			super.checkFailed();
			if (delegate instanceof ErrorHandlingInspector) {
				// XXX need to add ErrorDestination and pass it around (much like CancelSupport get passed)
				// so that delegate would be able report its failures directly to caller without this hack
				((ErrorHandlingInspector) delegate).checkFailed();
			}
		}
	}
}
