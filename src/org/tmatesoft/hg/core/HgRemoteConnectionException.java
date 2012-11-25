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

/**
 *
 * @author Artem Tikhomirov
 * @author TMate Software Ltd.
 */
@SuppressWarnings("serial")
public class HgRemoteConnectionException extends HgException {

	private String serverInfo;
	private String cmdName;

	public HgRemoteConnectionException(String reason) {
		super(reason);
	}

	public HgRemoteConnectionException(String reason, Throwable cause) {
		super(reason, cause);
	}

	public HgRemoteConnectionException setServerInfo(String si) {
		serverInfo = si;
		return this;
	}
	public String getServerInfo() {
		return serverInfo;
	}
	
	public HgRemoteConnectionException setRemoteCommand(String commandName) {
		cmdName = commandName;
		return this;
	}

	public String getRemoteCommand() {
		return cmdName;
	}
}
