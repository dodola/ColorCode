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
 * Mix-in to report progress of a long-running operation
 *  
 * @author Artem Tikhomirov
 * @author TMate Software Ltd.
 */
public interface ProgressSupport {

	// -1 for unspecified?
	public void start(int totalUnits);
	public void worked(int units);
	// XXX have to specify whether PS implementors may expect #done regardless of job completion (i.e. in case of cancellation) 
	public void done();

	static class Factory {

		/**
		 * @param target object that might be capable to report progress. Can be <code>null</code>
		 * @return support object extracted from target or an empty, no-op implementation
		 */
		public static ProgressSupport get(Object target) {
			ProgressSupport ps = Adaptable.Factory.getAdapter(target, ProgressSupport.class, null);
			if (ps != null) {
				return ps;
			}
			return new ProgressSupport() {
				public void start(int totalUnits) {
				}
				public void worked(int units) {
				}
				public void done() {
				}
			};
		}
	}
	
	class Sub implements ProgressSupport {
		private final ProgressSupport ps;
		private int total;
		private int units;
		private int psUnits;

		public Sub(ProgressSupport parent, int parentUnits) {
			if (parent == null) {
				throw new IllegalArgumentException();
			}
			ps = parent;
			psUnits = parentUnits;
		}

		public void start(int totalUnits) {
			total = totalUnits;
		}

		public void worked(int worked) {
			// FIXME fine-grained subprogress report. now only report at about 50% 
			if (psUnits > 1 && units < total/2 && units+worked > total/2) {
				ps.worked(psUnits/2);
				psUnits -= psUnits/2;
			}
			units += worked;
		}

		public void done() {
			ps.worked(psUnits);
		}
	}

	interface Target<T> {
		T set(ProgressSupport ps);
	}
}
