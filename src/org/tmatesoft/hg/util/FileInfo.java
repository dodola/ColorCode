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

import java.nio.channels.ReadableByteChannel;

/**
 * Subset of File-related functionality to support other than {@link java.io.File}-based {@link FileIterator} implementations   
 * 
 * @author Artem Tikhomirov
 * @author TMate Software Ltd.
 */
public interface FileInfo {
	
	/**
	 * @return true if the filesystem object described by this instance exists, is a regular file and can be read
	 */
	boolean exists();
	
	/**
	 * File last modification time, in seconds since Jan 1, 1970. E.g. <code> {@link java.io.File#lastModified()} / 1000 </code>
	 * @return int value representing time, in seconds, when file was last modified.
	 */
	int lastModified();
	
	/**
	 * @return file size
	 */
	long length();

	/**
	 * Access file contents. Caller is responsible to close the channel.
	 * @return file reader object, never <code>null</code>
	 */
	ReadableByteChannel newInputChannel();
}
