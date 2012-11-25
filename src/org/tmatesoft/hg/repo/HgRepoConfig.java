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

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.tmatesoft.hg.internal.ConfigFile;
import org.tmatesoft.hg.internal.Experimental;
import org.tmatesoft.hg.util.Pair;

/**
 * WORK IN PROGRESS
 * 
 * Repository-specific configuration. 
 * 
 * @author Artem Tikhomirov
 * @author TMate Software Ltd.
 */
@Experimental(reason="WORK IN PROGRESS")
public final class HgRepoConfig /*implements RepoChangeListener, perhaps, also RepoChangeNotifier? */{
	/*ease access for inner classes*/ final ConfigFile config;

	/*package-local*/HgRepoConfig(ConfigFile configFile) {
		config = configFile;
	}

	public Section getSection(String name) {
		if (name == null) {
			throw new IllegalArgumentException();
		}
		if ("paths".equals(name)) {
			return new PathsSection();
		}
		if ("extensions".equals(name)) {
			return new ExtensionsSection();
		}
		return new Section(name);
	}
	
	public boolean hasSection(String name) {
		return config.hasSection(name);
	}
	
	public boolean getBooleanValue(String section, String key, boolean defaultValue) {
		return config.getBoolean(section, key, defaultValue);
	}
	
	public String getStringValue(String section, String key, String defaultValue) {
		return config.getString(section, key, defaultValue);
	}

	//
	//
	
	public PathsSection getPaths() {
		Section s = getSection("paths");
		if (s.exists()) {
			return (PathsSection) s;
		}
		return new PathsSection();
	}
	
	public ExtensionsSection getExtensions() {
		Section s = getSection("extensions");
		if (s.exists()) {
			return (ExtensionsSection) s;
		}
		return new ExtensionsSection();
	}

	/*
	 * IMPLEMENTATION NOTE: Section is merely a view to configuration file, without any state. 
	 * In case I need to sync access to config (i.e. write) or refresh it later - can be easily done  
	 */

	public class Section implements Iterable<Pair<String,String>> {

		protected final String section;

		/*package-local*/Section(String sectionName) {
			section = sectionName;
		}
		
		public String getName() {
			return section;
		}
					
		/**
		 * Whether this is real section or a bare non-null instance 
		 */
		public boolean exists() {
			return hasSection(section);
		}

		/**
		 * @return defined keys, in the order they appear in the section
		 */
		public List<String> getKeys() {
			// PathsSection depends on return value being modifiable
			return new ArrayList<String>(config.getSection(section).keySet());
		}
		
		/**
		 * Find out whether key is present and got any value 
		 * @param key identifies an entry to look up
		 * @return true if key is present in the section and has non-empty value
		 */
		public boolean isKeySet(String key) {
			String value = getStringValue(section, key, null);
			return value != null && value.length() > 0;
		}

		/**
		 * Value of a key as boolean
		 * @param key identifies an entry to look up
		 * @param defaultValue optional value to return if no entry for the key found in this section
		 * @return value corresponding to the key, or defaultValue if key not found
		 */
		public boolean getBoolean(String key, boolean defaultValue) {
			return getBooleanValue(section, key, defaultValue);
		}
		
		/**
		 * Value of a key as regular string
		 * @param key identifies entry to look up
		 * @param defaultValue optional value to return if no entry for the key found in this section
		 * @return value corresponding to the key, or defaultValue if key not found
		 */
		public String getString(String key, String defaultValue) {
			return getStringValue(section, key, defaultValue);
		}

		public Iterator<Pair<String, String>> iterator() {
			final Map<String, String> m = config.getSection(section);
			if (m.isEmpty()) {
				return Collections.<Pair<String,String>>emptyList().iterator();
			}
			ArrayList<Pair<String, String>> rv = new ArrayList<Pair<String,String>>(m.size());
			for (Map.Entry<String, String> e : m.entrySet()) {
				rv.add(new Pair<String,String>(e.getKey(), e.getValue()));
			}
			return rv.iterator();
		}
	}
	
	/*
	 * Few well-known sections may get their specific subclasses
	 */

	/**
	 * Section [paths]
	 */
	public class PathsSection extends Section {
		PathsSection() {
			super("paths");
		}
		
		/**
		 * Similar to {@link #getKeys()}, but without entries for <b>default</b> and <b>default-push</b> paths
		 * @return list of path symbolic names
		 */
		public List<String> getPathSymbolicNames() {
			final List<String> rv = getKeys();
			rv.remove("default");
			rv.remove("default-push");
			return rv;
		}

		public boolean hasDefault() {
			return isKeySet("default");
		}
		public String getDefault() {
			return super.getString("default", null);
		}
		public boolean hasDefaultPush() {
			return isKeySet("default-push");
		}
		public String getDefaultPush() {
			return super.getString("default-push", null);
		}
	}

	/**
	 * Section [extensions]
	 *
	 * @author Artem Tikhomirov
	 * @author TMate Software Ltd.
	 */
	public class ExtensionsSection extends Section {
		ExtensionsSection() {
			super("extensions");
		}

		public boolean isEnabled(String extensionName) {
			final Map<String, String> sect = config.getSection(section);
			String value = sect.get(extensionName);
			if (value == null) {
				value = sect.get("hgext." + extensionName);
			}
			if (value == null) {
				value = sect.get("hgext/" + extensionName);
			}
			if (value != null) {
				// empty line, just "extension =" is valid way to enable it
				return value.length() == 0 || '!' != value.charAt(0);
			}
			return false;
		}
	}
}
