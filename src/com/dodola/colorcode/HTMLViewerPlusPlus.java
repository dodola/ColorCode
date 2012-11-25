package com.dodola.colorcode;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.net.URI;
import java.util.List;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.app.SearchManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.ViewGroup.LayoutParams;
import android.view.Window;
import android.view.inputmethod.InputMethodManager;
import android.webkit.CookieSyncManager;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.EditText;
import android.widget.RelativeLayout;
import android.widget.Toast;

/**
 * @author Cosme Zamudio - Android SDK Examples I Grabbed this class from the
 *         SDK Examples, i actually made a lot of changes, so it doesnt look
 *         like the original one. it has the prettify functionality inside the
 *         class, it may look ugly.. but it works fine and its well organized
 *         (thats what i think) Note: Im Leaving the original comments, they
 *         might come in handy for other users reading the code
 * 
 *         Wraps a WebView widget within an Activity. When launched, it uses the
 *         URI from the intent as the URL to load into the WebView. It supports
 *         all URLs schemes that a standard WebView supports, as well as loading
 *         the top level markup using the file scheme. The WebView default
 *         settings are used with the exception of normal layout is set. This
 *         activity shows a loading progress bar in the window title and sets
 *         the window title to the title of the content.
 * 
 */
public class HTMLViewerPlusPlus extends WithHeaderActivity {

	/**
	 * The WebView that is placed in this Activity
	 */
	private WebView mWebView;

