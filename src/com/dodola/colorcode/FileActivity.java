package com.dodola.colorcode;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;

import com.dodola.db.DbHelper;

import android.app.AlertDialog;
import android.app.ListActivity;
import android.content.ActivityNotFoundException;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.AdapterView.AdapterContextMenuInfo;

public class FileActivity extends ListActivity {

	private static final int MENU_OPEN = 0;
	private static final int MENU_ADDFAV = 1;
	private FileManager mFileMag;
	private EventHandler mHandler;
	private EventHandler.ListAdapter mTable;

	private SharedPreferences mSettings;
	private boolean mReturnIntent = false;
	private boolean mUseBackKey = true;
	private String mSelectedListItem; // item from context menu
	private TextView mPathLabel;
	private ImageView mNavigationBarUpDownArrow;
	private View navBar, mDropdownNavigation, mCurrentPatScroller, upLevel,
			arrow;
	private String sdDir = Util.getSdDirectory();
	private String mCurrentPath;
	private ArrayList<String> currentItems = new ArrayList<String>();
	private DbHelper dbhelper;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		getWindow().requestFeature(Window.FEATURE_NO_TITLE);
		setContentView(R.layout.listview);
		navBar = (View) this.findViewById(R.id.navigation_bar);
		mDropdownNavigation = this.findViewById(R.id.dropdown_navigation);
		mNavigationBarUpDownArrow = (ImageView) this
				.findViewById(R.id.path_pane_arrow);
		mCurrentPatScroller = findViewById(R.id.current_path_pane);
		mCurrentPatScroller.setOnClickListener(currentPathPaneClick);

