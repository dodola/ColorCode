package com.dodola.colorcode;

import android.os.Environment;

public class Util {
	public static String getSdDirectory() {
		return Environment.getExternalStorageDirectory().getPath();
	}
	
	
}
