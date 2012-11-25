package com.dodola.colorcode;

import java.util.ArrayList;
import java.util.List;

import android.app.LocalActivityManager;
import android.app.TabActivity;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.os.Bundle;
import android.os.Parcelable;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.support.v4.view.ViewPager.OnPageChangeListener;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.TranslateAnimation;
import android.widget.ImageView;
import android.widget.TabHost;
import android.widget.TextView;

public class MainActivity extends TabActivity   {
	private static final int MENU_QUIT = 2;
	private static final int MENU_ABOUT = 1;
	// 页卡内容
	private ViewPager mPager;

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		// TODO Auto-generated method stub
		// Log.d("mainactivity", manager.getActivity("FileList").getClass()
		// .getName());
		if (manager.getActivity("FileList").getClass() == FileActivity.class
				&& currIndex == 0) {
			FileActivity activity = (FileActivity) manager
					.getActivity("FileList");
			activity.onKeyDown(keyCode, event);

		}
		return true;

	}

	// Tab页面列表
	private List<View> listViews;
	// 动画图片
	private ImageView cursor;

	// 页卡头标
	private TextView projectTitle, fileBrowserTitle;// 页卡头标
	// 动画图片偏移量
	private int offset = 0;
	// 当前页卡编号
	private int currIndex = 0;
	// 动画图片宽度
	private int bmpW;
	private LocalActivityManager manager = null;
	private final static String TAG = "ConfigTabActivity";
	private final Context context = MainActivity.this;
	private TabHost mTabHost;
	private Resources rs;

	int one = 0;

	private int selectTitleColor, unSelectTitleColor;

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		Log.d(TAG, "---onCreate---");

		setContentView(R.layout.main);

		rs = this.getResources();

		mTabHost = getTabHost();

		mTabHost.addTab(mTabHost.newTabSpec("OpenFile").setIndicator("")
				.setContent(new Intent(this, FileActivity.class)));
		mTabHost.addTab(mTabHost.newTabSpec("ProjectList").setIndicator("")
				.setContent(new Intent(this, ProjectList.class)));
		mTabHost.setCurrentTab(0);

		manager = new LocalActivityManager(this, true);
		manager.dispatchCreate(savedInstanceState);

		selectTitleColor = rs.getColor(R.color.text_select_color);
		unSelectTitleColor = rs.getColor(R.color.title_bar_unselected_color);
		InitImageView();
		InitTextView();
		InitViewPager();
	}

	

	/**
	 * 初始化头标
	 */
	private void InitTextView() {

		projectTitle = (TextView) findViewById(R.id.webServer_text);
		fileBrowserTitle = (TextView) findViewById(R.id.webLog_text);

		projectTitle.setOnClickListener(new MyOnClickListener(0));
		fileBrowserTitle.setOnClickListener(new MyOnClickListener(1));
	}

	/**
	 * 初始化ViewPager
	 */
	private void InitViewPager() {
		mPager = (ViewPager) findViewById(R.id.vPager);
		listViews = new ArrayList<View>();
		MyPagerAdapter mpAdapter = new MyPagerAdapter(listViews);

		Intent intent1 = new Intent(context, FileActivity.class);

		listViews.add(getView("FileList", intent1));

		Intent intent2 = new Intent(context, ProjectList.class);

		listViews.add(getView("ProjectList", intent2));

		mPager.setAdapter(mpAdapter);
		mPager.setCurrentItem(0);
		mPager.setOnPageChangeListener(new MyOnPageChangeListener());
	}

	/**
	 * 初始化动画
	 */
	private void InitImageView() {

		cursor = (ImageView) findViewById(R.id.cursor);
		bmpW = BitmapFactory.decodeResource(getResources(),
				R.drawable.arrow_down).getWidth();// 获取图片宽度
		DisplayMetrics dm = new DisplayMetrics();
		getWindowManager().getDefaultDisplay().getMetrics(dm);
		int screenW = dm.widthPixels;// 获取分辨率宽度
		offset = (screenW / 2 - bmpW) / 2;// -+ 计算偏移量

		Log.d("Main", " offset :" + offset);
		Matrix matrix = new Matrix();
		matrix.postTranslate(offset, 0);

		cursor.setImageMatrix(matrix);// 设置动画初始位置
	}

	/**
	 * ViewPager适配器
	 */
	public class MyPagerAdapter extends PagerAdapter {
		public List<View> mListViews;

		public MyPagerAdapter(List<View> mListViews) {
			this.mListViews = mListViews;
		}

		@Override
		public void destroyItem(View arg0, int arg1, Object arg2) {
			((ViewPager) arg0).removeView(mListViews.get(arg1));
		}

		@Override
		public void finishUpdate(View arg0) {
		}

		@Override
		public int getCount() {
			return mListViews.size();
		}

		@Override
		public Object instantiateItem(View arg0, int arg1) {
			((ViewPager) arg0).addView(mListViews.get(arg1), 0);
			return mListViews.get(arg1);
		}

		@Override
		public boolean isViewFromObject(View arg0, Object arg1) {
			return arg0 == (arg1);
		}

		@Override
		public void restoreState(Parcelable arg0, ClassLoader arg1) {
		}

		@Override
		public Parcelable saveState() {
			return null;
		}

		@Override
		public void startUpdate(View arg0) {
		}
	}

	/**
	 * 头标点击监听
	 */
	public class MyOnClickListener implements View.OnClickListener {
		private int index = 0;

		public MyOnClickListener(int i) {
			index = i;
		}

		@Override
		public void onClick(View v) {
			mPager.setCurrentItem(index);
		}
	};

	/**
	 * 页卡切换监听
	 */
	public class MyOnPageChangeListener implements OnPageChangeListener {

		@Override
		public void onPageSelected(int arg0) {

			one = offset * 2 + bmpW;// 页卡1 -> 页卡2 偏移量
			Animation animation = null;
			switch (arg0) {

			case 0:
				if (currIndex == 1) {
					animation = new TranslateAnimation(one, 0, 0, 0);
				}

				projectTitle.setTextColor(selectTitleColor);
				fileBrowserTitle.setTextColor(unSelectTitleColor);
				break;
			case 1:

				// 刷新列表
				ProjectList projectActivity = (ProjectList) manager
						.getActivity("ProjectList");
				projectActivity.updateAdapter();
				if (currIndex == 0) {
					animation = new TranslateAnimation(offset, one, 0, 0);
				}

				projectTitle.setTextColor(unSelectTitleColor);
				fileBrowserTitle.setTextColor(selectTitleColor);
				break;
			}
			currIndex = arg0;
			animation.setFillAfter(true);// True:图片停在动画结束位置
			animation.setDuration(300);
			cursor.startAnimation(animation);
		}

		@Override
		public void onPageScrolled(int arg0, float arg1, int arg2) {
		}

		@Override
		public void onPageScrollStateChanged(int arg0) {
		}
	}

	private View getView(String id, Intent intent) {
		return manager.startActivity(id, intent).getDecorView();
	}

	@Override
	public void setRequestedOrientation(int requestedOrientation) {
		super.setRequestedOrientation(requestedOrientation);
	}

	@Override
	public int getRequestedOrientation() {
		return super.getRequestedOrientation();
	}

	@Override
	public void onConfigurationChanged(Configuration newConfig) {

		if (this.getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE
				|| this.getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT) {
			DisplayMetrics dm = new DisplayMetrics();
			getWindowManager().getDefaultDisplay().getMetrics(dm);
			int screenW = dm.widthPixels;// 获取分辨率宽度

			offset = (screenW / 2 - bmpW) / 2;// 计算偏移量

			Matrix matrix = cursor.getImageMatrix();
			matrix.reset();

			matrix.postTranslate(offset, 0);

		}

		super.onConfigurationChanged(newConfig);
	}


}