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

/**
 * Mix-in for objects that support cancellation. 
 * 
 * @author Artem Tikhomirov
 * @author TMate Software Ltd.
 */
public interface CancelSupport {

	/**
	 * This method is invoked to check if target had been brought to canceled state. Shall silently return if target is
	 * in regular state.
	 * @throws CancelledException when target internal state has been changed to canceled.
	 */
	void checkCancelled() throws CancelledException;


	// Yeah, this factory class looks silly now, but perhaps in the future I'll need wrappers for other cancellation sources?
	// just don't want to have general Utils class with methods like get() below
	static class Factory {

		/**
		 * Obtain non-null cancel support object.
		 * 
		 * @param target any object (or <code>null</code>) that might have cancel support. For <code>null</code>, returns an instance than never cancels.
		 * @return target if it's capable checking cancellation status or no-op implementation that never cancels.
				 */
		public static CancelSupport get(Object target) {
			CancelSupport cs = get(target, null);
			if (cs != null) {
				return cs;
			}
			return new CancelSupport() {
				public void checkCancelled() {
				}
			};
		}
		
		public static CancelSupport get(Object target, CancelSupport defaultValue) {
			return Adaptable.Factory.getAdapter(target, CancelSupport.class, defaultValue);
		}
	}

	interface Target<T> {
		T set(CancelSupport cs);
	}
}
