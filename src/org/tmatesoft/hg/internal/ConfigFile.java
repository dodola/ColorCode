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
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * .ini / .rc file reader
 * @author Artem Tikhomirov
 * @author TMate Software Ltd.
 */
public class ConfigFile {

	private List<String> sections;
	private List<Map<String,String>> content;

	public ConfigFile() {
	}

	public void addLocation(File path) throws IOException {
		read(path);
	}
	
	public boolean hasSection(String sectionName) {
		return sections == null ? false : sections.indexOf(sectionName) != -1;
	}
	
	public List<String> getSectionNames() {
		return sections == null ? Collections.<String>emptyList() : Collections.unmodifiableList(sections);
	}

	public Map<String,String> getSection(String sectionName) {
		if (sections == null) {
			return Collections.emptyMap();
		}
		int x = sections.indexOf(sectionName);
		if (x == -1) {
			return Collections.emptyMap();
		}
		return Collections.unmodifiableMap(content.get(x));
	}

	public boolean getBoolean(String sectionName, String key, boolean defaultValue) {
		String value = getSection(sectionName).get(key);
		if (value == null) {
			return defaultValue;
		}
		for (String s : new String[] { "true", "yes", "on", "1" }) {
			if (s.equalsIgnoreCase(value)) {
				return true;
			}
		}
		return false;
	}
	
	public String getString(String sectionName, String key, String defaultValue) {
		String value = getSection(sectionName).get(key);
		return value == null ? defaultValue : value;
	}
	
	public void putString(String sectionName, String key, String newValue) {
		Map<String, String> section = null;
		if (sections == null) {
			// init, in case we didn't read any file with prev config
			sections = new ArrayList<String>();
			content = new ArrayList<Map<String,String>>();
		}
		int x = sections.indexOf(sectionName);
		if (x == -1) {
			if (newValue == null) {
				return;
			}
			sections.add(sectionName);
			content.add(section = new LinkedHashMap<String, String>());
		} else {
			section = content.get(x);
		}
		if (newValue == null) {
			section.remove(key);
		} else {
			section.put(key, newValue);
		}
	}
	
	// TODO handle %include and %unset directives
	// TODO "" and lists
	// XXX perhaps, single string to keep whole section with substrings for keys/values to minimize number of arrays (String.value)
	private void read(File f) throws IOException {
		if (f == null || !f.canRead()) {
			return;
		}
		if (sections == null) {
			sections = new ArrayList<String>();
			content = new ArrayList<Map<String,String>>();
		}
		BufferedReader br = null;
		try {
			br = new BufferedReader(new FileReader(f));
			String line;
			String sectionName = "";
			Map<String,String> section = new LinkedHashMap<String, String>();
			while ((line = br.readLine()) != null) {
				line = line.trim();
				int x;
				if ((x = line.indexOf('#')) != -1) {
					// do not keep comments in memory, get new, shorter string
					line = new String(line.substring(0, x).trim());
				}
				if (line.length() <= 2) { // a=b or [a] are at least of length 3
					continue;
				}
				if (line.charAt(0) == '[' && line.charAt(line.length() - 1) == ']') {
					sectionName = line.substring(1, line.length() - 1);
					if (sections.indexOf(sectionName) == -1) {
						sections.add(sectionName);
						content.add(section = new LinkedHashMap<String, String>());
					} else {
						section = null; // drop cached value
					}
				} else if ((x = line.indexOf('=')) != -1) {
					// share char[] of the original string
					String key = line.substring(0, x).trim();
					String value = line.substring(x+1).trim();
					if (section == null) {
						int i = sections.indexOf(sectionName);
						assert i >= 0;
						section = content.get(i);
					}
					if (sectionName.length() == 0) {
						// add fake section only if there are any values 
						sections.add(sectionName);
						content.add(section);
					}
					section.put(key, value);
				}
			}
		} finally {
			((ArrayList<?>) sections).trimToSize();
			((ArrayList<?>) content).trimToSize();
			if (br != null) {
				br.close();
			}
		}
		assert sections.size() == content.size();
	}

	public void writeTo(File f) throws IOException {
		byte[] data = compose();
		if (!f.exists()) {
			f.createNewFile();
		}
		FileChannel fc = new FileOutputStream(f).getChannel();
		FileLock fl = fc.lock();
		try {
			fc.write(ByteBuffer.wrap(data));
		} finally {
			fl.release();
			fc.close();
		}
	}
	
	private byte[] compose() {
		ByteArrayOutputStream bos = new ByteArrayOutputStream(1024);
		PrintStream ps = new PrintStream(bos);
		Iterator<String> sectionNames = sections.iterator();
		for (Map<String,String> s : content) {
			final String name = sectionNames.next(); // iterate through names despite section may be empty
			if (s.isEmpty()) {
				continue; // do not write an empty section
			}
			ps.print('[');
			ps.print(name);
			ps.println(']');
			for (Map.Entry<String, String> e : s.entrySet()) {
				ps.print(e.getKey());
				ps.print('=');
				ps.println(e.getValue());
			}
			ps.println();
		}
		ps.flush();
		return bos.toByteArray();
	}
}
