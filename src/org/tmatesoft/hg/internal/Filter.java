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

import java.nio.ByteBuffer;

import org.tmatesoft.hg.repo.HgRepository;
import org.tmatesoft.hg.util.Path;

/**
 *
 * @author Artem Tikhomirov
 * @author TMate Software Ltd.
 */
public interface Filter {

	/**
	 * Filters data and returns a new buffer with data or an original buffer.
	 * Original buffer may not be fully consumed, #compact() might be operation to perform
	 * @param src
	 * @return a buffer ready to be read
	 */
	//  
	ByteBuffer filter(ByteBuffer src);

	/*
	 * Factory doesn't look into data (i.e. could't do a preview), works solely with path
	 */
	interface Factory {
		void initialize(HgRepository hgRepo);
		// may return null if for a given path and/or options this filter doesn't make any sense
		Filter create(Path path, Options opts);
	}

	enum Direction {
		FromRepo, ToRepo
	}

	public class Options {

		private final Direction direction;
		public Options(Direction dir) {
			direction = dir;
		}
		
		Direction getDirection() {
			return direction;
		}

	}
}
