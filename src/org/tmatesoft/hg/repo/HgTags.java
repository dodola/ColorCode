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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.tmatesoft.hg.core.HgInvalidControlFileException;
import org.tmatesoft.hg.core.Nodeid;

/**
 * @see http://mercurial.selenic.com/wiki/TagDesign
 *
 * @author Artem Tikhomirov
 * @author TMate Software Ltd.
 */
public class HgTags {
	// global tags come from ".hgtags"
	// local come from ".hg/localtags"

	private final HgRepository repo;

	private final Map<Nodeid, List<String>> globalToName;
	private final Map<Nodeid, List<String>> localToName;
	private final Map<String, List<Nodeid>> globalFromName;
	private final Map<String, List<Nodeid>> localFromName;
	
	private Map<String, TagInfo> tags;
	
	/*package-local*/ HgTags(HgRepository hgRepo) {
		repo = hgRepo;
		globalToName =  new HashMap<Nodeid, List<String>>();
		localToName  =  new HashMap<Nodeid, List<String>>();
		globalFromName = new TreeMap<String, List<Nodeid>>();
		localFromName  = new TreeMap<String, List<Nodeid>>();
	}
	
	/*package-local*/ void readLocal(File localTags) throws IOException {
		if (localTags == null || localTags.isDirectory()) {
			throw new IllegalArgumentException(String.valueOf(localTags));
		}
		read(localTags, localToName, localFromName);
	}
	
	/*package-local*/ void readGlobal(File globalTags) throws IOException {
		if (globalTags == null || globalTags.isDirectory()) {
			throw new IllegalArgumentException(String.valueOf(globalTags));
		}
		read(globalTags, globalToName, globalFromName);
	}

	/*package-local*/ void readGlobal(Reader globalTags) throws IOException {
		BufferedReader r = null;
		try {
			r = new BufferedReader(globalTags);
			read(r, globalToName, globalFromName);
		} finally {
			if (r != null) {
				r.close();
			}
		}
	}
	
	private void read(File f, Map<Nodeid,List<String>> nid2name, Map<String, List<Nodeid>> name2nid) throws IOException {
		if (!f.canRead()) {
			return;
		}
		BufferedReader r = null;
		try {
			r = new BufferedReader(new FileReader(f));
			read(r, nid2name, name2nid);
		} finally {
			if (r != null) {
				r.close();
			}
		}
	}
	
	private void read(BufferedReader reader, Map<Nodeid,List<String>> nid2name, Map<String, List<Nodeid>> name2nid) throws IOException {
		String line;
		while ((line = reader.readLine()) != null) {
			line = line.trim();
			if (line.length() == 0) {
				continue;
			}
			if (line.length() < 40+2 /*nodeid, space and at least single-char tagname*/) {
				repo.getContext().getLog().warn(getClass(), "Bad tags line: %s", line); 
				continue;
			}
			int spacePos = line.indexOf(' ');
			if (spacePos != -1) {
				assert spacePos == 40;
				final byte[] nodeidBytes = line.substring(0, spacePos).getBytes();
				Nodeid nid = Nodeid.fromAscii(nodeidBytes, 0, nodeidBytes.length);
				String tagName = line.substring(spacePos+1);
				List<Nodeid> nids = name2nid.get(tagName);
				if (nids == null) {
					nids = new LinkedList<Nodeid>();
					nids.add(nid);
					// tagName is substring of full line, thus need a copy to let the line be GC'ed
					// new String(tagName.toCharArray()) is more expressive, but results in 1 extra arraycopy
					tagName = new String(tagName);
					name2nid.put(tagName, nids);
				} else if (!nid.equals(nids.get(0))) {
					// Alternatively, !nids.contains(nid) might have come to mind.
					// However, I guess that 'tag history' means we need to record each change of revision
					// associated with the tag, i.e. imagine project evolution:
					// tag1=r1, tag1=r2, tag1=r1. If we choose !contains, list top of tag1 would point to r2
					// while we need it to point to r1.
					// In fact, there are still possible odd patterns in name2nid list, e.g.
					// when tag was removed and added back(initially rev1 tag1, on removal *added* nullrev tag1), 
					// then added back (rev2 tag1).
					// name2nid would list (rev2 nullrev rev1) as many times, as there were revisions of the .hgtags file
					// See cpython "v2.4.3c1" revision for example.
					// It doesn't seem to hurt (unless there are clients that care about tag history and depend on
					// unique revisions there), XXX but better to be fixed (not sure how, though) 
					((LinkedList<Nodeid>) nids).addFirst(nid);
					// XXX repo.getNodeidCache().nodeid(nid);
				}
				List<String> revTags = nid2name.get(nid);
				if (revTags == null) {
					revTags = new LinkedList<String>();
					revTags.add(tagName);
					nid2name.put(nid, revTags);
				} else if (!revTags.contains(tagName)) {
					// !contains because we don't care about order of the tags per revision
					revTags.add(tagName);
				}
				
			} else {
				repo.getContext().getLog().warn(getClass(), "Bad tags line: %s", line);
			}
		}
	}

