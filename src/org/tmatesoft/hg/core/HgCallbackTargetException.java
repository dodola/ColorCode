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

import org.tmatesoft.hg.util.Path;



/**
 * Checked exception that indicates errors in client code and tries to supply extra information about the context it occurred in.
 * 
 * Generally, client need to pass own error information/exceptions from within implementations of the callback methods they supply. 
 * However, there's no straightforward way to alter throws clause for these methods, and alternatives like generic {@link Exception} or
 * library's own {@link HgException} are rather obscure. Suggested approach is to wrap whatever exception user code produces with
 * {@link RuntimeException} subclass, {@link Wrap}. Then, unwrap and re-throw with checked {@link HgCallbackTargetException}. 
 * 
 * FIXME REVISIT perhaps, shall just throw HgCallbackTargetException from any handler, and do not catch anything in commands at all?
 * FIXME decide whether shall root at HgException ("throws HgException, HgCallbackTargetException" looks a bit odd now) 
 * 
 * @author Artem Tikhomirov
 * @author TMate Software Ltd.
 */
@SuppressWarnings("serial")
public class HgCallbackTargetException extends HgException {
	/**
	 * @param cause can't be <code>null</code>
	 */
	public HgCallbackTargetException(Throwable cause) {
		super((String) null);
		if (cause == null) {
			throw new IllegalArgumentException();
		}
		if (cause.getClass() == Wrap.class) {
			// eliminate wrapper
			initCause(cause.getCause());
		} else {
			initCause(cause);
		}
	}

	@SuppressWarnings("unchecked")
	public <T extends Exception> T getTargetException() {
		return (T) getCause();
	}
	
	/**
	 * Despite this exception is merely a way to give users access to their own exceptions, it may still supply 
	 * valuable debugging information about what led to the error.
	 */
	@Override
	public String getMessage() {
		StringBuilder sb = new StringBuilder();
		sb.append("Original exception thrown: ");
		sb.append(getCause().getClass().getName());
		sb.append(" at ");
		appendDetails(sb);
		return sb.toString();
	}

	@Override
	public HgCallbackTargetException setRevision(Nodeid r) {
		return (HgCallbackTargetException) super.setRevision(r);
	}
	@Override
	public HgCallbackTargetException setRevisionIndex(int rev) {
		return (HgCallbackTargetException) super.setRevisionIndex(rev);
	}
	@Override
	public HgCallbackTargetException setFileName(Path name) {
		return (HgCallbackTargetException) super.setFileName(name);
	}

	/**
	 * Given the approach high-level handlers throw RuntimeExceptions to indicate errors, and
	 * a need to throw reasonable checked exception from client code, clients may utilize this class
	 * to get their checked exceptions unwrapped by {@link HgCallbackTargetException} and serve as that 
	 * exception cause, eliminating {@link RuntimeException} mediator.
	 */
	public static final class Wrap extends RuntimeException {

		public Wrap(Throwable cause) {
			super(cause);
			if (cause == null) {
				throw new IllegalArgumentException();
			}
		}
	}
}
