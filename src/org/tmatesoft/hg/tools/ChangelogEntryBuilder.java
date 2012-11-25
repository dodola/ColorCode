/*
 * Copyright (c) 2012 TMate Software Ltd
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
package org.tmatesoft.hg.tools;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.Map.Entry;

import org.tmatesoft.hg.core.Nodeid;

/**
 *
 * @author Artem Tikhomirov
 * @author TMate Software Ltd.
 */
public class ChangelogEntryBuilder {

	private String user;
	private List<String> modifiedFiles;
	private final Map<String, String> extrasMap = new LinkedHashMap<String, String>();
	private Integer tzOffset;
	private Long csetTime;
	
	public ChangelogEntryBuilder user(String username) {
		user = username;
		return this;
	}
	
	public String user() {
		if (user == null) {
			// for our testing purposes anything but null is ok. no reason to follow Hg username lookup conventions 
			user = System.getProperty("user.name");
		}
		return user;
	}
	
	public ChangelogEntryBuilder setModified(List<String> files) {
		modifiedFiles = new ArrayList<String>(files == null ? Collections.<String>emptyList() : files);
		return this;
	}

	public ChangelogEntryBuilder addModified(List<String> files) {
		if (modifiedFiles == null) {
			return setModified(files);
		}
		modifiedFiles.addAll(files);
		return this;
	}
	
	public ChangelogEntryBuilder branch(String branchName) {
		if (branchName == null || "default".equals(branchName)) {
			extrasMap.remove("branch");
		} else {
			extrasMap.put("branch", branchName);
		}
		return this;
	}
	
	public ChangelogEntryBuilder extras(Map<String, String> extras) {
		extrasMap.clear();
		extrasMap.putAll(extras);
		return this;
	}
	
	public ChangelogEntryBuilder date(long seconds, int timezoneOffset) {
		csetTime = seconds;
		tzOffset = timezoneOffset;
		return this;
	}
	
	private long csetTime() {
		if (csetTime != null) { 
			return csetTime;
		}
		return System.currentTimeMillis() / 1000;
	}
	
	private int csetTimezone(long time) {
		if (tzOffset != null) {
			return tzOffset;
		}
		return -(TimeZone.getDefault().getOffset(time) / 1000);
	}

	public byte[] build(Nodeid manifestRevision, String comment) {
		String f = "%s\n%s\n%d %d %s\n%s\n\n%s";
		StringBuilder extras = new StringBuilder();
		for (Iterator<Entry<String, String>> it = extrasMap.entrySet().iterator(); it.hasNext();) {
			final Entry<String, String> next = it.next();
			extras.append(encodeExtrasPair(next.getKey()));
			extras.append(':');
			extras.append(encodeExtrasPair(next.getValue()));
			if (it.hasNext()) {
				extras.append('\00');
			}
		}
		StringBuilder files = new StringBuilder();
		if (modifiedFiles != null) {
			for (Iterator<String> it = modifiedFiles.iterator(); it.hasNext(); ) {
				files.append(it.next());
				if (it.hasNext()) {
					files.append('\n');
				}
			}
		}
		final long date = csetTime();
		final int tz = csetTimezone(date);
		return String.format(f, manifestRevision.toString(), user(), date, tz, extras, files, comment).getBytes();
	}

	private final static CharSequence encodeExtrasPair(String s) {
		if (s != null) {
			return s.replace("\\", "\\\\").replace("\n", "\\n").replace("\r", "\\r").replace("\00", "\\0");
		}
		return s;
	}
}
