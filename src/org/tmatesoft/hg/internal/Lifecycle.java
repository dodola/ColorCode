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

/**
 * EXPERIMENTAL.
 * Mix-in for RevlogStream.Inspector to get informed about start and end of the iteration
 * 
 * @author Artem Tikhomirov
 * @author TMate Software Ltd.
 */
@Experimental(reason="Experimenting whether such approach pays off")
public interface Lifecycle {

	/**
	 * @param count approximate number of iterations.
	 * @param callback callback to communicate with RevlogStream (now to stop iteration only)
	 * @param token identifier of the process
	 */
	public void start(int count, Callback callback, Object token);

	/**
	 * @param token identifier of the process, identical to the one passed in {@link #start(int, Callback, Object)} of this iteration
	 */
	public void finish(Object token);

	/**
	 * Access to RevlogStream facilities.
	 */
	interface Callback {
		void stop();
	}
	
	class BasicCallback implements Callback {
		private boolean done = false;
		
		public void stop() {
			done = true;
		}
		public boolean isStopped() {
			return done;
		}
	}
}