	/**
	 * As the file content is loaded completely into RAM first, set a limitation
	 * on the file size so we don't use too much RAM. If someone wants to load
	 * content that is larger than this, then a content provider should be used.
	 */
	static final int MAXFILESIZE = 16172;
	static final String LOGTAG = "HTMLViewerPlusPlus";
	private static final int PICK_REQUEST_CODE = 0;
	private boolean isSearching = false;
	private Resources resources;
	private Uri currentUri;
	private SharedPreferences prefs;
	private String currentTheme;
	private String currentMimeType;
	final String encoding = "utf-8";
	public static final String PREFS_NAME = "options";

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.main_menu, menu);
		return true;

	}

	/**
	 * Modify the menus according to the searching mode and matches
	 */
	@Override
	public boolean onPrepareOptionsMenu(Menu menu) {
		if (isSearching) {
			menu.findItem(R.id.next_menu).setEnabled(true);
			menu.findItem(R.id.clear_menu).setEnabled(true);
		} else {
			menu.findItem(R.id.next_menu).setEnabled(false);
			menu.findItem(R.id.clear_menu).setEnabled(false);
		}

		return super.onPrepareOptionsMenu(menu);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case R.id.search_menu:
			showSearchDialog();
			break;
		case R.id.next_menu:
			nextSearch();
			break;
		case R.id.clear_menu:
			clearSearch();
			break;
		case R.id.select_menu:
			selectAndCopyText();
			break;
		case R.id.option_menu:
			// 当我们点击 Settings 菜单的时候就会跳转到我们的 首选项视图，也就是我们的
			// FlightPreferenceActivity
			Intent intent = new Intent().setClass(this,
					CodeColorPreferenceActivity.class);
			// 因为我们要接收上一个Activity 就是我们的首选项视图 返回的数据，所以这里用
			// startActivityForResult()方法启动我们的首选项视图
			// 参数一：我们要跳转到哪里
			// 参数二：回传码
			this.startActivityForResult(intent, 0);

			break;
		case R.id.about_menu:
			showAboutDialog();
			break;
		case R.id.quit_menu:
			quitApplication();
			break;
		}
		return false;

	}

	/**
	 * Added to avoid refreshing the page on orientation change saw it on
	 * stackoverflow, dont remember wich article
	 */
	@Override
	public void onConfigurationChanged(Configuration newConfig) {
		super.onConfigurationChanged(newConfig);
	}

	/**
	 * Gets the result from the file picker activity thats the only intent im
	 * actually calling (and expecting results from) right now
	 */
	protected void onActivityResult(int requestCode, int resultCode,
			Intent intent) {
		if (requestCode == PICK_REQUEST_CODE) {
			if (resultCode == RESULT_OK) {
				Uri uri = intent.getData();
				if (uri != null) {
					String path = uri.toString();
					if (path.toLowerCase().startsWith("file://")) {
						File f = new File(URI.create(path));
						setHeaderTitle(f.getName());
						path = f.getAbsolutePath();
						loadFile(Uri.parse(path), "text/html");

					}
				}
			} else {
				setOptionText();
			}
		}
	}

	private void setOptionText() {

		currentTheme = prefs.getString(
				resources.getString(R.string.selected_theme),
				resources.getString(R.string.default_theme));
		loadFile(currentUri, currentMimeType);
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		CookieSyncManager.createInstance(this);
		requestWindowFeature(Window.FEATURE_PROGRESS);
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		setContentView(R.layout.code_view);
		initHeader(WithHeaderActivity.HEADER_STYLE_FILE);
		mWebView = (WebView) findViewById(R.id.codeview);

		mWebView.setWebViewClient(new WebChrome2());

		prefs = PreferenceManager
				.getDefaultSharedPreferences(getApplicationContext());
		resources = this.getResources();
		currentTheme = prefs.getString(
				resources.getString(R.string.selected_theme),
				resources.getString(R.string.default_theme));

		// Configure the webview
		WebSettings s = mWebView.getSettings();
		s.setLayoutAlgorithm(WebSettings.LayoutAlgorithm.NORMAL);
		s.setUseWideViewPort(false);
		s.setAllowFileAccess(true);
		s.setBuiltInZoomControls(true);
		s.setLightTouchEnabled(true);
		s.setLoadsImagesAutomatically(true);
		s.setPluginsEnabled(false);
		s.setSupportZoom(true);
		s.setSupportMultipleWindows(true);
		s.setJavaScriptEnabled(true);

		// Restore a webview if we are meant to restore
		if (savedInstanceState != null) {
			mWebView.restoreState(savedInstanceState);
		} else {
			Intent intent = getIntent();
			if (intent.getData() != null) {
				currentUri = intent.getData();
				if ("file".equals(currentUri.getScheme())) {
					setHeaderTitle(currentUri.getPath());
					currentMimeType = intent.getType();
					loadFile(currentUri, currentMimeType);
				} else {
					mWebView.loadUrl(intent.getData().toString());
				}
			} else {

				mWebView.loadUrl("file:///android_asset/index.html");
				setHeaderTitle("Color Code");
			}
		}

	}

	/**
	 * Get a Document Handler Depending on the filename extension
	 * 
	 * @param filename
	 *            The filename to retrieve the handler from
	 * @return The new document handler
	 */
	public DocumentHandler getHandlerByExtension(String filename) {
		DocumentHandler handler = null;

		String prefix = filename.substring(filename.lastIndexOf(".") + 1);
		handler = new DocumentHandler(prefix);
		Log.v(LOGTAG, " Handler: " + filename);
		return handler;
	}

	/**
	 * Call the intent to open files
	 */
	public void openFileIntent() {
		Intent fileIntent = new Intent(HTMLViewerPlusPlus.this,
				FileActivity.class);
		startActivityForResult(fileIntent, PICK_REQUEST_CODE);

	}

	/**
	 * Function found in the android sdk examples, checks if an action has an
	 * intent associated going to be used to check for other filebrowser intents
	 * 
	 * @param context
	 *            usually this, the context we are working on
	 * @param action
	 *            the action we want to check the intents
	 * @return true if there is at least one intent for that action
	 */
	private boolean isIntentAvailable(Context context, String action) {
		final PackageManager packageManager = context.getPackageManager();
		final Intent intent = new Intent(action);
		List<ResolveInfo> list = packageManager.queryIntentServices(intent, 0);

		return list.size() > 0;
	}

	/***
	 * Closes the application
	 */
	public void quitApplication() {
		Intent startMain = new Intent(Intent.ACTION_MAIN);
		startMain.addCategory(Intent.CATEGORY_HOME);
		startMain.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		startActivity(startMain);
		System.exit(0);
	}

	/**
	 * Loads the home screen
	 */
	public void loadHomeScreen() {
		mWebView.getSettings().setUseWideViewPort(false);
		mWebView.loadUrl("file:///android_asset/index.html");
	}

	/**
	 * Loads the help screen
	 */
	public void loadHelpScreen() {
		mWebView.getSettings().setUseWideViewPort(false);
		mWebView.loadUrl("file:///android_asset/index.html"); // Need to change
																// for the help
																// screen
	}

	/**
	 * Select Text in the webview and automatically sends the selected text to
	 * the clipboard
	 */
	public void selectAndCopyText() {
		// try {
		// KeyEvent shiftPressEvent = new KeyEvent(0, 0, KeyEvent.ACTION_DOWN,
		// KeyEvent.KEYCODE_SHIFT_LEFT, 0, 0);
		// shiftPressEvent.dispatch(mWebView);
		// } catch (Exception e) {
		// throw new AssertionError(e);
		// }
		mWebView.emulateShiftHeld();
	}

	/**
	 * Clear all the matches in the search
	 */
	public void clearSearch() {
		isSearching = false;
		mWebView.clearMatches();
	}

	/**
	 * Find Next Match in Search
	 */
	public void nextSearch() {
		mWebView.findNext(true);
		highlight();
	}

	private void highlight() {
		try {
			Method m = WebView.class.getMethod("setFindIsUp", Boolean.TYPE);
			m.invoke(mWebView, true);
		} catch (Throwable ignored) {
		}
	}

	/**
	 * Search inside the webview
	 */
	public void showSearchDialog() {
		AlertDialog.Builder alert = new AlertDialog.Builder(this);

		alert.setTitle(R.string.search_title);
		alert.setMessage(R.string.search_text);

		// Set an EditText view to get user input
		final EditText inputText = new EditText(this);

		alert.setView(inputText);

		alert.setPositiveButton("确定", new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int whichButton) {
				String value = inputText.getText().toString();
				isSearching = true;
				mWebView.findAll(value);
				highlight();
			}
		});

		alert.setNegativeButton("取消", new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int whichButton) {

			}
		});

		alert.show();

	}

	/**
	 * Load the HTML file into the webview by converting it to a data: URL. If
	 * there were any relative URLs, then they will fail as the webview does not
	 * allow access to the file:/// scheme for accessing the local file system,
	 * 
	 * Note: Before actually loading the info in webview, i add the prettify
	 * libraries to do the syntax highlight also i organize the data where it
	 * has to be. works fine now but it needs some work
	 * 
	 * @param uri
	 *            file URI pointing to the content to be loaded
	 * @param mimeType
	 *            mimetype provided
	 */
	private void loadFile(Uri uri, String mimeType) {
		String path = uri.getPath();
		DocumentHandler handler = getHandlerByExtension(path);

		File f = new File(path);
		final long length = f.length();
		if (!f.exists()) {
			Log.e(LOGTAG, "File doesnt exists: " + path);
			return;
		}

		if (handler == null) {
			Log.e(LOGTAG, "Filetype not supported");
			Toast.makeText(HTMLViewerPlusPlus.this, "Filetype not supported",
					2000);
			return;
		}

		// typecast to int is safe as long as MAXFILESIZE < MAXINT
		byte[] array = new byte[(int) length];

		try {
			InputStream is = new FileInputStream(f);
			is.read(array);
			is.close();
		} catch (FileNotFoundException ex) {
			// Checked for file existance already, so this should not happen
			Log.e(LOGTAG, "Failed to access file: " + path, ex);
			return;
		} catch (IOException ex) {
			// read or close failed
			Log.e(LOGTAG, "Failed to access file: " + path, ex);
			return;
		}
		String contentString = "";
		setTitle("Android ColorCode - " + path);
		contentString += "<html><head><title>" + path + "</title>";

		contentString += "<script src='file:///android_asset/sh_main.min.js' type='text/javascript'></script> ";
		contentString += handler.getFileScriptFiles();
		contentString += "<link href='file:///android_asset/css/sh_"
				+ currentTheme
				+ ".min.css' rel='stylesheet' type='text/css'/> ";
		contentString += "</head><body onload='sh_highlightDocument();'><pre class='"
				+ handler.getFilePrettifyClass() + "'>";
		String sourceString = new String(array);

		contentString += handler.getFileFormattedString(sourceString);
		contentString += "</pre> </html> ";
		mWebView.getSettings().setUseWideViewPort(true);
		mWebView.loadDataWithBaseURL("file:///android_asset/", contentString,
				handler.getFileMimeType(), encoding, "");
		Log.v(LOGTAG, "File Loaded: " + path);

	}

	@Override
	protected void onResume() {
		super.onResume();
		CookieSyncManager.getInstance().startSync();
	}

	@Override
	protected void onSaveInstanceState(Bundle outState) {

		mWebView.saveState(outState);
	}

	@Override
	protected void onStop() {
		super.onStop();

		CookieSyncManager.getInstance().stopSync();
		mWebView.stopLoading();
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		mWebView.destroy();
	}

	@Override
	public void setRequestedOrientation(int requestedOrientation) {
		super.setRequestedOrientation(requestedOrientation);
	}

	@Override
	public int getRequestedOrientation() {
		return super.getRequestedOrientation();
	}

	protected void showAboutDialog() {
		AlertDialog.Builder builder = new Builder(HTMLViewerPlusPlus.this);
		builder.setMessage(resources.getString(R.string.about_text));

		builder.setTitle(resources.getString(R.string.about_title));

		builder.setNegativeButton("确认", new OnClickListener() {

			@Override
			public void onClick(DialogInterface dialog, int which) {

			}
		});

		builder.create().show();
	}

}
