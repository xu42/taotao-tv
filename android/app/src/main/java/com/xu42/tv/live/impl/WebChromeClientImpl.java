package com.xu42.tv.live.impl;

import android.view.View;
import android.webkit.JsPromptResult;
import android.webkit.JsResult;
import android.webkit.WebChromeClient;
import android.webkit.WebView;

import com.xu42.tv.live.util.LogUtil;

/**
 * 基于系统内核（android.webkit）的 WebChromeClient 实现。
 * 参考：https://developer.android.com/reference/android/webkit/WebChromeClient
 */
public class WebChromeClientImpl extends WebChromeClient {
    private static String TAG = "WebChromeClient";

    @Override
    public void onProgressChanged(WebView view, int newProgress) {
        LogUtil.i(TAG, "onProgressChanged, newProgress:" + newProgress);
    }

    @Override
    public boolean onJsAlert(WebView webView, String url, String message, JsResult result) {
        LogUtil.i(TAG, "onJsAlert " + url);
        return true;
    }

    @Override
    public boolean onJsConfirm(WebView webView, String url, String message, JsResult result) {
        LogUtil.i(TAG, "onJsConfirm " + url);
        return true;
    }

    @Override
    public boolean onJsBeforeUnload(WebView webView, String url, String message, JsResult result) {
        LogUtil.i(TAG, "onJsBeforeUnload " + url);
        return true;
    }

    @Override
    public boolean onJsPrompt(WebView webView, String url, String message, String defaultValue,
                              JsPromptResult result) {
        LogUtil.i(TAG, "onJsPrompt");
        return true;
    }

    @Override
    public void onShowCustomView(View view, CustomViewCallback callback) {
        LogUtil.i(TAG, "onShowCustomView");
    }
}
