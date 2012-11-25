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

/**
 * Thrown when client supplied an argument that turned out to be incorrect.
 * E.g. an {@link java.net.URL URL} of remote server  or {@link java.io.File File} destination for a new repository
 * might be otherwise valid, but unsuitable for the purpose of the operation.
 *  
 * Not a replacement for {@link IllegalArgumentException} or {@link NullPointerException}.
 * 
 * TODO review usage to match description
 * 
 * @author Artem Tikhomirov
 * @author TMate Software Ltd.
 */
@SuppressWarnings("serial")
public class HgBadArgumentException extends HgException {

	public HgBadArgumentException(String message, Throwable cause) {
		super(message, cause);
	}
}
