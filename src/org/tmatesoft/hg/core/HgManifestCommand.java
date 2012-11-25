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

import static org.tmatesoft.hg.repo.HgRepository.*;
import static org.tmatesoft.hg.repo.HgRepository.BAD_REVISION;
import static org.tmatesoft.hg.repo.HgRepository.TIP;

import java.util.ConcurrentModificationException;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;

import org.tmatesoft.hg.repo.HgManifest;
import org.tmatesoft.hg.repo.HgRepository;
import org.tmatesoft.hg.repo.HgManifest.Flags;
import org.tmatesoft.hg.util.Path;
import org.tmatesoft.hg.util.PathPool;
import org.tmatesoft.hg.util.PathRewrite;


/**
 * Gives access to list of files in each revision (Mercurial manifest information), 'hg manifest' counterpart.
 *  
 * @author Artem Tikhomirov
 * @author TMate Software Ltd.
 */
public class HgManifestCommand extends HgAbstractCommand<HgManifestCommand> {
	
	private final HgRepository repo;
	private Path.Matcher matcher;
	private int startRev = 0, endRev = TIP;
	private Handler visitor;
	private boolean needDirs = false;
	
	private final Mediator mediator = new Mediator();

	public HgManifestCommand(HgRepository hgRepo) {
		repo = hgRepo;
	}

	/**
	 * Parameterize command to visit revisions <code>[rev1..rev2]</code>.
	 * @param rev1 - revision local index to start from. Non-negative. May be {@link HgRepository#TIP} (rev2 argument shall be {@link HgRepository#TIP} as well, then) 
	 * @param rev2 - revision local index to end with, inclusive. Non-negative, greater or equal to rev1. May be {@link HgRepository#TIP}.
	 * @return <code>this</code> for convenience.
	 * @throws IllegalArgumentException if revision arguments are incorrect (see above).
	 */
	public HgManifestCommand range(int rev1, int rev2) {
		// XXX if manifest range is different from that of changelog, need conversion utils (external?)
		boolean badArgs = rev1 == BAD_REVISION || rev2 == BAD_REVISION || rev1 == WORKING_COPY || rev2 == WORKING_COPY;
		badArgs |= rev2 != TIP && rev2 < rev1; // range(3, 1);
		badArgs |= rev1 == TIP && rev2 != TIP; // range(TIP, 2), although this may be legitimate when TIP points to 2
		if (badArgs) {
			throw new IllegalArgumentException(String.format("Bad range: [%d, %d]", rev1, rev2));
		}
		startRev = rev1;
		endRev = rev2;
		return this;
	}
	
	public HgManifestCommand revision(int rev) {
		startRev = endRev = rev;
		return this;
	}
	
	public HgManifestCommand dirs(boolean include) {
		// XXX whether directories with directories only are include or not
		// now lists only directories with files
		needDirs = include;
		return this;
	}
	
	/**
	 * Limit manifest walk to a subset of files. 
	 * @param pathMatcher - filter, pass <code>null</code> to clear.
	 * @return <code>this</code> instance for convenience
	 */
	public HgManifestCommand match(Path.Matcher pathMatcher) {
		matcher = pathMatcher;
		return this;
	}
	
	/**
	 * Runs the command.
	 * @param handler - callback to get the outcome
	 * @throws IllegalArgumentException if handler is <code>null</code>
	 * @throws ConcurrentModificationException if this command is already in use (running)
	 */
	public void execute(Handler handler) throws HgException {
		if (handler == null) {
			throw new IllegalArgumentException();
		}
		if (visitor != null) {
			throw new ConcurrentModificationException();
		}
		try {
			visitor = handler;
			mediator.start();
			repo.getManifest().walk(startRev, endRev, mediator);
		} finally {
			mediator.done();
			visitor = null;
		}
	}

	/**
	 * Callback to walk file/directory tree of a revision
	 */
	public interface Handler {
		void begin(Nodeid manifestRevision);
		void dir(Path p); // optionally invoked (if walker was configured to spit out directories) prior to any files from this dir and subdirs
		void file(HgFileRevision fileRevision); // XXX allow to check p is invalid (df.exists())
		void end(Nodeid manifestRevision);
	}

	// I'd rather let HgManifestCommand implement HgManifest.Inspector directly, but this pollutes API alot
	private class Mediator implements HgManifest.Inspector2 {
		// file names are likely to repeat in each revision, hence caching of Paths.
		// However, once HgManifest.Inspector switches to Path objects, perhaps global Path pool
		// might be more effective?
		private PathPool pathPool;
		private List<HgFileRevision> manifestContent;
		private Nodeid manifestNodeid;
		
		public void start() {
			// Manifest keeps normalized paths
			pathPool = new PathPool(new PathRewrite.Empty());
		}
		
		public void done() {
			manifestContent = null;
			pathPool = null;
		}
	
		public boolean begin(int manifestRevision, Nodeid nid, int changelogRevision) {
			if (needDirs && manifestContent == null) {
				manifestContent = new LinkedList<HgFileRevision>();
			}
			visitor.begin(manifestNodeid = nid);
			return true;
		}
		public boolean end(int revision) {
			if (needDirs) {
				LinkedHashMap<Path, LinkedList<HgFileRevision>> breakDown = new LinkedHashMap<Path, LinkedList<HgFileRevision>>();
				for (HgFileRevision fr : manifestContent) {
					Path filePath = fr.getPath();
					Path dirPath = pathPool.parent(filePath);
					LinkedList<HgFileRevision> revs = breakDown.get(dirPath);
					if (revs == null) {
						revs = new LinkedList<HgFileRevision>();
						breakDown.put(dirPath, revs);
					}
					revs.addLast(fr);
				}
				for (Path dir : breakDown.keySet()) {
					visitor.dir(dir);
					for (HgFileRevision fr : breakDown.get(dir)) {
						visitor.file(fr);
					}
				}
				manifestContent.clear();
			}
			visitor.end(manifestNodeid);
			manifestNodeid = null;
			return true;
		}
		public boolean next(Nodeid nid, String fname, String flags) {
			throw new HgBadStateException(HgManifest.Inspector2.class.getName());
		}
		
		public boolean next(Nodeid nid, Path fname, Flags flags) {
			if (matcher != null && !matcher.accept(fname)) {
				return true;
			}
			HgFileRevision fr = new HgFileRevision(repo, nid, fname);
			if (needDirs) {
				manifestContent.add(fr);
			} else {
				visitor.file(fr);
			}
			return true;
		}
	}
}
