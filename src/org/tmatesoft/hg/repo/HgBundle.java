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
package org.tmatesoft.hg.repo;

import java.io.File;
import java.io.IOException;

import org.tmatesoft.hg.core.HgBadStateException;
import org.tmatesoft.hg.core.HgCallbackTargetException;
import org.tmatesoft.hg.core.HgInvalidFileException;
import org.tmatesoft.hg.core.Nodeid;
import org.tmatesoft.hg.core.SessionContext;
import org.tmatesoft.hg.internal.ByteArrayChannel;
import org.tmatesoft.hg.internal.ByteArrayDataAccess;
import org.tmatesoft.hg.internal.DataAccess;
import org.tmatesoft.hg.internal.DataAccessProvider;
import org.tmatesoft.hg.internal.DigestHelper;
import org.tmatesoft.hg.internal.Experimental;
import org.tmatesoft.hg.internal.InflaterDataAccess;
import org.tmatesoft.hg.internal.Patch;
import org.tmatesoft.hg.repo.HgChangelog.RawChangeset;
import org.tmatesoft.hg.util.CancelledException;

/**
 * @see http://mercurial.selenic.com/wiki/BundleFormat
 * 
 * @author Artem Tikhomirov
 * @author TMate Software Ltd.
 */
public class HgBundle {

	private final File bundleFile;
	private final DataAccessProvider accessProvider;

	// private final SessionContext sessionContext;

	HgBundle(SessionContext ctx, DataAccessProvider dap, File bundle) {
		// sessionContext = ctx;
		accessProvider = dap;
		bundleFile = bundle;
	}

	private DataAccess getDataStream() throws IOException {
		DataAccess da = accessProvider.create(bundleFile);
		byte[] signature = new byte[6];
		if (da.length() > 6) {
			da.readBytes(signature, 0, 6);
			if (signature[0] == 'H' && signature[1] == 'G'
					&& signature[2] == '1' && signature[3] == '0') {
				if (signature[4] == 'G' && signature[5] == 'Z') {
					return new InflaterDataAccess(da, 6, da.length() - 6);
				}
				if (signature[4] == 'B' && signature[5] == 'Z') {
					throw HgRepository.notImplemented();
				}
				if (signature[4] != 'U' || signature[5] != 'N') {
					throw new HgBadStateException("Bad bundle signature:"
							+ new String(signature));
				}
				// "...UN", fall-through
			} else {
				da.reset();
			}
		}
		return da;
	}

	private int uses = 0;

	public HgBundle link() {
		uses++;
		return this;
	}

	public void unlink() {
		uses--;
		if (uses == 0 && bundleFile != null) {
			bundleFile.deleteOnExit();
		}
	}

	public boolean inUse() {
		return uses > 0;
	}

