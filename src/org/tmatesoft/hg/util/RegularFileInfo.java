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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;

import org.tmatesoft.hg.internal.StreamLogFacility;

/**
 *
 * @author Artem Tikhomirov
 * @author TMate Software Ltd.
 */
public class RegularFileInfo implements FileInfo {
	private File file;
	
	public RegularFileInfo() {
	}
	
	public void init(File f) {
		file = f;
	}
	
	public boolean exists() {
		return file.canRead() && file.isFile();
	}

	public int lastModified() {
		return (int) (file.lastModified() / 1000);
	}

	public long length() {
		return file.length();
	}

	public ReadableByteChannel newInputChannel() {
		try {
			return new FileInputStream(file).getChannel();
		} catch (FileNotFoundException ex) {
			StreamLogFacility.newDefault().debug(getClass(), ex, null);
			// shall not happen, provided this class is used correctly
			return new ReadableByteChannel() {
				
				public boolean isOpen() {
					return true;
				}
				
				public void close() throws IOException {
				}
				
				public int read(ByteBuffer dst) throws IOException {
					// EOF right away
					return -1;
				}
			};
		}
	}

}
