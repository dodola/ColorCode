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

import org.tmatesoft.hg.internal.Experimental;
import org.tmatesoft.hg.util.LogFacility;

/**
 * WORK IN PROGRESS
 * 
 * Access to objects that might need to be shared between various distinct operations ran during the same working session 
 * (i.e. caches, log, etc.). It's unspecified whether session context is per repository or can span multiple repositories 
 * 
 * @author Artem Tikhomirov
 * @author TMate Software Ltd.
 */
@Experimental(reason="Work in progress")
public interface SessionContext {
	LogFacility getLog();
	
	/**
	 * LIKELY TO CHANGE TO STANDALONE CONFIGURATION OBJECT
	 */
	Object getProperty(String name, Object defaultValue);
}