	/**
	 * Get changes recorded in the bundle that are missing from the supplied
	 * repository.
	 * 
	 * @param hgRepo
	 *            repository that shall possess base revision for this bundle
	 * @param inspector
	 *            callback to get each changeset found
	 */
	public void changes(final HgRepository hgRepo,
			final HgChangelog.Inspector inspector)
			throws HgCallbackTargetException, HgInvalidFileException {
		Inspector bundleInsp = new Inspector() {
			DigestHelper dh = new DigestHelper();
			boolean emptyChangelog = true;
			private DataAccess prevRevContent;
			private int revisionIndex;

			public void changelogStart() {
				emptyChangelog = true;
				revisionIndex = 0;
			}

			public void changelogEnd() {
				if (emptyChangelog) {
					throw new IllegalStateException(
							"No changelog group in the bundle"); // XXX perhaps,
																	// just be
																	// silent
																	// and/or
																	// log?
				}
			}

			/*
			 * Despite that BundleFormat wiki says: "Each Changelog entry
			 * patches the result of all previous patches (the previous, or
			 * parent patch of a given patch p is the patch that has a node
			 * equal to p's p1 field)", it seems not to hold true. Instead, each
			 * entry patches previous one, regardless of whether the one before
			 * is its parent (i.e. ge.firstParent()) or not.
			 * 
			 * Actual state in the changelog.i Index Offset Flags Packed Actual
			 * Base Rev Link Rev Parent1 Parent2 nodeid 50: 9212 0 209 329 48 50
			 * 49 -1 f1db8610da62a3e0beb8d360556ee1fd6eb9885e 51: 9421 0 278 688
			 * 48 51 50 -1 9429c7bd1920fab164a9d2b621d38d57bcb49ae0 52: 9699 0
			 * 154 179 52 52 50 -1 30bd389788464287cee22ccff54c330a4b715de5 53:
			 * 9853 0 133 204 52 53 51 52
			 * a6f39e595b2b54f56304470269a936ead77f5725 54: 9986 0 156 182 54 54
			 * 52 -1 fd4f2c98995beb051070630c272a9be87bef617d
			 * 
			 * Excerpt from bundle (nodeid, p1, p2, cs):
			 * f1db8610da62a3e0beb8d360556ee1fd6eb9885e
			 * 26e3eeaa39623de552b45ee1f55c14f36460f220
			 * 0000000000000000000000000000000000000000
			 * f1db8610da62a3e0beb8d360556ee1fd6eb9885e; patches:4
			 * 9429c7bd1920fab164a9d2b621d38d57bcb49ae0
			 * f1db8610da62a3e0beb8d360556ee1fd6eb9885e
			 * 0000000000000000000000000000000000000000
			 * 9429c7bd1920fab164a9d2b621d38d57bcb49ae0; patches:3 >
			 * 30bd389788464287cee22ccff54c330a4b715de5
			 * f1db8610da62a3e0beb8d360556ee1fd6eb9885e
			 * 0000000000000000000000000000000000000000
			 * 30bd389788464287cee22ccff54c330a4b715de5; patches:3
			 * a6f39e595b2b54f56304470269a936ead77f5725
			 * 9429c7bd1920fab164a9d2b621d38d57bcb49ae0
			 * 30bd389788464287cee22ccff54c330a4b715de5
			 * a6f39e595b2b54f56304470269a936ead77f5725; patches:3
			 * fd4f2c98995beb051070630c272a9be87bef617d
			 * 30bd389788464287cee22ccff54c330a4b715de5
			 * 0000000000000000000000000000000000000000
			 * fd4f2c98995beb051070630c272a9be87bef617d; patches:3
			 * 
			 * To recreate 30bd..e5, one have to take content of 9429..e0, not
			 * its p1 f1db..5e
			 */
			public boolean element(GroupElement ge) {
				emptyChangelog = false;
				HgChangelog changelog = hgRepo.getChangelog();
				try {
					if (prevRevContent == null) {
						if (ge.firstParent().isNull()
								&& ge.secondParent().isNull()) {
							prevRevContent = new ByteArrayDataAccess(
									new byte[0]);
						} else {
							final Nodeid base = ge.firstParent();
							if (!changelog.isKnown(base) /*
														 * only first parent,
														 * that's Bundle
														 * contract
														 */) {
								throw new IllegalStateException(
										String.format(
												"Revision %s needs a parent %s, which is missing in the supplied repo %s",
												ge.node().shortNotation(),
												base.shortNotation(),
												hgRepo.toString()));
							}
							ByteArrayChannel bac = new ByteArrayChannel();
							changelog.rawContent(base, bac); // FIXME get
																// DataAccess
																// directly, to
																// avoid
							// extra byte[] (inside ByteArrayChannel)
							// duplication just for the sake of subsequent
							// ByteArrayDataChannel wrap.
							prevRevContent = new ByteArrayDataAccess(
									bac.toArray());
						}
					}
					//
					byte[] csetContent = ge.apply(prevRevContent);
					dh = dh.sha1(ge.firstParent(), ge.secondParent(),
							csetContent); // XXX ge may give me access to byte[]
											// content of nodeid directly,
											// perhaps, I don't need DH to be
											// friend of Nodeid?
					if (!ge.node().equalsTo(dh.asBinary())) {
						throw new IllegalStateException(
								"Integrity check failed on " + bundleFile
										+ ", node:" + ge.node());
					}
					ByteArrayDataAccess csetDataAccess = new ByteArrayDataAccess(
							csetContent);
					RawChangeset cs = RawChangeset.parse(csetDataAccess);
					inspector.next(revisionIndex++, ge.node(), cs);
					prevRevContent.done();
					prevRevContent = csetDataAccess.reset();
				} catch (CancelledException ex) {
					return false;
				} catch (Exception ex) {
					throw new HgBadStateException(ex); // FIXME EXCEPTIONS
				}
				return true;
			}

			public void manifestStart() {
			}

			public void manifestEnd() {
			}

			public void fileStart(String name) {
			}

			public void fileEnd(String name) {
			}

		};
		try {
			inspectChangelog(bundleInsp);
		} catch (RuntimeException ex) {
			throw new HgCallbackTargetException(ex);
		}
	}

