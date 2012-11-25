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
package org.tmatesoft.hg.util;

import org.tmatesoft.hg.internal.Experimental;

/**
 * WORK IN PROGRESS
 * 
 * Intention of this class is to abstract away almost any log facility out there clients might be using with the <b>Hg4J</b> library, 
 * not to be a full-fledged logging facility of its own.
 * 
 * Implementations may wrap platform- or application-specific loggers, e.g. {@link java.util.logging.Logger} or 
 * <code>org.eclipse.core.runtime.ILog</code>
 * 
 * @author Artem Tikhomirov
 * @author TMate Software Ltd.
 */
@Experimental(reason="API might get changed")
public interface LogFacility {

	boolean isDebug();
	boolean isInfo();

	// src and format never null
	void debug(Class<?> src, String format, Object... args);
	void info(Class<?> src, String format, Object... args);
	void warn(Class<?> src, String format, Object... args);
	void error(Class<?> src, String format, Object... args);

	// src shall be non null, either th or message or both
	void debug(Class<?> src, Throwable th, String message);
	void info(Class<?> src, Throwable th, String message);
	void warn(Class<?> src, Throwable th, String message);
	void error(Class<?> src, Throwable th, String message);
}
