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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;

/**
 *
 * @author Artem Tikhomirov
 * @author TMate Software Ltd.
 */
public class RequiresFile {
	public static final int STORE = 1;
	public static final int FNCACHE = 2;
	public static final int DOTENCODE = 4;
	
	public RequiresFile() {
	}

	public void parse(Internals repoImpl, File requiresFile) throws IOException {
		if (!requiresFile.exists()) {
			return;
		}
		BufferedReader br = null;
		try {
			boolean revlogv1 = false;
			boolean store = false;
			boolean fncache = false;
			boolean dotencode = false;
			br = new BufferedReader(new InputStreamReader(new FileInputStream(requiresFile)));
			String line;
			while ((line = br.readLine()) != null) {
				revlogv1 |= "revlogv1".equals(line);
				store |= "store".equals(line);
				fncache |= "fncache".equals(line);
				dotencode |= "dotencode".equals(line);
			}
			int flags = 0;
			flags += store ? STORE : 0;
			flags += fncache ? FNCACHE : 0;
			flags += dotencode ? DOTENCODE : 0;
			repoImpl.setStorageConfig(revlogv1 ? 1 : 0, flags);
		} finally {
			if (br != null) {
				br.close();
			}
		}
	}
}
