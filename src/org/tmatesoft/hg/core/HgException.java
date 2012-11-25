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

import static org.tmatesoft.hg.repo.HgRepository.BAD_REVISION;

import org.tmatesoft.hg.repo.HgRepository;
import org.tmatesoft.hg.util.Path;

/**
 * Root class for all hg4j exceptions.
 * 
 * @author Artem Tikhomirov
 * @author TMate Software Ltd.
 */
@SuppressWarnings("serial")
public class HgException extends Exception {

	protected int revNumber = BAD_REVISION;
	protected Nodeid revision;
	protected Path filename;

	public HgException(String reason) {
		super(reason);
	}

	public HgException(String reason, Throwable cause) {
		super(reason, cause);
	}

	public HgException(Throwable cause) {
		super(cause);
	}

	/**
	 * @return not {@link HgRepository#BAD_REVISION} only when revision index was supplied at the construction time
	 */
	public int getRevisionIndex() {
		return revNumber;
	}

	/**
	 * @deprecated use {@link #getRevisionIndex()}
	 */
	@Deprecated
	public int getRevisionNumber() {
		return getRevisionIndex();
	}
	

	public HgException setRevisionIndex(int rev) {
		revNumber = rev;
		return this;
	}
	
	/**
	 * @deprecated use {@link #setRevisionIndex(int)}
	 */
	@Deprecated
	public final HgException setRevisionNumber(int rev) {
		return setRevisionIndex(rev);
	}

	/**
	 * @return non-null only when revision was supplied at construction time
	 */
	public Nodeid getRevision() {
		return revision;
	}

	public HgException setRevision(Nodeid r) {
		revision = r;
		return this;
	}

	/**
	 * @return non-null only if file name was set at construction time
	 */
	public Path getFileName() {
		return filename;
	}

	public HgException setFileName(Path name) {
		filename = name;
		return this;
	}
	
	protected void appendDetails(StringBuilder sb) {
		if (filename != null) {
			sb.append("path:'");
			sb.append(filename);
			sb.append('\'');
			sb.append(';');
			sb.append(' ');
		}
		sb.append("rev:");
		if (revNumber != BAD_REVISION) {
			sb.append(revNumber);
			if (revision != null) {
				sb.append(':');
			}
		}
		if (revision != null) {
			sb.append(revision.shortNotation());
		}
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder(super.toString());
		sb.append(' ');
		sb.append('(');
		appendDetails(sb);
		sb.append(')');
		return sb.toString();
	}
//	/* XXX CONSIDER capability to pass extra information about errors */
//	public static class Status {
//		public Status(String message, Throwable cause, int errorCode, Object extraData) {
//		}
//	}
}
