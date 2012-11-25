/*
 * Copyright (c) 2011-2012 TMate Software Ltd
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

import java.io.File;
import java.io.IOException;

/**
 * Thrown when there are troubles working with local file. Most likely (but not necessarily) wraps IOException. Might be 
 * perceived as specialized IOException with optional File and other repository information.
 * 
 * <b>Hg4J</b> tries to minimize chances for IOException to occur (i.e. {@link File#canRead()} is checked before attempt to 
 * read a file that might not exist, and doesn't use this exception to wrap each and any {@link IOException} source (e.g. 
 * <code>#close()</code> calls are unlikely to yield it), hence it is likely to address real cases when I/O error occurs.
 * 
 * On the other hand, when a file is supposed to exist and be readable, this exception might get thrown as well to indicate
 * that's not true. 
 * 
 * @author Artem Tikhomirov
 * @author TMate Software Ltd.
 */
@SuppressWarnings("serial")
public class HgInvalidFileException extends HgException {

	private File localFile;

	public HgInvalidFileException(String message, Throwable th) {
		super(message, th);
	}

	public HgInvalidFileException(String message, Throwable th, File file) {
		super(message, th);
		localFile = file; // allows null
	}

	public HgInvalidFileException setFile(File file) {
		assert file != null; // doesn't allow null not to clear file accidentally
		localFile = file;
		return this;
	}

	/**
	 * @return file object that causes troubles, or <code>null</code> if specific file is unknown
	 */
	public File getFile() {
		return localFile;
	}

	@Override
	protected void appendDetails(StringBuilder sb) {
		super.appendDetails(sb);
		if (localFile != null) {
			sb.append(" file:");
			sb.append(localFile.getPath());
			sb.append(',');
			if (localFile.exists()) {
				sb.append("EXISTS");
			} else {
				sb.append("DOESN'T EXIST");
			}
		}
	}
}
