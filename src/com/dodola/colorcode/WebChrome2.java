package com.dodola.colorcode;

import android.app.ProgressDialog;
import android.graphics.Bitmap;
import android.webkit.WebView;
import android.webkit.WebViewClient;

class WebChrome2 extends WebViewClient {
	 private ProgressDialog mProgressDialog;
    @Override
    public boolean shouldOverrideUrlLoading(WebView view, String url) {
   	
    	return false;
    }
    
    @Override
    public void onPageFinished(WebView view, String url) {
    	if (mProgressDialog!=null) mProgressDialog.dismiss();
    	mProgressDialog = null;
    	
    	super.onPageFinished(view, url);
    }
    
    @Override
    public void onPageStarted(WebView view, String url, Bitmap favicon) {
    	mProgressDialog = ProgressDialog.show(view.getContext(),"请等待...", "正在处理文件...", true);
    	super.onPageStarted(view, url, favicon);
    }
 }   