	// callback to minimize amount of Strings and Nodeids instantiated
	public interface Inspector {
		void changelogStart();

		void changelogEnd();

		void manifestStart();

		void manifestEnd();

		void fileStart(String name);

		void fileEnd(String name);

		/**
		 * XXX desperately need exceptions here
		 * 
		 * @param element
		 *            data element, instance might be reused, don't keep a
		 *            reference to it or its raw data
		 * @return <code>true</code> to continue
		 */
		boolean element(GroupElement element);
	}

	public void inspectChangelog(Inspector inspector)
			throws HgInvalidFileException {
		if (inspector == null) {
			throw new IllegalArgumentException();
		}
		DataAccess da = null;
		try {
			da = getDataStream();
			internalInspectChangelog(da, inspector);
		} catch (IOException ex) {
			throw new HgInvalidFileException("Bundle.inspectChangelog failed",
					ex, bundleFile);
		} finally {
			if (da != null) {
				da.done();
			}
		}
	}

	public void inspectManifest(Inspector inspector)
			throws HgInvalidFileException {
		if (inspector == null) {
			throw new IllegalArgumentException();
		}
		DataAccess da = null;
		try {
			da = getDataStream();
			if (da.isEmpty()) {
				return;
			}
			skipGroup(da); // changelog
			internalInspectManifest(da, inspector);
		} catch (IOException ex) {
			throw new HgInvalidFileException("Bundle.inspectManifest failed",
					ex, bundleFile);
		} finally {
			if (da != null) {
				da.done();
			}
		}
	}

	public void inspectFiles(Inspector inspector) throws HgInvalidFileException {
		if (inspector == null) {
			throw new IllegalArgumentException();
		}
		DataAccess da = null;
		try {
			da = getDataStream();
			if (da.isEmpty()) {
				return;
			}
			skipGroup(da); // changelog
			if (da.isEmpty()) {
				return;
			}
			skipGroup(da); // manifest
			internalInspectFiles(da, inspector);
		} catch (IOException ex) {
			throw new HgInvalidFileException("Bundle.inspectFiles failed", ex,
					bundleFile);
		} finally {
			if (da != null) {
				da.done();
			}
		}
	}

	public void inspectAll(Inspector inspector) throws HgInvalidFileException {
		if (inspector == null) {
			throw new IllegalArgumentException();
		}
		DataAccess da = null;
		try {

			da = getDataStream();
			internalInspectChangelog(da, inspector);
			internalInspectManifest(da, inspector);
			internalInspectFiles(da, inspector);

		} catch (IOException ex) {
			throw new HgInvalidFileException("Bundle.inspectAll failed", ex,
					bundleFile);
		} finally {
			if (da != null) {
				da.done();
			}
		}
	}

	private void internalInspectChangelog(DataAccess da, Inspector inspector)
			throws IOException {
		if (da.isEmpty()) {
			return;
		}
		inspector.changelogStart();
		readGroup(da, inspector);
		inspector.changelogEnd();
	}

	private void internalInspectManifest(DataAccess da, Inspector inspector)
			throws IOException {
		if (da.isEmpty()) {
			return;
		}
		inspector.manifestStart();
		readGroup(da, inspector);
		inspector.manifestEnd();
	}

	private void internalInspectFiles(DataAccess da, Inspector inspector)
			throws IOException {
		while (!da.isEmpty()) {
			int fnameLen = da.readInt();
			if (fnameLen <= 4) {
				break; // null chunk, the last one.
			}
			byte[] fnameBuf = new byte[fnameLen - 4];
			da.readBytes(fnameBuf, 0, fnameBuf.length);
			String name = new String(fnameBuf);
			System.out.println(name);
			inspector.fileStart(name);
			readGroup(da, inspector);
			inspector.fileEnd(name);
			
			
		}
	}

	

