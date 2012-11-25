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

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.tmatesoft.hg.util.Adaptable;
import org.tmatesoft.hg.util.ByteChannel;
import org.tmatesoft.hg.util.CancelledException;

/**
 *
 * @author Artem Tikhomirov
 * @author TMate Software Ltd.
 */
public class FilterByteChannel implements ByteChannel, Adaptable {
	private final Filter[] filters;
	private final ByteChannel delegate;
	
	public FilterByteChannel(ByteChannel delegateChannel, Collection<Filter> filtersToApply) {
		if (delegateChannel == null || filtersToApply == null) {
			throw new IllegalArgumentException();
		}
		delegate = delegateChannel;
		filters = filtersToApply.toArray(new Filter[filtersToApply.size()]);
	}

	public int write(ByteBuffer buffer) throws IOException, CancelledException {
		final int srcPos = buffer.position();
		ByteBuffer processed = buffer;
		for (Filter f : filters) {
			// each next filter consumes not more than previous
			// hence total consumed equals position shift in the original buffer
			processed = f.filter(processed);
		}
		delegate.write(processed);
		return buffer.position() - srcPos; // consumed as much from original buffer
	}

	// adapters or implemented interfaces of the original class shall not be obfuscated by filter
	public <T> T getAdapter(Class<T> adapterClass) {
		if (adapterClass == Preview.class) {
			ArrayList<Preview> previewers = new ArrayList<Preview>(filters.length);
			Adaptable.Factory<Preview> factory = new Adaptable.Factory<Preview>(Preview.class);
			for (Filter f : filters) {
				Preview p = factory.get(f);
				if (p != null) {
					previewers.add(p);
				}
			}
			if (!previewers.isEmpty()) {
				@SuppressWarnings("unchecked")
				T rv = (T) new PreviewSupport(previewers);
				return rv;
			}
			// fall through to let delegate answer
		}
		return Adaptable.Factory.getAdapter(delegate, adapterClass, null);
	}

	private static class PreviewSupport implements Preview {
		private final Preview[] participants;

		public PreviewSupport(List<Preview> previewers) {
			participants = new Preview[previewers.size()];
			previewers.toArray(participants);
		}
		
		public void preview(ByteBuffer src) {
			final int originalPos = src.position();
			for (Preview p : participants) {
				p.preview(src);
				// reset to initial state
				src.position(originalPos);
			}
		}
	}
}
