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

import org.tmatesoft.hg.util.CancelledException;

/**
 * Callback to process {@link HgChangeset changesets}.
 * 
 * @author Artem Tikhomirov
 * @author TMate Software Ltd.
 */
public interface HgChangesetHandler/*XXX perhaps, shall parameterize with exception clients can throw, like: <E extends Exception>*/ {
	/**
	 * @param changeset not necessarily a distinct instance each time, {@link HgChangeset#clone() clone()} if need a copy.
	 * @throws CancelledException if handler is not interested in more changesets and iteration shall stop
	 * @throws RuntimeException or any subclass thereof to indicate error. General contract is that RuntimeExceptions 
	 * will be re-thrown wrapped into {@link HgCallbackTargetException}.  
	 */
	void next(HgChangeset changeset) throws CancelledException;
}
