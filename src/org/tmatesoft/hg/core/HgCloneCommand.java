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
package org.tmatesoft.hg.core;

import static org.tmatesoft.hg.core.Nodeid.NULL;
import static org.tmatesoft.hg.internal.RequiresFile.*;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.TreeMap;
import java.util.zip.DeflaterOutputStream;

import org.tmatesoft.hg.internal.BasicSessionContext;
import org.tmatesoft.hg.internal.ByteArrayDataAccess;
import org.tmatesoft.hg.internal.DataAccess;
import org.tmatesoft.hg.internal.DigestHelper;
import org.tmatesoft.hg.internal.Internals;
import org.tmatesoft.hg.repo.HgBundle;
import org.tmatesoft.hg.repo.HgBundle.GroupElement;
import org.tmatesoft.hg.repo.HgDataFile;
import org.tmatesoft.hg.repo.HgLookup;
import org.tmatesoft.hg.repo.HgRemoteRepository;
import org.tmatesoft.hg.repo.HgRepository;
import org.tmatesoft.hg.util.ByteChannel;
import org.tmatesoft.hg.util.CancelledException;
import org.tmatesoft.hg.util.PathRewrite;

/**
 * WORK IN PROGRESS, DO NOT USE
 * 
 * @author Artem Tikhomirov
 * @author TMate Software Ltd.
 */
public class HgCloneCommand {

	private File destination;
	private HgRemoteRepository srcRepo;
	private static ArrayList<String> files = new ArrayList<String>();

	public HgCloneCommand() {

	}

	/**
	 * @param folder
	 *            location to become root of the repository (i.e. where
	 *            <em>.hg</em> folder would reside). Either shall not exist or
	 *            be empty otherwise.
	 * @return <code>this</code> for convenience
	 */
	public HgCloneCommand destination(File folder) {
		destination = folder;

		return this;
	}

	public HgCloneCommand source(HgRemoteRepository hgRemote) {
		srcRepo = hgRemote;
		return this;
	}

	public HgRepository execute() throws HgBadArgumentException,
			HgRemoteConnectionException, HgInvalidFileException,
			CancelledException {
		files = new ArrayList<String>();
		if (destination == null) {
			throw new IllegalArgumentException("Destination not set", null);
		}
		if (srcRepo == null || srcRepo.isInvalid()) {
			throw new HgBadArgumentException("Bad source repository", null);
		}
		if (destination.exists()) {
			if (!destination.isDirectory()) {
				throw new HgBadArgumentException(String.format(
						"%s is not a directory", destination), null);
			} else if (destination.list().length > 0) {
				throw new HgBadArgumentException(String.format(
						"% shall be empty", destination), null);
			}
		} else {
			destination.mkdirs();
		}
		// if cloning remote repo, which can stream and no revision is specified
		// -
		// can use 'stream_out' wireproto
		//
		// pull all changes from the very beginning
		// XXX consult getContext() if by any chance has a bundle ready, if not,
		// then read and register
		HgBundle completeChanges = srcRepo.getChanges(Collections
				.singletonList(NULL));
		WriteDownMate mate = new WriteDownMate(destination);
		try {
			// instantiate new repo in the destdir
			mate.initEmptyRepository();
			// pull changes
			completeChanges.inspectAll(mate);
			mate.complete();
		} catch (IOException ex) {
			throw new HgInvalidFileException(getClass().getName(), ex);
		} finally {
			completeChanges.unlink();
		}
		return new HgLookup().detect(destination);
	}

	public ArrayList<String> GetFiles() {
		return files;
	}

	// 1. process changelog, memorize nodeids to index
	// 2. process manifest, using map from step 3, collect manifest nodeids
	// 3. process every file, using map from 3, and consult set from step 4 to
	// ensure repo is correct
	private static class WriteDownMate implements HgBundle.Inspector {
		private final File hgDir;
		private final PathRewrite storagePathHelper;
		private FileOutputStream indexFile;
		private String filename; // human-readable name of the file being
									// written, for log/exception purposes

		private final TreeMap<Nodeid, Integer> changelogIndexes = new TreeMap<Nodeid, Integer>();
		private boolean collectChangelogIndexes = false;

		private int base = -1;
		private long offset = 0;
		private DataAccess prevRevContent;
		private final DigestHelper dh = new DigestHelper();
		private final ArrayList<Nodeid> revisionSequence = new ArrayList<Nodeid>(); // last
																					// visited
																					// nodes
																					// first

		private final LinkedList<String> fncacheFiles = new LinkedList<String>();
		private Internals implHelper;

		public WriteDownMate(File destDir) {
			hgDir = new File(destDir, ".hg");
			implHelper = new Internals(new BasicSessionContext(null, null));
			implHelper.setStorageConfig(1, STORE | FNCACHE | DOTENCODE);
			storagePathHelper = implHelper.buildDataFilesHelper();

		}

		public void initEmptyRepository() throws IOException {
			implHelper.initEmptyRepository(hgDir);
		}

		public void complete() throws IOException {
			FileOutputStream fncacheFile = new FileOutputStream(new File(hgDir,
					"store/fncache"));
			for (String s : fncacheFiles) {
				fncacheFile.write(s.getBytes());
				fncacheFile.write(0x0A); // http://mercurial.selenic.com/wiki/fncacheRepoFormat
			}
			fncacheFile.close();
		}

		public void changelogStart() {
			try {
				base = -1;
				offset = 0;
				revisionSequence.clear();
				indexFile = new FileOutputStream(new File(hgDir,
						filename = "store/00changelog.i"));
				collectChangelogIndexes = true;
			} catch (IOException ex) {
				throw new HgBadStateException(ex);
			}
		}

