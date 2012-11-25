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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.tmatesoft.hg.core.HgInvalidControlFileException;
import org.tmatesoft.hg.repo.HgRepository;
import org.tmatesoft.hg.repo.HgSubrepoLocation;

/**
 * 
 * @author Artem Tikhomirov
 * @author TMate Software Ltd.
 */
public class SubrepoManager /* XXX RepoChangeNotifier, RepoChangeListener */{

	private final HgRepository repo;
	private List<HgSubrepoLocation> subRepos;

	public SubrepoManager(HgRepository hgRepo) {
		assert hgRepo != null;
		repo = hgRepo;
	}

	private List<HgSubrepoLocation> readActualState() throws HgInvalidControlFileException {
		File hgsubFile = new File(repo.getWorkingDir(), ".hgsub");
		if (!hgsubFile.canRead()) {
			return Collections.emptyList();
		}
		Map<String, String> state; // path -> revision
		File hgstateFile = null;
		try {
			hgstateFile = new File(repo.getWorkingDir(), ".hgsubstate");
			if (hgstateFile.canRead()) {
				state = readState(new BufferedReader(new FileReader(hgstateFile)));
			} else {
				state = Collections.emptyMap();
			}
		} catch (IOException ex) {
			throw new HgInvalidControlFileException("Subrepo state read failed", ex, hgstateFile);
		}
		try {
			BufferedReader br = new BufferedReader(new FileReader(hgsubFile));
			return readConfig(br, state);
		} catch (IOException ex) {
			throw new HgInvalidControlFileException("Subrepo state read failed", ex, hgsubFile);
		}
	}

	private List<HgSubrepoLocation> readConfig(BufferedReader br, Map<String, String> substate) throws IOException {
		try {
			String line;
			LinkedList<HgSubrepoLocation> res = new LinkedList<HgSubrepoLocation>();
			while ((line = br.readLine()) != null) {
				int sep = line.indexOf('=');
				if (sep == -1) {
					continue;
				}
				// since both key and value are referenced from HgSubrepoLocation, doesn't make sense
				// to have separate String instances (new String(line.substring()))
				String key = line.substring(0, sep).trim();
				String value = line.substring(sep + 1).trim();
				if (value.length() == 0) {
					// XXX log bad line?
					continue;
				}
				HgSubrepoLocation.Kind kind = HgSubrepoLocation.Kind.Hg;
				int kindEnd = value.indexOf(']', 1);
				if (value.charAt(0) == '[' && kindEnd != -1) {
					String kindStr = value.substring(1, kindEnd);
					value = value.substring(kindEnd + 1);
					if ("svn".equals(kindStr)) {
						kind = HgSubrepoLocation.Kind.SVN;
					} else if ("git".equals(kindStr)) {
						kind = HgSubrepoLocation.Kind.Git;
					}
				}
				// TODO respect paths mappings in config file
				HgSubrepoLocation loc = new HgSubrepoLocation(repo, key, value, kind, substate.get(key));
				res.add(loc);
			}
			return Arrays.asList(res.toArray(new HgSubrepoLocation[res.size()]));
		} finally {
			br.close();
		}
	}

	private Map<String, String> readState(BufferedReader br) throws IOException {
		HashMap<String, String> rv = new HashMap<String, String>();
		try {
			String line;
			while ((line = br.readLine()) != null) {
				int sep = line.trim().indexOf(' ');
				if (sep != -1) {
					rv.put(line.substring(sep+1).trim(), line.substring(0, sep).trim());
				}
			}
		} finally {
			br.close();
		}
		return rv;
	}

	/*public to allow access from HgRepository, otherwise package-local*/
	public void read() throws HgInvalidControlFileException {
		subRepos = readActualState();
	}
	
	public List<HgSubrepoLocation> all(/*int revision, or TIP|WC*/) {
		assert subRepos != null;
		return subRepos;
	}
}
