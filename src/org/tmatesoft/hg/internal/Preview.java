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
import java.nio.channels.ByteChannel;

/**
 * Filters may need to look into data stream before actual processing takes place,
 * for example, to find out whether filter shall be applied at all.
 * 
 * {@link ByteChannel ByteChannels} that may use filters shall be checked for {@link Preview} adaptable before writing to them. 
 * 
 * E.g. newline filter handles streams with mixed newline endings according to configuration option 
 * (either process or ignore), hence, need to check complete stream first.
 * 
 * @author Artem Tikhomirov
 * @author TMate Software Ltd.
 */
public interface Preview {
	/**
	 * Supplied buffer may be shared between few Preview filter instances, hence implementers shall NOT consume (move position)
	 * of the buffer. Caller enforces this reseting buffer position between calls to previewers.
	 * <p>
	 * XXX if this approach turns out to impose a great burden on filter implementations, FilterByteChannel may be modified to keep
	 * record of how far each filter had read it's buffer. 
	 */
	void preview(ByteBuffer src);
}