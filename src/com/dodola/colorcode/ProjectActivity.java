package com.dodola.colorcode;

import android.app.TabActivity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.TabHost;

public class ProjectActivity extends TabActivity {
	/**
	 * @see android.app.Activity#onCreate(Bundle)
	 */
	TabHost tabhost;
	TabHost.TabSpec favTab, hgTab, webFileTab, webTarTab;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		this.setContentView(R.layout.project_layout);
		tabhost = getTabHost();

		Intent intent1 = new Intent(this, ProjectList.class);
		Bundle bundle = new Bundle();
		bundle.putInt("showtype", 1);
		intent1.putExtras(bundle);
		favTab = tabhost.newTabSpec("fav").setIndicator("fav")
				.setContent(intent1);
		tabhost.addTab(favTab);

		// Intent intent2 = new Intent(this, ProjectList.class);
		// bundle = new Bundle();
		// bundle.putInt("showtype", 2);
		// intent2.putExtras(bundle);
		// hgTab =
		// tabhost.newTabSpec("hg").setIndicator("Hg").setContent(intent2);
		// tabhost.addTab(hgTab);
		//
		// Intent intent3 = new Intent(this, ProjectList.class);
		// bundle = new Bundle();
		// bundle.putInt("showtype", 3);
		// intent3.putExtras(bundle);
		// webFileTab = tabhost.newTabSpec("WebFile").setIndicator("WebFile")
		// .setContent(intent3);
		// tabhost.addTab(webFileTab);
		//
		// Intent intent4 = new Intent(this, ProjectList.class);
		// bundle = new Bundle();
		// bundle.putInt("showtype", 4);
		// intent4.putExtras(bundle);
		// webTarTab = tabhost.newTabSpec("Traball").setIndicator("Traball")
		// .setContent(intent4);
		// tabhost.addTab(webTarTab);
	}
}
