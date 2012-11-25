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
package org.tmatesoft.hg.core;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.tmatesoft.hg.internal.ConfigFile;
import org.tmatesoft.hg.internal.Experimental;
import org.tmatesoft.hg.internal.Internals;
import org.tmatesoft.hg.repo.HgInternals;
import org.tmatesoft.hg.repo.HgRepository;

/**
 * WORK IN PROGRESS, DO NOT USE
 *
 * @author Artem Tikhomirov
 * @author TMate Software Ltd.
 */
@Experimental(reason="Investigating approaches to alter Hg configuration files")
public final class HgUpdateConfigCommand extends HgAbstractCommand<HgUpdateConfigCommand> {
	
	private final File configFile;
	
	private Map<String,List<String>> toRemove;
	private Map<String,Map<String,String>> toSet;

	private HgUpdateConfigCommand(File configurationFile) {
		configFile = configurationFile;
	}
	
	public static HgUpdateConfigCommand forRepository(HgRepository hgRepo) {
		// XXX HgRepository to implement SessionContextProvider (with getContext())?
		return new HgUpdateConfigCommand(new File(HgInternals.getRepositoryDir(hgRepo), "hgrc"));
	}
	
	public static HgUpdateConfigCommand forUser(SessionContext ctx) {
		return new HgUpdateConfigCommand(Internals.getUserConfigurationFileToWrite(ctx));
	}
	
	public static HgUpdateConfigCommand forInstallation(SessionContext ctx) {
		return new HgUpdateConfigCommand(Internals.getInstallationConfigurationFileToWrite(ctx));
	}
	
	/**
	 * Remove a property altogether
	 * @return <code>this</code> for convenience
	 */
	public HgUpdateConfigCommand remove(String section, String key) {
		if (toRemove == null) {
			toRemove = new LinkedHashMap<String, List<String>>();
		}
		List<String> s = toRemove.get(section);
		if (s == null) {
			toRemove.put(section, s = new ArrayList<String>(5));
		}
		s.add(key);
		if (toSet != null && toSet.containsKey(section)) {
			toSet.get(section).remove(key);
		}
		return this;
	}

	/**
	 * Delete single attribute in a multi-valued property
	 * @return <code>this</code> for convenience
	 */
	public HgUpdateConfigCommand remove(String section, String key, String value) {
		throw new UnsupportedOperationException();
	}
	
	/**
	 * Set single-valued properties or update multi-valued with a single value
	 * @return <code>this</code> for convenience
	 */
	public HgUpdateConfigCommand put(String section, String key, String value) {
		if (toSet == null) {
			toSet =  new LinkedHashMap<String, Map<String,String>>();
		}
		Map<String,String> s = toSet.get(section);
		if (s == null) {
			toSet.put(section, s = new LinkedHashMap<String, String>());
		}
		s.put(key, value);
		return this;
	}
	
	/**
	 * Multi-valued properties
	 * @return <code>this</code> for convenience
	 */
	public HgUpdateConfigCommand add(String section, String key, String value) {
		throw new UnsupportedOperationException();
	}
	
	public void execute() throws HgException {
		try {
			ConfigFile cfg = new ConfigFile();
			cfg.addLocation(configFile);
			if (toRemove != null) {
				for (Map.Entry<String,List<String>> s : toRemove.entrySet()) {
					for (String e : s.getValue()) {
						cfg.putString(s.getKey(), e, null);
					}
				}
			}
			if (toSet != null) {
				for (Map.Entry<String,Map<String,String>> s : toSet.entrySet()) {
					for (Map.Entry<String, String> e : s.getValue().entrySet()) {
						cfg.putString(s.getKey(), e.getKey(), e.getValue());
					}
				}
			}
			cfg.writeTo(configFile);
		} catch (IOException ex) {
			throw new HgInvalidFileException("Failed to update configuration file", ex, configFile);
		}
	}


	public static void main(String[] args) throws Exception {
		HgUpdateConfigCommand cmd = HgUpdateConfigCommand.forUser(null);
		cmd.remove("test1", "sample1");
		cmd.put("test2", "sample2", "value2");
		cmd.put("ui", "user-name", "Another User <email@domain.com>");
		cmd.execute();
	}
}