	private static void readGroup(DataAccess da, Inspector inspector)
			throws IOException {
		int len = da.readInt();
		boolean good2go = true;
		while (len > 4 && !da.isEmpty() && good2go) {
			byte[] nb = new byte[80];
			da.readBytes(nb, 0, 80);
			int dataLength = len - 84 /* length field + 4 nodeids */;
			byte[] data = new byte[dataLength];
			da.readBytes(data, 0, dataLength);
			DataAccess slice = new ByteArrayDataAccess(data); // XXX in fact,
																// may pass a
																// slicing
																// DataAccess.
			// Just need to make sure that we seek to proper location afterwards
			// (where next GroupElement starts),
			// regardless whether that slice has read it or not.
			GroupElement ge = new GroupElement(nb, slice);
			good2go = inspector.element(ge);
			slice.done(); // BADA doesn't implement done(), but it could (e.g.
							// free array)
			// / and we'd better tell it we are not going to use it any more.
			// However, it's important to ensure Inspector
			// implementations out there do not retain GroupElement.rawData()
			len = da.isEmpty() ? 0 : da.readInt();
		}
		// need to skip up to group end if inspector told he don't want to
		// continue with the group,
		// because outer code may try to read next group immediately as we
		// return back.
		while (len > 4 && !da.isEmpty()) {
			da.skip(len - 4 /* length field */);
			len = da.isEmpty() ? 0 : da.readInt();
		}
	}

	private static void skipGroup(DataAccess da) throws IOException {
		int len = da.readInt();
		while (len > 4 && !da.isEmpty()) {
			da.skip(len - 4); // sizeof(int)
			len = da.isEmpty() ? 0 : da.readInt();
		}
	}

	@Experimental(reason = "Cumbersome API, rawData and apply with byte[] perhaps need replacement with ByteChannel/ByteBuffer, and better Exceptions. Perhaps, shall split into interface and impl")
	public static class GroupElement {
		private final byte[] header; // byte[80] takes 120 bytes, 4 Nodeids -
										// 192
		private final DataAccess dataAccess;
		private Patch patches;

		GroupElement(byte[] fourNodeids, DataAccess rawDataAccess) {
			assert fourNodeids != null && fourNodeids.length == 80;
			header = fourNodeids;
			dataAccess = rawDataAccess;
		}

		/**
		 * <b>node</b> field of the group element
		 * 
		 * @return node revision, never <code>null</code>
		 */
		public Nodeid node() {
			return Nodeid.fromBinary(header, 0);
		}

		/**
		 * <b>p1</b> <i>(parent 1)</i> field of the group element
		 * 
		 * @return revision of parent 1, never <code>null</code>
		 */
		public Nodeid firstParent() {
			return Nodeid.fromBinary(header, 20);
		}

		/**
		 * <b>p2</b> <i>(parent 2)</i> field of the group element
		 * 
		 * @return revision of parent 2, never <code>null</code>
		 */
		public Nodeid secondParent() {
			return Nodeid.fromBinary(header, 40);
		}

		/**
		 * <b>cs</b> <i>(changeset link)</i> field of the group element
		 * 
		 * @return changeset revision, never <code>null</code>
		 */
		public Nodeid cset() {
			return Nodeid.fromBinary(header, 60);
		}

		public byte[] rawDataByteArray() throws IOException { // XXX IOException
																// or
																// HgInvalidFileException?
			return rawData().byteArray();
		}

		public byte[] apply(byte[] baseContent) throws IOException {
			return apply(new ByteArrayDataAccess(baseContent));
		}

		/* package-local */DataAccess rawData() {
			return dataAccess;
		}

		/* package-local */Patch patch() throws IOException {
			if (patches == null) {
				dataAccess.reset();
				patches = new Patch();
				patches.read(dataAccess);
			}
			return patches;
		}

		/* package-local */byte[] apply(DataAccess baseContent)
				throws IOException {
			return patch().apply(baseContent, -1);
		}

		public String toString() {
			int patchCount;
			try {
				patchCount = patch().count();
			} catch (IOException ex) {
				ex.printStackTrace();
				patchCount = -1;
			}
			return String.format("%s %s %s %s; patches:%d\n", node()
					.shortNotation(), firstParent().shortNotation(),
					secondParent().shortNotation(), cset().shortNotation(),
					patchCount);
		}
	}
}
