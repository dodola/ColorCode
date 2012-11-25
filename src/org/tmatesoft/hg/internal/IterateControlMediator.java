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

import org.tmatesoft.hg.internal.Lifecycle.Callback;
import org.tmatesoft.hg.util.CancelSupport;
import org.tmatesoft.hg.util.CancelledException;

/**
 *
 * @author Artem Tikhomirov
 * @author TMate Software Ltd.
 */
public class IterateControlMediator {

	private final CancelSupport src;
	private Callback receiver;

	public IterateControlMediator(CancelSupport source, Lifecycle.Callback target) {
		assert target != null;
		src = source;
		receiver = target;
	}

	public boolean checkCancelled() {
		if (src == null) {
			return false;
		}
		try {
			src.checkCancelled();
			return false;
		} catch (CancelledException ex) {
			receiver.stop();
			return true;
		}
	}
	
	public void stop() {
		receiver.stop();
	}
}