		upLevel = this.findViewById(R.id.path_pane_up_level);
		arrow = findViewById(R.id.path_pane_arrow);
		dbhelper = new DbHelper(this);
		upLevel.setOnClickListener(goBackClick);
		if (Environment.getExternalStorageState().equals(
				Environment.MEDIA_MOUNTED)) {

			mFileMag = new FileManager();
			mFileMag.setShowHiddenFiles(false);
			mFileMag.setSortType(1);

			if (savedInstanceState != null)
				mHandler = new EventHandler(FileActivity.this, mFileMag,
						savedInstanceState.getString("location"));
			else
				mHandler = new EventHandler(FileActivity.this, mFileMag);

			mTable = mHandler.new ListAdapter();

			mPathLabel = (TextView) this.findViewById(R.id.current_path_view);

			mHandler.setListAdapter(mTable);
			setListAdapter(mTable);

			/* register context menu for our list view */
			registerForContextMenu(getListView());

			setmCurrentPath(this.sdDir);

			mPathLabel.setText(R.string.sd_folder);

		} else {
			navBar.setVisibility(View.GONE);
			this.findViewById(R.id.sd_not_available_page).setVisibility(
					View.VISIBLE);
		}

	}

	private void showDropdownNavigation(boolean show) {
		mDropdownNavigation.setVisibility(show ? View.VISIBLE : View.GONE);
		mNavigationBarUpDownArrow.setImageResource(mDropdownNavigation
				.getVisibility() == View.VISIBLE ? R.drawable.arrow_up
				: R.drawable.arrow_down);
	}

	public String getDisplayPath(String path) {
		Log.d("OpenFile", path);
		if (path.startsWith(this.sdDir)) {
			return getString(R.string.sd_folder)
					+ path.substring(this.sdDir.length());
		} else {
			return path;
		}
	}

	public String getRealPath(String displayPath) {
		final String perfixName = getString(R.string.sd_folder);
		if (displayPath.startsWith(perfixName)) {
			return this.sdDir + displayPath.substring(perfixName.length());
		} else {
			return displayPath;
		}
	}

	private View.OnClickListener currentPathPaneClick = new View.OnClickListener() {

		@Override
		public void onClick(View v) {
			onNavigationBarClick();

		}

	};

	private OnClickListener navigationClick = new OnClickListener() {

		@Override
		public void onClick(View v) {
			String path = (String) v.getTag();
			assert (path != null);
			showDropdownNavigation(false);

			if (path.isEmpty()) {
				setmCurrentPath(sdDir);
			} else {
				setmCurrentPath(path);
			}
			refreshFileList();
		}

	};

	public boolean onOperationUpLevel() {
		showDropdownNavigation(false);

		if (!this.sdDir.equals(getmCurrentPath())) {
			setmCurrentPath(new File(getmCurrentPath()).getParent());
			refreshFileList();
			return true;
		}

		return false;
	}

	private OnClickListener goBackClick = new OnClickListener() {

		@Override
		public void onClick(View v) {
			onOperationUpLevel();
		}
	};

	public void refreshFileList() {
		Log.d("OpenFile", "refreshFileList " + getmCurrentPath());
		updateNavigationPane();
		currentItems = mFileMag.getNextDir(getmCurrentPath(), true);
		mHandler.updateDirectory(currentItems);
	}

	private void updateNavigationPane() {

		upLevel.setVisibility(this.sdDir.equals(getmCurrentPath()) ? View.INVISIBLE
				: View.VISIBLE);

		arrow.setVisibility(this.sdDir.equals(getmCurrentPath()) ? View.GONE
				: View.VISIBLE);

		mPathLabel.setText(getDisplayPath(getmCurrentPath()));
	}

	protected void onNavigationBarClick() {
		Log.d("OpenFile", "onNavigateClick");
		if (mDropdownNavigation.getVisibility() == View.VISIBLE) {
			showDropdownNavigation(false);
		} else {
			LinearLayout list = (LinearLayout) mDropdownNavigation
					.findViewById(R.id.dropdown_navigation_list);
			list.removeAllViews();
			int pos = 0;
			String displayPath = getDisplayPath(getmCurrentPath());
			boolean root = true;
			int left = 0;
			while (pos != -1) {
				int end = displayPath.indexOf("/", pos);
				if (end == -1)
					break;

				View listItem = LayoutInflater.from(this).inflate(
						R.layout.dropdown_item, null);

				View listContent = listItem.findViewById(R.id.list_item);
				listContent.setPadding(left, 0, 0, 0);
				left += 20;
				ImageView img = (ImageView) listItem
						.findViewById(R.id.item_icon);

				img.setImageResource(root ? R.drawable.dropdown_icon_root
						: R.drawable.dropdown_icon_folder);
				root = false;

				TextView text = (TextView) listItem
						.findViewById(R.id.path_name);
				String substring = displayPath.substring(pos, end);
				text.setText(substring);

				listItem.setOnClickListener(navigationClick);
				listItem.setTag(getRealPath(displayPath.substring(0, end)));
				pos = end + 1;
				list.addView(listItem);
			}
			if (list.getChildCount() > 0)
				showDropdownNavigation(true);

		}
	}

	@Override
	protected void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);

		outState.putString("location", mFileMag.getCurrentDir());
	}

	/*
	 * (non Java-Doc) Returns the file that was selected to the intent that
	 * called this activity. usually from the caller is another application.
	 */
	private void returnIntentResults(File data) {
		mReturnIntent = false;

		Intent ret = new Intent();
		ret.setData(Uri.fromFile(data));
		setResult(RESULT_OK, ret);

		finish();
	}

	/**
	 * To add more functionality and let the user interact with more file types,
	 * this is the function to add the ability.
	 * 
	 * (note): this method can be done more efficiently
	 */
	@Override
	public void onListItemClick(ListView parent, View view, int position,
			long id) {
		final String item = mHandler.getData(position);

		File file = new File(mFileMag.getCurrentDir() + "/" + item);
		String item_ext = null;
		try {
			item_ext = item.substring(item.lastIndexOf("."), item.length());

		} catch (IndexOutOfBoundsException e) {
			item_ext = "";
		}
		/*
		 * If the user has multi-select on, we just need to record the file not
		 * make an intent for it.
		 */

		if (file.isDirectory()) {
			if (file.canRead()) {
				currentItems = mFileMag.getNextDir(item, false);
				mHandler.updateDirectory(currentItems);

				setmCurrentPath(mFileMag.getCurrentDir());

				mPathLabel.setText(getmCurrentPath());
				updateNavigationPane();
				/*
				 * set back button switch to true (this will be better
				 * implemented later)
				 */
				if (!mUseBackKey)
					mUseBackKey = true;

			} else {
				Toast.makeText(this, R.string.no_permission, Toast.LENGTH_SHORT)
						.show();
			}
		}
		/* generic intent */
		else {
			if (file.exists()) {
				if (mReturnIntent) {
					returnIntentResults(file);

				} else if (item_ext.equalsIgnoreCase(".mp3")
						|| item_ext.equalsIgnoreCase(".m4a")
						|| item_ext.equalsIgnoreCase(".mp4")) {

					if (mReturnIntent) {
						returnIntentResults(file);
					} else {
						Intent i = new Intent();
						i.setAction(android.content.Intent.ACTION_VIEW);
						i.setDataAndType(Uri.fromFile(file), "audio/*");
						startActivity(i);
					}
				}

				/* photo file selected */
				else if (item_ext.equalsIgnoreCase(".jpeg")
						|| item_ext.equalsIgnoreCase(".jpg")
						|| item_ext.equalsIgnoreCase(".png")
						|| item_ext.equalsIgnoreCase(".gif")
						|| item_ext.equalsIgnoreCase(".tiff")) {

					if (file.exists()) {
						if (mReturnIntent) {
							returnIntentResults(file);

						} else {
							Intent picIntent = new Intent();
							picIntent
									.setAction(android.content.Intent.ACTION_VIEW);
							picIntent.setDataAndType(Uri.fromFile(file),
									"image/*");
							startActivity(picIntent);
						}
					}
				}

				/* video file selected--add more video formats */
				else if (item_ext.equalsIgnoreCase(".m4v")
						|| item_ext.equalsIgnoreCase(".3gp")
						|| item_ext.equalsIgnoreCase(".wmv")
						|| item_ext.equalsIgnoreCase(".mp4")
						|| item_ext.equalsIgnoreCase(".ogg")
						|| item_ext.equalsIgnoreCase(".wav")) {

					if (file.exists()) {
						if (mReturnIntent) {
							returnIntentResults(file);

						} else {
							Intent movieIntent = new Intent();
							movieIntent
									.setAction(android.content.Intent.ACTION_VIEW);
							movieIntent.setDataAndType(Uri.fromFile(file),
									"video/*");
							startActivity(movieIntent);
						}
					}
				}

				/* gzip files, this will be implemented later */
				else if (item_ext.equalsIgnoreCase(".gzip")
						|| item_ext.equalsIgnoreCase(".gz")) {

					if (mReturnIntent) {
						returnIntentResults(file);

					} else {
						// TODO:
					}
				}

				/* pdf file selected */
				else if (item_ext.equalsIgnoreCase(".pdf")) {

					if (file.exists()) {
						if (mReturnIntent) {
							returnIntentResults(file);

						} else {
							Intent pdfIntent = new Intent();
							pdfIntent
									.setAction(android.content.Intent.ACTION_VIEW);
							pdfIntent.setDataAndType(Uri.fromFile(file),
									"application/pdf");

							try {
								startActivity(pdfIntent);
							} catch (ActivityNotFoundException e) {
								Toast.makeText(this,
										"Sorry, couldn't find a pdf viewer",
										Toast.LENGTH_SHORT).show();
							}
						}
					}
				}

				/* Android application file */
				else if (item_ext.equalsIgnoreCase(".apk")) {

					if (file.exists()) {
						if (mReturnIntent) {
							returnIntentResults(file);

						} else {
							Intent apkIntent = new Intent();
							apkIntent
									.setAction(android.content.Intent.ACTION_VIEW);
							apkIntent.setDataAndType(Uri.fromFile(file),
									"application/vnd.android.package-archive");
							startActivity(apkIntent);
						}
					}
				}
				/* generic intent */
				else {
					if (file.exists()) {
						if (mReturnIntent) {
							returnIntentResults(file);

						} else {

							fileHandle(file.getAbsolutePath());

						}
					}
				}
			}

		}
	}

	@Override
	public void onCreateContextMenu(ContextMenu menu, View v,
			ContextMenuInfo info) {
		super.onCreateContextMenu(menu, v, info);
		AdapterView.AdapterContextMenuInfo menuInfo = (AdapterContextMenuInfo) info;

		if (!mFileMag.isDirectory(mHandler.getData(menuInfo.position))) {
			menu.add(0, MENU_OPEN, 0, "打开");
			menu.add(0, MENU_ADDFAV, 1, "收藏");
		}
	}

	@Override
	public boolean onContextItemSelected(MenuItem item) {
		AdapterView.AdapterContextMenuInfo menuInfo = (AdapterContextMenuInfo) item
				.getMenuInfo();
		final String p = mFileMag.getCurrentDir() + "/"
				+ mHandler.getData(menuInfo.position);

		switch (item.getItemId()) {
		case MENU_OPEN:
			fileHandle(p);
			break;
		case MENU_ADDFAV:
			addProject(p);
			break;
		default:
			break;
		}
		return false;
	}

	private void fileHandle(String path) {
		Log.d("FileActivity", path);
		Intent resultIntent = new Intent(FileActivity.this,
				HTMLViewerPlusPlus.class);
		resultIntent.setData(Uri.parse("file://" + path));
		this.startActivity(resultIntent);

	}

	/*
	 * (non-Javadoc) This will check if the user is at root directory. If so, if
	 * they press back again, it will close the application.
	 * 
	 * @see android.app.Activity#onKeyDown(int, android.view.KeyEvent)
	 */
	@Override
	public boolean onKeyDown(int KeyCode, KeyEvent event) {
		// setmCurrentPath(mFileMag.getCurrentDir());

		if (event.getKeyCode() == KeyEvent.KEYCODE_BACK && mUseBackKey
				&& !getmCurrentPath().equals(this.sdDir)) {

			// mHandler.updateDirectory(mFileMag.getPreviousDir());
			// setmCurrentPath(mFileMag.getCurrentDir());
			// mPathLabel.setText(getmCurrentPath());
			onOperationUpLevel();

			return true;

		} else if (event.getKeyCode() == KeyEvent.KEYCODE_BACK && mUseBackKey
				&& getmCurrentPath().equals(this.sdDir)) {
			Toast.makeText(FileActivity.this, R.string.quit_toast,
					Toast.LENGTH_SHORT).show();

			mUseBackKey = false;
			setmCurrentPath(mFileMag.getCurrentDir());
			mPathLabel.setText(getmCurrentPath());

			return false;

		} else if (event.getKeyCode() == KeyEvent.KEYCODE_BACK && !mUseBackKey
				&& getmCurrentPath().equals(this.sdDir)) {
			finish();

			return false;
		}

		if (event.getKeyCode() == KeyEvent.KEYCODE_MENU) {
			this.openOptionsMenu();
			return true;
		}
		return false;
	}

	public String getmCurrentPath() {
		return mCurrentPath;
	}

	public void setmCurrentPath(String mCurrentPath) {
		this.mCurrentPath = mCurrentPath;
	}

	private void addProject(String title) {
		SimpleDateFormat formatter = new SimpleDateFormat(
				"yyyy年MM月dd日   HH:mm:ss     ");
		Date curDate = new Date(System.currentTimeMillis());// 获取当前时间
		String str = formatter.format(curDate);
		dbhelper.insert(title, str, "", 1);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {

		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.file_menu, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case R.id.refresh_menu:
			refreshFileList();
			break;
		}
		return true;
	}
}
