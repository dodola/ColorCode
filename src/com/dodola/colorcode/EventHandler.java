/*
    Open Manager, an open source file manager for the Android system
    Copyright (C) 2009, 2010, 2011  Joe Berria <nexesdevelopment@gmail.com>

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.dodola.colorcode;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;

import android.net.Uri;
import android.os.AsyncTask;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Bitmap;
import android.view.View.OnClickListener;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

/**
 * This class sits between the Main activity and the FileManager class. To keep
 * the FileManager class modular, this class exists to handle UI events and
 * communicate that information to the FileManger class
 * 
 * This class is responsible for the buttons onClick method. If one needs to
 * change the functionality of the buttons found from the Main activity or add
 * button logic, this is the class that will need to be edited.
 * 
 * This class is responsible for handling the information that is displayed from
 * the list view (the files and folder) with a a nested class TableRow. The
 * TableRow class is responsible for displaying which icon is shown for each
 * entry. For example a folder will display the folder icon, a Word doc will
 * display a word icon and so on. If more icons are to be added, the TableRow
 * class must be updated to display those changes.
 * 
 * @author Joe Berria
 */
public class EventHandler implements OnClickListener {

	private final Context mContext;
	private final FileManager mFileMang;
	private ThumbnailCreator mThumbnail;
	private ListAdapter mDelegate;

	// the list used to feed info into the array adapter and when multi-select
	// is on
	private ArrayList<String> mDataSource;

	/**
	 * Creates an EventHandler object. This object is used to communicate most
	 * work from the Main activity to the FileManager class.
	 * 
	 * @param context
	 *            The context of the main activity e.g Main
	 * @param manager
	 *            The FileManager object that was instantiated from Main
	 */
	public EventHandler(Context context, final FileManager manager) {
		mContext = context;
		mFileMang = manager;

		mDataSource = new ArrayList<String>(mFileMang.setHomeDir(Environment
				.getExternalStorageDirectory().getPath()));
	}

	/**
	 * This constructor is called if the user has changed the screen orientation
	 * and does not want the directory to be reset to home.
	 * 
	 * @param context
	 *            The context of the main activity e.g Main
	 * @param manager
	 *            The FileManager object that was instantiated from Main
	 * @param location
	 *            The first directory to display to the user
	 */
	public EventHandler(Context context, final FileManager manager,
			String location) {
		mContext = context;
		mFileMang = manager;

		mDataSource = new ArrayList<String>(
				mFileMang.getNextDir(location, true));
	}

	/**
	 * This method is called from the Main activity and this has the same
	 * reference to the same object so when changes are made here or there they
	 * will display in the same way.
	 * 
	 * @param adapter
	 *            The TableRow object
	 */
	public void setListAdapter(ListAdapter adapter) {
		mDelegate = adapter;
	}

	/**
	 * This method, handles the button presses of the top buttons found in the
	 * Main activity.
	 */
	@Override
	public void onClick(View v) {

		switch (v.getId()) {

		}
	}

	/**
	 * will return the data in the ArrayList that holds the dir contents.
	 * 
	 * @param position
	 *            the indext of the arraylist holding the dir content
	 * @return the data in the arraylist at position (position)
	 */
	public String getData(int position) {

		if (position > mDataSource.size() - 1 || position < 0)
			return null;

		return mDataSource.get(position);
	}

	/**
	 * called to update the file contents as the user navigates there phones
	 * file system.
	 * 
	 * @param content
	 *            an ArrayList of the file/folders in the current directory.
	 */
	public void updateDirectory(ArrayList<String> content) {
		if (!mDataSource.isEmpty())
			mDataSource.clear();

		for (String data : content)
			mDataSource.add(data);

		mDelegate.notifyDataSetChanged();
	}

	private static class ViewHolder {
		TextView fileNameView;
		TextView fileCountView;

		TextView modifiedTimeView;
		ImageView icon;
	}

	/**
	 * A nested class to handle displaying a custom view in the ListView that is
	 * used in the Main activity. If any icons are to be added, they must be
	 * implemented in the getView method. This class is instantiated once in
	 * Main and has no reason to be instantiated again.
	 * 
	 * @author Joe Berria
	 */
	public class ListAdapter extends ArrayAdapter<String> {
		private final int KB = 1024;
		private final int MG = KB * KB;
		private final int GB = MG * KB;
		private String display_size;
		private ArrayList<Integer> positions;

		public ListAdapter() {
			super(mContext, R.layout.listrow, mDataSource);
		}

		public String getFilePermissions(File file) {
			String per = "-";

			if (file.isDirectory())
				per += "d";
			if (file.canRead())
				per += "r";
			if (file.canWrite())
				per += "w";

			return per;
		}

		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			final ViewHolder mViewHolder;
			int num_items = 0;
			String temp = mFileMang.getCurrentDir();
			File file = new File(temp + "/" + mDataSource.get(position));
			String[] list = file.list();