		public void changelogEnd() {
			try {
				if (prevRevContent != null) {
					prevRevContent.done();
					prevRevContent = null;
				}
				collectChangelogIndexes = false;
				indexFile.close();
				indexFile = null;
				filename = null;
			} catch (IOException ex) {
				throw new HgBadStateException(ex);
			}
		}

		public void manifestStart() {
			try {
				base = -1;
				offset = 0;
				revisionSequence.clear();
				indexFile = new FileOutputStream(new File(hgDir,
						filename = "store/00manifest.i"));
			} catch (IOException ex) {
				throw new HgBadStateException(ex);
			}
		}

		public void manifestEnd() {
			try {
				if (prevRevContent != null) {
					prevRevContent.done();
					prevRevContent = null;
				}
				indexFile.close();
				indexFile = null;
				filename = null;
			} catch (IOException ex) {
				throw new HgBadStateException(ex);
			}
		}

		public void fileStart(String name) {
			try {
				base = -1;
				offset = 0;
				revisionSequence.clear();
				fncacheFiles.add("data/" + name + ".i"); // FIXME this is pure
															// guess,
				// need to investigate more how filenames are kept in fncache
				files.add(name);
				File file = new File(hgDir, filename = storagePathHelper
						.rewrite(name).toString());
				file.getParentFile().mkdirs();
				indexFile = new FileOutputStream(file);
			} catch (IOException ex) {
				throw new HgBadStateException(ex);
			}
		}

		public void fileEnd(String name) {
			try {
				if (prevRevContent != null) {
					prevRevContent.done();
					prevRevContent = null;
				}
				indexFile.close();
				indexFile = null;
				filename = null;
			} catch (IOException ex) {
				throw new HgBadStateException(ex);
			}
		}

		private int knownRevision(Nodeid p) {
			if (p.isNull()) {
				return -1;
			} else {
				for (int i = revisionSequence.size() - 1; i >= 0; i--) {
					if (revisionSequence.get(i).equals(p)) {
						return i;
					}
				}
			}
			throw new HgBadStateException(String.format(
					"Can't find index of %s for file %s", p.shortNotation(),
					filename));
		}

		public boolean element(GroupElement ge) {
			try {
				assert indexFile != null;
				boolean writeComplete = false;
				Nodeid p1 = ge.firstParent();
				Nodeid p2 = ge.secondParent();
				if (p1.isNull() && p2.isNull() /*
												 * or forced flag, does
												 * REVIDX_PUNCHED_FLAG indicate
												 * that?
												 */) {
					prevRevContent = new ByteArrayDataAccess(new byte[0]);
					writeComplete = true;
				}
				byte[] content = ge.apply(prevRevContent.byteArray());
				byte[] calculated = dh.sha1(p1, p2, content).asBinary();
				final Nodeid node = ge.node();
				if (!node.equalsTo(calculated)) {
					throw new HgBadStateException(
							String.format(
									"Checksum failed: expected %s, calculated %s. File %s",
									node, calculated, filename));
				}
				final int link;
				if (collectChangelogIndexes) {
					changelogIndexes.put(node, revisionSequence.size());
					link = revisionSequence.size();
				} else {
					Integer csRev = changelogIndexes.get(ge.cset());
					if (csRev == null) {
						throw new HgBadStateException(String.format(
								"Changelog doesn't contain revision %s of %s",
								ge.cset().shortNotation(), filename));
					}
					link = csRev.intValue();
				}
				final int p1Rev = knownRevision(p1), p2Rev = knownRevision(p2);
				byte[] patchContent = ge.rawDataByteArray();
				writeComplete = writeComplete
						|| patchContent.length >= (/* 3/4 of actual */content.length - (content.length >>> 2));
				if (writeComplete) {
					base = revisionSequence.size();
				}
				final byte[] sourceData = writeComplete ? content
						: patchContent;
				final byte[] data;
				ByteArrayOutputStream bos = new ByteArrayOutputStream(
						content.length);
				DeflaterOutputStream dos = new DeflaterOutputStream(bos);
				dos.write(sourceData);
				dos.close();
				final byte[] compressedData = bos.toByteArray();
				dos = null;
				bos = null;
				final Byte dataPrefix;
				if (compressedData.length >= (sourceData.length - (sourceData.length >>> 2))) {
					// compression wasn't too effective,
					data = sourceData;
					dataPrefix = 'u';
				} else {
					data = compressedData;
					dataPrefix = null;
				}

				ByteBuffer header = ByteBuffer
						.allocate(64 /* REVLOGV1_RECORD_SIZE */);
				if (offset == 0) {
					final int INLINEDATA = 1 << 16;
					header.putInt(1 /* RevlogNG */| INLINEDATA);
					header.putInt(0);
				} else {
					header.putLong(offset << 16);
				}
				final int compressedLen = data.length
						+ (dataPrefix == null ? 0 : 1);
				header.putInt(compressedLen);
				header.putInt(content.length);
				header.putInt(base);
				header.putInt(link);
				header.putInt(p1Rev);
				header.putInt(p2Rev);
				header.put(node.toByteArray());
				// assume 12 bytes left are zeros
				indexFile.write(header.array());
				if (dataPrefix != null) {
					indexFile.write(dataPrefix.byteValue());
				}
				indexFile.write(data);
				//
				offset += compressedLen;
				revisionSequence.add(node);
				prevRevContent.done();
				prevRevContent = new ByteArrayDataAccess(content);
			} catch (IOException ex) {
				throw new HgBadStateException(ex);
			}
			return true;
		}

	}

}
