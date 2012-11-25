package com.dodola.colorcode;

import android.app.Activity;
import android.content.Intent;
import android.graphics.drawable.BitmapDrawable;
import android.os.Bundle;
import android.text.TextPaint;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.RelativeLayout.LayoutParams;
import android.widget.TextView;

public class WithHeaderActivity extends Activity {

	private static final String TAG = "WithHeaderActivity";

	public static final int HEADER_STYLE_FILE = 1;
	public static final int HEADER_STYLE_WRITE = 2;
	public static final int HEADER_STYLE_BACK = 3;
	public static final int HEADER_STYLE_SEARCH = 4;

	protected ImageButton refreshButton;
	protected ImageButton searchButton;
	protected ImageButton writeButton;
	protected TextView titleButton;
	protected Button backButton;
	protected ImageButton homeButton;
	protected MenuDialog dialog;
	protected EditText searchEdit;

	// LOGO按钮
	protected void addTitleButton() {

		// Find View
		titleButton = (TextView) findViewById(R.id.title);

		// titleButton.setOnClickListener(new View.OnClickListener() {
		// public void onClick(View v) {
		//
		// int top = titleButton.getTop();
		// int height = titleButton.getHeight();
		// int x = top + height;
		//
		// if (null == dialog) {
		// Log.i(TAG, "Create menu dialog.");
		// dialog = new MenuDialog(WithHeaderActivity.this);
		// dialog.bindEvent(WithHeaderActivity.this);
		// dialog.setPosition(-1, x);
		// }
		//
		// if (dialog.isShowing()) {
		// dialog.dismiss(); //没机会触发
		// } else {
		// dialog.show();
		// }
		// }
		// });
	}

	protected void setHeaderTitle(String title) {
		titleButton.setBackgroundDrawable(new BitmapDrawable());
		titleButton.setText(title);
		LayoutParams lp = new LayoutParams(
				android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
				android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
		lp.setMargins(3, 12, 0, 0);
		titleButton.setLayoutParams(lp);
		// 中文粗体
		TextPaint tp = titleButton.getPaint();
		tp.setFakeBoldText(true);
	}

	protected void setHeaderTitle(int resource) {
		titleButton.setBackgroundResource(resource);
	}

	protected void initHeader(int style) {
		switch (style) {
		case HEADER_STYLE_FILE:
			addHeaderView(R.layout.header);
			addTitleButton();
			break;
		}
	}

	private void addHeaderView(int resource) {
		ViewGroup root = (ViewGroup) getWindow().getDecorView();
		ViewGroup content = (ViewGroup) root.getChildAt(0);
		View header = View.inflate(WithHeaderActivity.this, resource, null);
		content.addView(header, 0);
	}

	@Override
	protected void onDestroy() {

		if (dialog != null) {
			dialog.dismiss();
		}
		super.onDestroy();
	}
}
