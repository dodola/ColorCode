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
package org.tmatesoft.hg.core;

import org.tmatesoft.hg.util.CancelSupport;
import org.tmatesoft.hg.util.ProgressSupport;

/**
 * intentionally package-local, might be removed or refactored in future
 * 
 * @author Artem Tikhomirov
 * @author TMate Software Ltd.
 */
class HgAbstractCommand<T extends HgAbstractCommand<?>> implements ProgressSupport.Target<T>, CancelSupport.Target<T> {
	private ProgressSupport progressHelper;
	private CancelSupport cancelHelper;

	@SuppressWarnings("unchecked")
	public T set(ProgressSupport ps) {
		progressHelper = ps;
		return (T) this;
	}
	
	@SuppressWarnings("unchecked")
	public T set(CancelSupport cs) {
		cancelHelper = cs;
		return (T) this;
	}

	// shall not return null
	protected ProgressSupport getProgressSupport(Object context) {
		if (progressHelper != null) {
			return progressHelper;
		}
		return ProgressSupport.Factory.get(context);
	}

	// shall not return null if create is true
	// CancelSupport from context, if any, takes precedence
	protected CancelSupport getCancelSupport(Object context, boolean create) {
		CancelSupport rv = CancelSupport.Factory.get(context, null);
		if (rv != null) {
			return rv;
		}
		if (cancelHelper != null) {
			return cancelHelper;
		}
		if (create) {
			return CancelSupport.Factory.get(null);
		}
		return null;
	}

}
