/*
 * Copyright (c) 2010-2011 TMate Software Ltd
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

import org.tmatesoft.hg.util.Path;

/**
 * Callback to get file status information
 * 
 * @author Artem Tikhomirov
 * @author TMate Software Ltd.
 */
public interface HgStatusInspector {
	void modified(Path fname);
	void added(Path fname);
	/**
	 * This method is invoked for files that we added as a result of a copy/move operation, and it's the sole
	 * method invoked in this case, that is {@link #added(Path)} method is NOT invoked along with it.
	 * If copied files of no interest, it is implementation responsibility to delegate to <code>this.added(fnameAdded)</code>
	 */
	void copied(Path fnameOrigin, Path fnameAdded);
	void removed(Path fname);
	void clean(Path fname);
	/**
	 * Reports file tracked by Mercurial, but not available in file system any more, aka deleted. 
	 */
	void missing(Path fname); // 
	void unknown(Path fname); // not tracked
	void ignored(Path fname);
	/**
	 * Reports a single file error during status collecting operation. It's up to client to treat the whole operation as successful or not.
	 * The error reported is otherwise not critical for the status operation.
	 *  
	 * @param fname origin of the error
	 * @param ex describes an error occurred while accessing the file, never <code>null</code>
	 */
	void invalid(Path fname, Exception ex);
}