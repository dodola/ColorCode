/*
 ******************************************************************************
 * Parts of this code sample are licensed under Apache License, Version 2.0   *
 * Copyright (c) 2009, Android Open Handset Alliance. All rights reserved.    *
 *                                                                            *                                                                         *
 * Except as noted, this code sample is offered under a modified BSD license. *
 * Copyright (C) 2010, Motorola Mobility, Inc. All rights reserved.           *
 *                                                                            *
 * For more details, see MOTODEV_Studio_for_Android_LicenseNotices.pdf        * 
 * in your installation folder.                                               *
 ******************************************************************************
 */

package com.dodola.colorcode;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import android.app.AlertDialog;
import android.app.ListActivity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnCreateContextMenuListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.SimpleCursorAdapter;
import android.widget.TextView;
import android.widget.Toast;

import com.dodola.colorcode.R;
import com.dodola.db.DbHelper;
import com.dodola.downcode.GitClone;
import com.dodola.downcode.Hg;

public class ProjectList extends ListActivity {

	public static final int GIT = 1, HG = 2;

	public static final int MENU_OPEN = 0;
	public static final int MENU_REMOVE = 1;

	private DbHelper dbhelper;
	private Button buttonAdd;
	private Context context;
	private int miCount;
	// private int showType;
	private Cursor cur;
	private int time = 0;
	private Runnable r;
	private ListView listView;
	private View emptyView;

	public void onCreate(Bundle bundle) {
		super.onCreate(bundle);
		setContentView(R.layout.project_list);
		context = this.getBaseContext();
		dbhelper = new DbHelper(this);
		emptyView = (View) this.findViewById(R.id.nodata_view);
		listView = getListView();
		listView.setOnItemClickListener(new OnItemClickListener() {
			public void onItemClick(AdapterView<?> arg0, final View arg1,
					int arg2, long arg3) {
				cur.moveToPosition(arg2);
				fileHandle(cur.getString(cur
						.getColumnIndex(dbhelper.FIELD_TITLE)));
			}
		});

		this.registerForContextMenu(listView);
		updateAdapter();

	}

	@Override
	public void onCreateContextMenu(ContextMenu menu, View v,
			ContextMenuInfo menuInfo) {

		menu.add(0, MENU_OPEN, 0, "打开");
		menu.add(0, MENU_REMOVE, 1, "删除");

	}

	@Override
	public boolean onContextItemSelected(MenuItem item) {
		AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) item
				.getMenuInfo();
		switch (item.getItemId()) {
		case ProjectList.MENU_OPEN:
			cur.moveToPosition(info.position);
			fileHandle(cur.getString(cur.getColumnIndex(dbhelper.FIELD_TITLE)));
			break;
		case ProjectList.MENU_REMOVE:
			final int rowId = (int) info.id;
			if (rowId > 0) {
				new AlertDialog.Builder(this)
						.setTitle("确认删除文件?")
						.setPositiveButton("确定",
								new DialogInterface.OnClickListener() {
									public void onClick(DialogInterface dialog,
											int which) {
										dbhelper.delete(rowId);

										Toast.makeText(context, "删除成功",
												Toast.LENGTH_LONG).show();
										updateAdapter();
									}
								}).setNegativeButton("取消", null).show();
			}

			break;
		default:
			break;
		}
		return super.onContextItemSelected(item);
	}

	private void fileHandle(String path) {
		Log.d("FileActivity", path);
		Intent resultIntent = new Intent(ProjectList.this,
				HTMLViewerPlusPlus.class);
		resultIntent.setData(Uri.parse("file://" + path));
		this.startActivity(resultIntent);

	}

	ProgressDialog pd;

	// private Handler handler = new Handler() {
	//
	// @Override
	// public void handleMessage(Message msg) {
	//
	// super.handleMessage(msg);
	// switch (msg.what) {
	// case 1:
	// if (pd != null && pd.isShowing()) {
	// pd.dismiss();
	// }
	// case 2:
	// if (pd != null && pd.isShowing()) {
	// pd.dismiss();
	// }
	// Toast.makeText(context, msg.obj.toString(), Toast.LENGTH_LONG)
	// .show();
	// break;
	// }
	//
	// }
	//
	// };

	/*
	 * AlertDialog addProjectDialog; private View.OnClickListener
	 * addClickListener = new View.OnClickListener() {
	 * 
	 * @Override public void onClick(View v) { // final EditText
	 * projectTitleText = new EditText(getParent()); final View dialogView =
	 * View.inflate(getParent(), R.layout.project_add_dialog, null);
	 * addProjectDialog = new AlertDialog.Builder(getParent()) .setTitle("请输入")
	 * .setIcon(android.R.drawable.ic_dialog_info) .setView(dialogView)
	 * .setPositiveButton("确定", new DialogInterface.OnClickListener() {
	 * 
	 * @Override public void onClick(DialogInterface dialog, int which) { final
	 * EditText projectTitleText = (EditText) dialogView
	 * .findViewById(R.id.project_title);
	 * 
	 * final EditText projectUrlText = (EditText) dialogView
	 * .findViewById(R.id.project_url); final String projectTitle =
	 * projectTitleText .getText().toString(); final String projectUrl =
	 * projectUrlText .getText().toString(); if (projectTitle != "" &&
	 * projectUrl != "") { addProjectDialog.dismiss(); Runnable getProject = new
	 * Runnable() {
	 * 
	 * @Override public void run() {
	 * 
	 * switch (showType) { case HG: Hg hg = new Hg(); hg.HgClone(projectUrl,
	 * projectTitle, handler); break; case GIT: GitClone git = new GitClone();
	 * git.Clone(projectTitle, projectUrl, handler); break; }
	 * addProject(projectTitleText .getText().toString(), projectUrlText
	 * .getText() .toString());
	 * 
	 * } }; handler.post(getProject); pd = ProgressDialog .show(getParent(),
	 * getString(R.string.wait), getString(R.string.get_project));
	 * 
	 * } else { Toast.makeText(getParent(), R.string.project_title_null,
	 * Toast.LENGTH_SHORT).show(); } } }).setNegativeButton("取消", null).show();
	 * 
	 * } };
	 */
	public void updateAdapter() {
		cur = dbhelper.selectAll();
		miCount = cur.getCount();
		if (cur != null && miCount > 0) {
			emptyView.setVisibility(View.GONE);
			ListAdapter adapter = new SimpleCursorAdapter(this,
					R.layout.project_list_row, cur, new String[] {
							dbhelper.FIELD_TITLE, dbhelper.FIELD_DATE },
					new int[] { R.id.title, R.id.create_date });
			this.setListAdapter(adapter);
		} else {
			emptyView.setVisibility(View.VISIBLE);
		}
	}

}