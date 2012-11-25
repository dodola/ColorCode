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

import static org.tmatesoft.hg.repo.HgInternals.wrongRevisionIndex;
import static org.tmatesoft.hg.repo.HgRepository.BAD_REVISION;
import static org.tmatesoft.hg.repo.HgRepository.TIP;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.ByteBuffer;

import org.tmatesoft.hg.repo.HgDataFile;
import org.tmatesoft.hg.repo.HgRepository;
import org.tmatesoft.hg.util.Adaptable;
import org.tmatesoft.hg.util.ByteChannel;
import org.tmatesoft.hg.util.CancelSupport;
import org.tmatesoft.hg.util.CancelledException;
import org.tmatesoft.hg.util.Path;

/**
 * Command to obtain content of a file, 'hg cat' counterpart. 
 * 
 * @author Artem Tikhomirov
 * @author TMate Software Ltd.
 */
public class HgCatCommand extends HgAbstractCommand<HgCatCommand> {

	private final HgRepository repo;
	private Path file;
	private int revisionIndex = TIP;
	private Nodeid revision;
	private Nodeid cset;

	public HgCatCommand(HgRepository hgRepo) {
		repo = hgRepo;
	}

	/**
	 * File to read, required parameter 
	 * @param fname path to a repository file, can't be <code>null</code>
	 * @return <code>this</code> for convenience
	 * @throws IllegalArgumentException if supplied fname is null or points to directory
	 */
	public HgCatCommand file(Path fname) {
		if (fname == null || fname.isDirectory()) {
			throw new IllegalArgumentException(String.valueOf(fname));
		}
		file = fname;
		return this;
	}

	/**
	 * Select specific revision of the file to cat with revision local index. Note, revision numbering is of particular file, not that of
	 * repository (i.e. revision 0 means initial content of the file, irrespective of changeset revision at the time of commit) 
	 * 
	 * Invocation of this method clears revision set with {@link #revision(Nodeid)} or {@link #revision(int)} earlier.
	 * 
	 * @param fileRevisionIndex - revision local index, non-negative, or one of predefined constants. Note, use of {@link HgRepository#BAD_REVISION}, 
	 * although possible, makes little sense (command would fail if executed).  
 	 * @return <code>this</code> for convenience
	 */
	public HgCatCommand revision(int fileRevisionIndex) {
		if (wrongRevisionIndex(fileRevisionIndex)) {
			throw new IllegalArgumentException(String.valueOf(fileRevisionIndex));
		}
		revisionIndex = fileRevisionIndex;
		revision = null;
		cset = null;
		return this;
	}
	
	/**
	 * Select file revision to read. Note, this revision is file revision (i.e. the one from manifest), not the changeset revision.
	 *  
	 * Invocation of this method clears revision set with {@link #revision(int)} or {@link #revision(Nodeid)} earlier.
	 * 
	 * @param nodeid - unique file revision identifier, Note, use of <code>null</code> or {@link Nodeid#NULL} is senseless
	 * @return <code>this</code> for convenience
	 */
	public HgCatCommand revision(Nodeid nodeid) {
		if (nodeid != null && nodeid.isNull()) {
			nodeid = null;
		}
		revision = nodeid;
		revisionIndex = BAD_REVISION;
		cset = null;
		return this;
	}

	/**
	 * Parameterize the command from file revision object.
	 * 
	 * @param fileRev file revision to cat 
	 * @return <code>this</code> for convenience
	 */
	public HgCatCommand revision(HgFileRevision fileRev) {
		return file(fileRev.getPath()).revision(fileRev.getRevision());
	}
	
	/**
	 * Select whatever revision of the file that was actual at the time of the specified changeset. Unlike {@link #revision(int)} or {@link #revision(Nodeid)}, this method 
	 * operates in terms of repository global revisions (aka changesets). 
	 * 
	 * Invocation of this method clears selection of a file revision with its index.
	 * 
	 * @param nodeid changeset revision
	 * @return <code>this</code> for convenience
	 */
	public HgCatCommand changeset(Nodeid nodeid) {
		revisionIndex = BAD_REVISION;
		revision = null;
		cset = nodeid;
		return this;
	}

	/**
	 * Runs the command with current set of parameters and pipes data to provided sink.
	 * 
	 * @param sink output channel to write data to.
	 * @throws HgDataStreamException 
	 * @throws IllegalArgumentException when command arguments are incomplete or wrong
	 */
	public void execute(ByteChannel sink) throws HgDataStreamException, HgInvalidControlFileException, CancelledException {
		if (revisionIndex == BAD_REVISION && revision == null && cset == null) {
			throw new IllegalArgumentException("File revision, corresponing local number, or a changset nodeid shall be specified");
		}
		if (file == null) {
			throw new IllegalArgumentException("Name of the file is missing");
		}
		if (sink == null) {
			throw new IllegalArgumentException("Need an output channel");
		}
		HgDataFile dataFile = repo.getFileNode(file);
		if (!dataFile.exists()) {
			throw new HgDataStreamException(file, new FileNotFoundException(file.toString()));
		}
		int revToExtract;
		if (cset != null) {
			int csetRev = repo.getChangelog().getRevisionIndex(cset);
			Nodeid toExtract = null;
			do {
				toExtract = repo.getManifest().getFileRevision(csetRev, file);
				if (toExtract == null) {
					if (dataFile.isCopy()) {
						file = dataFile.getCopySourceName();
						dataFile = repo.getFileNode(file);
					} else {
						break;
					}
				}
			} while (toExtract == null);
			if (toExtract == null) {
				throw new HgBadStateException(String.format("File %s nor its origins were not known at repository %s revision", file, cset.shortNotation()));
			}
			revToExtract = dataFile.getRevisionIndex(toExtract);
		} else if (revision != null) {
			revToExtract = dataFile.getRevisionIndex(revision);
		} else {
			revToExtract = revisionIndex;
		}
		ByteChannel sinkWrap;
		if (getCancelSupport(null, false) == null) {
			// no command-specific cancel helper, no need for extra proxy
			// sink itself still may supply CS
			sinkWrap = sink;
		} else {
			// try CS from sink, if any. at least there is CS from command 
			CancelSupport cancelHelper = getCancelSupport(sink, true);
			cancelHelper.checkCancelled();
			sinkWrap = new ByteChannelProxy(sink, cancelHelper);
		}
		dataFile.contentWithFilters(revToExtract, sinkWrap);
	}

	private static class ByteChannelProxy implements ByteChannel, Adaptable {
		private final ByteChannel delegate;
		private final CancelSupport cancelHelper;

		public ByteChannelProxy(ByteChannel _delegate, CancelSupport cs) {
			assert _delegate != null;
			delegate = _delegate;
			cancelHelper = cs;
		}
		public int write(ByteBuffer buffer) throws IOException, CancelledException {
			return delegate.write(buffer);
		}

		public <T> T getAdapter(Class<T> adapterClass) {
			if (CancelSupport.class == adapterClass) {
				return adapterClass.cast(cancelHelper);
			}
			return Adaptable.Factory.getAdapter(delegate, adapterClass, null);
		}
	}
}
