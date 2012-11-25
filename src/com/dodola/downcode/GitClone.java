package com.dodola.downcode;

import android.os.Handler;
import android.os.Message;
//import org.eclipse.jgit.api.Git;

public class GitClone {
	public static int SUCCESS = 1;
	public static int ERRMSG = 2;

	public void Clone(String desdir, String url, Handler handler) {

		// try {
//		Git.cloneRepository().setBare(true)
//				.setDirectory(new File("/mnt/sdcard/colorcode", desdir))
//				.setURI(url).call();
//		handler.sendMessage(MakeMessage(Hg.SUCCESS, "获取成功"));
		// } catch (Exception ex) {
		// handler.sendMessage(MakeMessage(Hg.ERRMSG, ex.getMessage()));
		// }
	}

	private Message MakeMessage(int what, String msg) {
		Message errMsg = new Message();
		errMsg.what = ERRMSG;
		errMsg.obj = msg;
		return errMsg;
	}
}
