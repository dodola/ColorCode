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
package org.tmatesoft.hg.util;

import org.tmatesoft.hg.internal.Experimental;

/**
 * Nothing but a holder for two values.
 * 
 * @author Artem Tikhomirov
 * @author TMate Software Ltd.
 */
@Experimental
public final class Pair<T1,T2> {
	private final T1 value1;
	private final T2 value2;

	public Pair(T1 v1, T2 v2) {
		value1 = v1;
		value2 = v2;
	}

	public T1 first() {
		return value1;
	}
	public T2 second() {
		return value2;
	}
	public boolean hasFirst() {
		return value1 != null;
	}
	public boolean hasSecond() {
		return value2 != null;
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append('<');
		sb.append(first());
		sb.append(':');
		sb.append(second());
		sb.append('>');
		return sb.toString();
	}
}