	public List<String> tags(Nodeid nid) {
		ArrayList<String> rv = new ArrayList<String>(5);
		List<String> l;
		if ((l = localToName.get(nid)) != null) {
			rv.addAll(l);
		}
		if ((l = globalToName.get(nid)) != null) {
			rv.addAll(l);
		}
		return rv;
	}

	public boolean isTagged(Nodeid nid) {
		return localToName.containsKey(nid) || globalToName.containsKey(nid);
	}

	public List<Nodeid> tagged(String tagName) {
		ArrayList<Nodeid> rv = new ArrayList<Nodeid>(5);
		List<Nodeid> l;
		if ((l = localFromName.get(tagName)) != null) {
			rv.addAll(l);
		}
		if ((l = globalFromName.get(tagName)) != null) {
			rv.addAll(l);
		}
		return rv;
	}
	
	/**
	 * @deprecated use {@link #getAllTags()} instead
	 */
	@Deprecated
	public Map<String, TagInfo> getTags() {
		return getAllTags();
	}

	/**
	 * All tag entries from the repository, for both active and removed tags
	 */
	public Map<String, TagInfo> getAllTags() {
		if (tags == null) {
			tags = new TreeMap<String, TagInfo>();
			for (String t : globalFromName.keySet()) {
				tags.put(t, new TagInfo(t));
			}
			for (String t : localFromName.keySet()) {
				tags.put(t, new TagInfo(t));
			}
			tags = Collections.unmodifiableMap(tags);
		}
		return tags;
	}
	
	/**
	 * Tags that are in use in the repository, unlike {@link #getAllTags()} doesn't list removed tags. 
	 */
	public Map<String, TagInfo> getActiveTags() {
		TreeMap<String, TagInfo> rv = new TreeMap<String, TagInfo>();
		for (Map.Entry<String, TagInfo> e : getAllTags().entrySet()) {
			if (!e.getValue().isRemoved()) {
				rv.put(e.getKey(), e.getValue());
			}
		}
		return rv;
	}

	
	public final class TagInfo {
		private final String name;
		private String branch;

		TagInfo(String tagName) {
			name = tagName;
		}
		public String name() {
			return name;
		}

		public boolean isLocal() {
			return localFromName.containsKey(name);
		}

		public String branch() throws HgInvalidControlFileException {
			if (branch == null) {
				int x = repo.getChangelog().getRevisionIndex(revision());
				branch = repo.getChangelog().range(x, x).get(0).branch();
			}
			return branch;
		}
		public Nodeid revision() {
			if (isLocal()) {
				return localFromName.get(name).get(0);
			}
			return globalFromName.get(name).get(0);
		}

		/**
		 * @return <code>true</code> if this tag entry describes tag removal
		 */
		public boolean isRemoved() {
			return revision().isNull();
		}
	}
}
