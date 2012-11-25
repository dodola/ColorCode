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

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Much like {@link java.nio.channels.WritableByteChannel} except for thrown exception 
 * 
 * XXX Perhaps, we'll add CharChannel in the future to deal with character conversions/encodings 
 * 
 * @author Artem Tikhomirov
 * @author TMate Software Ltd.
 */
public interface ByteChannel {
	// XXX does int return value makes any sense given buffer keeps its read state
	// not clear what retvalue should be in case some filtering happened inside write - i.e. return
	// number of bytes consumed in 
	int write(ByteBuffer buffer) throws IOException, CancelledException;
}