			if (list != null)
				num_items = list.length;

			if (convertView == null) {
				LayoutInflater inflater = (LayoutInflater) mContext
						.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
				convertView = inflater.inflate(R.layout.listrow, parent, false);

				mViewHolder = new ViewHolder();
				mViewHolder.fileNameView = (TextView) convertView
						.findViewById(R.id.file_name);
				mViewHolder.modifiedTimeView = (TextView) convertView
						.findViewById(R.id.modified_time);
				mViewHolder.fileCountView = (TextView) convertView
						.findViewById(R.id.file_count);
				mViewHolder.icon = (ImageView) convertView
						.findViewById(R.id.file_image);

				convertView.setTag(mViewHolder);

			} else {
				mViewHolder = (ViewHolder) convertView.getTag();
			}

			if (file != null && file.isFile()) {
				String ext = file.toString();
				String sub_ext = ext.substring(ext.lastIndexOf(".") + 1);

				/*
				 * This series of else if statements will determine which icon
				 * is displayed
				 */
				if (sub_ext.equalsIgnoreCase("pdf")) {
					mViewHolder.icon.setImageResource(R.drawable.icon_pdf);

				} else if (sub_ext.equalsIgnoreCase("mp3")
						|| sub_ext.equalsIgnoreCase("wma")
						|| sub_ext.equalsIgnoreCase("m4a")
						|| sub_ext.equalsIgnoreCase("m4p")) {

					mViewHolder.icon.setImageResource(R.drawable.icon_music);

				} else if (sub_ext.equalsIgnoreCase("png")
						|| sub_ext.equalsIgnoreCase("jpg")
						|| sub_ext.equalsIgnoreCase("jpeg")
						|| sub_ext.equalsIgnoreCase("gif")
						|| sub_ext.equalsIgnoreCase("tiff")) {

					mViewHolder.icon.setImageResource(R.drawable.icon_image);

				} else if (sub_ext.equalsIgnoreCase("zip")
						|| sub_ext.equalsIgnoreCase("gzip")
						|| sub_ext.equalsIgnoreCase("gz")) {

					mViewHolder.icon.setImageResource(R.drawable.icon_zip);

				} else if (sub_ext.equalsIgnoreCase("m4v")
						|| sub_ext.equalsIgnoreCase("wmv")
						|| sub_ext.equalsIgnoreCase("3gp")
						|| sub_ext.equalsIgnoreCase("mp4")) {

					mViewHolder.icon.setImageResource(R.drawable.icon_video);

				} else if (sub_ext.equalsIgnoreCase("doc")
						|| sub_ext.equalsIgnoreCase("docx")) {

					mViewHolder.icon.setImageResource(R.drawable.icon_doc);

				} else if (sub_ext.equalsIgnoreCase("xls")
						|| sub_ext.equalsIgnoreCase("xlsx")) {

					mViewHolder.icon.setImageResource(R.drawable.icon_excel);

				} else if (sub_ext.equalsIgnoreCase("ppt")
						|| sub_ext.equalsIgnoreCase("pptx")) {

					mViewHolder.icon.setImageResource(R.drawable.icon_ppt);

				} else if (sub_ext.equalsIgnoreCase("html")) {
					mViewHolder.icon.setImageResource(R.drawable.icon_html);

				} else if (sub_ext.equalsIgnoreCase("xml")) {
					mViewHolder.icon.setImageResource(R.drawable.icon_xml);

				} else if (sub_ext.equalsIgnoreCase("apk")) {
					mViewHolder.icon.setImageResource(R.drawable.icon_app);
				} else if (sub_ext.equalsIgnoreCase("jar")) {
					mViewHolder.icon.setImageResource(R.drawable.icon_jar);
				} else if (sub_ext.equalsIgnoreCase("ada ")) {
					mViewHolder.icon.setImageResource(R.drawable.icon_ada);
				} else if (sub_ext.equalsIgnoreCase("java")) {
					mViewHolder.icon.setImageResource(R.drawable.icon_java);
				} else if (sub_ext.equalsIgnoreCase("cs")) {
					mViewHolder.icon.setImageResource(R.drawable.icon_cs);
				} else if (sub_ext.equalsIgnoreCase("c")) {
					mViewHolder.icon.setImageResource(R.drawable.icon_c);
				} else if (sub_ext.equalsIgnoreCase("cpp")) {
					mViewHolder.icon.setImageResource(R.drawable.icon_cpp);
				} else if (sub_ext.equalsIgnoreCase("py")) {
					mViewHolder.icon.setImageResource(R.drawable.icon_py);
				} else if (sub_ext.equalsIgnoreCase("rb")) {
					mViewHolder.icon.setImageResource(R.drawable.icon_rb);
				} else if (sub_ext.equalsIgnoreCase("sh")) {// shell
					mViewHolder.icon.setImageResource(R.drawable.icon_sh);
				} else if (sub_ext.equalsIgnoreCase("log")) {
					mViewHolder.icon.setImageResource(R.drawable.icon_log);
				} else if (sub_ext.equalsIgnoreCase("js")) {
					mViewHolder.icon.setImageResource(R.drawable.icon_js);
				} else if (sub_ext.equalsIgnoreCase("php")) {
					mViewHolder.icon.setImageResource(R.drawable.icon_php);
				} else if (sub_ext.equalsIgnoreCase("scala")) {
					mViewHolder.icon.setImageResource(R.drawable.icon_scala);
				} else if (sub_ext.equalsIgnoreCase("sql")) {
					mViewHolder.icon.setImageResource(R.drawable.icon_sql);
				} else if (sub_ext.equalsIgnoreCase("pl")) {
					mViewHolder.icon.setImageResource(R.drawable.icon_pl);
				} else if (sub_ext.equalsIgnoreCase("tcl")) {
					mViewHolder.icon.setImageResource(R.drawable.icon_tcl);
				} else if (sub_ext.equalsIgnoreCase("latex")) {
					mViewHolder.icon.setImageResource(R.drawable.icon_latex);
				} else if (sub_ext.equalsIgnoreCase("css")) {
					mViewHolder.icon.setImageResource(R.drawable.icon_css);
				} else if (sub_ext.equalsIgnoreCase("makefile")) {
					mViewHolder.icon.setImageResource(R.drawable.icon_makefile);
				} else if (sub_ext.equalsIgnoreCase("css")) {
					mViewHolder.icon.setImageResource(R.drawable.icon_css);
				} else if (sub_ext.equalsIgnoreCase("diff")) {
					mViewHolder.icon.setImageResource(R.drawable.icon_diff);
				} else if (sub_ext.equalsIgnoreCase("frag")) {// glsl
					mViewHolder.icon.setImageResource(R.drawable.icon_frag);
				} else if (sub_ext.equalsIgnoreCase("hx")) {// haxe
					mViewHolder.icon.setImageResource(R.drawable.icon_hx);
				} else if (sub_ext.equalsIgnoreCase("ldap")) {
					mViewHolder.icon.setImageResource(R.drawable.icon_ldap);
				} else if (sub_ext.equalsIgnoreCase("lsm")) {
					mViewHolder.icon.setImageResource(R.drawable.icon_lsm);
				} else if (sub_ext.equalsIgnoreCase("m4")) {
					mViewHolder.icon.setImageResource(R.drawable.icon_m4);
				} else if (sub_ext.equalsIgnoreCase("ml")) {// objective caml
					mViewHolder.icon.setImageResource(R.drawable.icon_ml);
				} else if (sub_ext.equalsIgnoreCase("pas")) {// pascal
					mViewHolder.icon.setImageResource(R.drawable.icon_pas);
				} else if (sub_ext.equalsIgnoreCase("prolog")) {// prolog
					mViewHolder.icon.setImageResource(R.drawable.icon_prolog);
				} else if (sub_ext.equalsIgnoreCase("sl")) {// s-lang
					mViewHolder.icon.setImageResource(R.drawable.icon_sl);
				} else {
					mViewHolder.icon.setImageResource(R.drawable.icon_file);
				}

			} else if (file != null && file.isDirectory()) {

				mViewHolder.icon.setImageResource(R.drawable.icon_folder);
			}

			if (file.isFile()) {
				double size = file.length();
				if (size > GB)
					display_size = String
							.format("%.2f Gb ", (double) size / GB);
				else if (size < GB && size > MG)
					display_size = String
							.format("%.2f Mb ", (double) size / MG);
				else if (size < MG && size > KB)
					display_size = String
							.format("%.2f Kb ", (double) size / KB);
				else
					display_size = String.format("%.2f bytes ", (double) size);

				mViewHolder.fileCountView.setText(display_size);

			} else {
				mViewHolder.fileCountView.setText("(" + num_items + ")");
			}
			mViewHolder.fileNameView.setText(file.getName());
			mViewHolder.modifiedTimeView.setText(getDate(file));
			return convertView;
		}

		private String getDate(File file) {
			String pattern = " ";
			Calendar now = Calendar.getInstance();
			Date fileDate = new Date(file.lastModified());
			if (fileDate.before(now.getTime()))
				pattern = getContext().getString(R.string.modify_date_format);
			else
				pattern = getContext().getString(R.string.date_format);
			SimpleDateFormat formatter = new SimpleDateFormat(pattern);
			return formatter.format(fileDate);
		}
	}

}
