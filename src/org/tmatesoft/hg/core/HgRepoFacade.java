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

import java.io.File;

import org.tmatesoft.hg.internal.BasicSessionContext;
import org.tmatesoft.hg.repo.HgRepository;
import org.tmatesoft.hg.repo.HgLookup;

/**
 * Starting point for the library.
 * <p>Sample use:
 * <pre>
 *  HgRepoFacade f = new HgRepoFacade();
 *  f.initFrom(System.getenv("whatever.repo.location"));
 *  HgStatusCommand statusCmd = f.createStatusCommand();
 *  HgStatusCommand.Handler handler = ...;
 *  statusCmd.execute(handler);
 * </pre>
 * 
 * @author Artem Tikhomirov
 * @author TMate Software Ltd.
 */
public class HgRepoFacade {
	private HgRepository repo;
	private final SessionContext context;

	public HgRepoFacade() {
		this(new BasicSessionContext(null, null));
	}
	
	public HgRepoFacade(SessionContext ctx) {
		if (ctx == null) {
			throw new IllegalArgumentException();
		}
		context = ctx;
	}
	
	/**
	 * @param hgRepo
	 * @return true on successful initialization
	 * @throws IllegalArgumentException when argument is null 
	 */
	public boolean init(HgRepository hgRepo) {
		if (hgRepo == null) {
			throw new IllegalArgumentException();
		}
		repo = hgRepo;
		return !repo.isInvalid();
	}

	/**
	 * Tries to find repository starting from the current working directory.
	 * @return <code>true</code> if found valid repository
	 * @throws HgInvalidFileException in case of errors during repository initialization
	 */
	public boolean init() throws HgInvalidFileException {
		repo = new HgLookup(context).detectFromWorkingDir();
		return repo != null && !repo.isInvalid();
	}
	
	/**
	 * Looks up Mercurial repository starting from specified location and up to filesystem root.
	 * 
	 * @param repoLocation path to any folder within structure of a Mercurial repository.
	 * @return <code>true</code> if found valid repository 
	 * @throws HgInvalidFileException if there are errors accessing specified location
	 * @throws IllegalArgumentException if argument is <code>null</code>
	 */
	public boolean initFrom(File repoLocation) throws HgInvalidFileException {
		if (repoLocation == null) {
			throw new IllegalArgumentException();
		}
		repo = new HgLookup(context).detect(repoLocation);
		return repo != null && !repo.isInvalid();
	}
	
	public HgRepository getRepository() {
		if (repo == null) {
			throw new IllegalStateException("Call any of #init*() methods first first");
		}
		return repo;
	}

	public HgLogCommand createLogCommand() {
		return new HgLogCommand(repo/*, getCommandContext()*/);
	}

	public HgStatusCommand createStatusCommand() {
		return new HgStatusCommand(repo/*, getCommandContext()*/);
	}

	public HgCatCommand createCatCommand() {
		return new HgCatCommand(repo);
	}

	public HgManifestCommand createManifestCommand() {
		return new HgManifestCommand(repo);
	}

	public HgOutgoingCommand createOutgoingCommand() {
		return new HgOutgoingCommand(repo);
	}

	public HgIncomingCommand createIncomingCommand() {
		return new HgIncomingCommand(repo);
	}
}
