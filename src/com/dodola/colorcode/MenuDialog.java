/*
 * Copyright (C) 2009 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.dodola.colorcode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager.LayoutParams;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.GridView;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.Toast;

/**
 * 顶部主菜单切换浮动层
 * 
 * @author lds
 * 
 */
public class MenuDialog extends Dialog {

	private ListView fileList = null;

	private Context c;

	public MenuDialog(Context context) {
		super(context);
		c = context;
		setContentView(R.layout.menu_dialog);
		setCanceledOnTouchOutside(true);

		// 设置window属性
		LayoutParams a = getWindow().getAttributes();
		a.gravity = Gravity.TOP;
		//a.dimAmount = 0; // 去背景遮盖
		getWindow().setAttributes(a);

		initMenu();
	}

	private void initMenu() {

		List<String> items = new ArrayList<String>();
		for (int i = 0; i < 10; i++) {
			items.add("test" + i);
		}
		fileList = (ListView) findViewById(R.id.fileList);
		fileList.setAdapter(new ArrayAdapter<String>(c,
				android.R.layout.simple_expandable_list_item_1, items));

	}

	public void setPosition(int x, int y) {
		LayoutParams a = getWindow().getAttributes();
		if (-1 != x)
			a.x = x;
		if (-1 != y)
			a.y = y;
		getWindow().setAttributes(a);
	}

	public void bindEvent(Activity activity) {
		setOwnerActivity(activity);

		// 绑定监听器
		fileList.setOnItemClickListener(new OnItemClickListener() {
			public void onItemClick(AdapterView<?> parent, View v,
					int position, long id) {

			}
		});

	}

}
