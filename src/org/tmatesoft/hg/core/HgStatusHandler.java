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

import org.tmatesoft.hg.util.Path;
import org.tmatesoft.hg.util.Status;

/**
 * Callback to process {@link HgStatus} objects.
 * @author Artem Tikhomirov
 * @author TMate Software Ltd.
 */
public interface HgStatusHandler {

	/* XXX #next() as in HgChangesetHandler?
	 * perhaps, handle() is better name? If yes, rename method in HgChangesetHandler, too, to make them similar.
	 * void next(HgStatus s);
	 * XXX describe RTE and HgCallbackTargetException
	 */
	void handleStatus(HgStatus s);

	/**
	 * Report non-critical error processing single file during status operation
	 * @param file name of the file that caused the trouble
	 * @param s error description object
	 */
	void handleError(Path file, Status s);
}
