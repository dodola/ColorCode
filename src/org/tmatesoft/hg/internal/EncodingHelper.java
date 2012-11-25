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

import java.io.UnsupportedEncodingException;

import org.tmatesoft.hg.core.HgBadStateException;

/**
 * Keep all encoding-related issues in the single place
 * @author Artem Tikhomirov
 * @author TMate Software Ltd.
 */
public class EncodingHelper {
	// XXX perhaps, shall not be full of statics, but rather an instance coming from e.g. HgRepository?

	public static String fromManifest(byte[] data, int start, int length) {
		try {
			return new String(data, start, length, "ISO-8859-1");
		} catch (UnsupportedEncodingException ex) {
			// can't happen
			throw new HgBadStateException(ex);
		}
	}
}
