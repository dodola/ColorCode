package com.dodola.downcode;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;

import org.tmatesoft.hg.core.HgBadArgumentException;
import org.tmatesoft.hg.core.HgCloneCommand;
import org.tmatesoft.hg.core.HgDataStreamException;
import org.tmatesoft.hg.core.HgInvalidControlFileException;
import org.tmatesoft.hg.core.HgInvalidFileException;
import org.tmatesoft.hg.core.HgInvalidRevisionException;
import org.tmatesoft.hg.core.HgRemoteConnectionException;
import org.tmatesoft.hg.repo.HgDataFile;
import org.tmatesoft.hg.repo.HgLookup;
import org.tmatesoft.hg.repo.HgRemoteRepository;
import org.tmatesoft.hg.repo.HgRepository;
import org.tmatesoft.hg.util.ByteChannel;
import org.tmatesoft.hg.util.CancelledException;

import android.os.Handler;
import android.os.Message;

public class Hg {

	public static int SUCCESS = 1;
	public static int ERRMSG = 2;

	public void HgClone(String repo, String dest, Handler handler) {
		HgCloneCommand cmd = new HgCloneCommand();
		String remoteRepo = repo;
		HgRemoteRepository hgRemote;
		try {
			hgRemote = new HgLookup().detectRemote(remoteRepo, null);

			if (hgRemote.isInvalid()) {
				// System.err.printf("Remote repository %s is not valid",
				// hgRemote.getLocation());

				handler.sendMessage(MakeMessage(Hg.ERRMSG,
						String.format("远程路径不正确:%s", hgRemote.getLocation())));
				return;
			}
			cmd.source(hgRemote);
			cmd.destination(new File("/mnt/sdcard/colorcode/" + "." + dest));
			HgRepository execute = cmd.execute();
			ArrayList<String> files = cmd.GetFiles();
			for (String file : files) {
				GetFile(execute, dest, file);
			}
			handler.sendMessage(MakeMessage(Hg.SUCCESS, "获取成功"));
		} catch (HgInvalidFileException e) {
			e.printStackTrace();
			handler.sendMessage(MakeMessage(Hg.ERRMSG, e.getMessage()));
		} catch (CancelledException e) {
			handler.sendMessage(MakeMessage(Hg.ERRMSG, e.getMessage()));
			e.printStackTrace();
		} catch (HgInvalidRevisionException e) {
			handler.sendMessage(MakeMessage(Hg.ERRMSG, e.getMessage()));
			e.printStackTrace();

		} catch (HgBadArgumentException e) {
			handler.sendMessage(MakeMessage(Hg.ERRMSG, e.getMessage()));
			e.printStackTrace();
		} catch (HgRemoteConnectionException e) {
			handler.sendMessage(MakeMessage(Hg.ERRMSG, e.getMessage()));
			e.printStackTrace();
		}

	}

	private Message MakeMessage(int what, String msg) {
		Message errMsg = new Message();
		errMsg.what = ERRMSG;
		errMsg.obj = msg;
		return errMsg;
	}

	private void GetFile(HgRepository hgRepo, String folder, String name) {

		if (hgRepo != null) {
			OutputStreamChannel out;
			try {
				File path = new File("/mnt/sdcard/colorcode/" + folder + "/",
						name);
				path.getParentFile().mkdirs();
				out = new OutputStreamChannel(new FileOutputStream(
						"/mnt/sdcard/colorcode/" + folder + "/" + name));
				HgDataFile fn = hgRepo.getFileNode(name);
				fn.contentWithFilters(-3, out);
			} catch (FileNotFoundException e) {

				e.printStackTrace();
			} catch (HgInvalidControlFileException e) {

				e.printStackTrace();
			} catch (HgInvalidRevisionException e) {

				e.printStackTrace();
			} catch (HgDataStreamException e) {

				e.printStackTrace();
			} catch (CancelledException e) {

				e.printStackTrace();
			}

		}
	}

	private class OutputStreamChannel implements ByteChannel {

		private final FileOutputStream stream;

		public OutputStreamChannel(FileOutputStream out) {
			stream = out;
		}

		public int write(ByteBuffer buffer) throws IOException {
			int count = buffer.remaining();
			while (buffer.hasRemaining()) {
				stream.write(buffer.get());
			}

			return count;
		}
	}
}
