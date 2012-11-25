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

import java.io.IOException;

import org.tmatesoft.hg.internal.Experimental;

/**
 * Abstracts iteration over file system.
 * 
 * @author Artem Tikhomirov
 * @author TMate Software Ltd.
 */
public interface FileIterator {

	/**
	 * Brings iterator into initial state to facilitate new use.
	 */
	void reset() throws IOException;

	/**
	 * @return whether can shift to next element
	 */
	boolean hasNext() throws IOException;

	/**
	 * Shift to next element
	 */
	void next() throws IOException;

	/**
	 * @return repository-local path to the current element.
	 */
	Path name();

	/**
	 * {@link FileInfo} object to retrieve actual file information. Caller shall not assume new instance for each {@link #next()} entry, 
	 * implementors of this interface may reuse {@link FileInfo} instance if deemed suitable. 
	 * @return file information holder.
	 */
	FileInfo file();

	/**
	 * When {@link FileIterator} represents only fraction of a repository, library might need to figure out if
	 * specific file (path) belongs to that fraction or not. Paths and files returned by this {@link FileIterator}
	 * are always considered as representing the fraction, nonetheless, {@link FileIterator} shall return true for such names if 
	 * asked.
	 * @return <code>true</code> if this {@link FileIterator} is responsible for (interested in) specified repository-local path 
	 */
	@Experimental(reason="Perhaps, shall not be part of FileIterator, but rather separate Path.Matcher. Approaches in regular StatusCollector (doesn't use FI, but supports scope) and WC collector to look similar, and for HgStatusCommand to use single approach to set the scope")
	boolean inScope(Path file);
}
