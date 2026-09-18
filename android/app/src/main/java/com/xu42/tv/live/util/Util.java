package com.xu42.tv.live.util;

import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.webkit.ValueCallback;
import android.webkit.WebView;

/**
 * WebView 相关的小工具。
 * 应用本地化之后只剩「在 UI 线程执行一段 JS」和「判断 CPU 位数」两件事。
 */
public class Util {
    private static final String TAG = "Util";
    public static final Handler mainHandler = new Handler(Looper.getMainLooper());

    /** 在 UI 线程执行 JS（调用方通常不在主线程） */
    public static void evalOnUi(WebView webView, String javascript) {
        mainHandler.post(() -> {
            LogUtil.i(TAG, "evalOnUi " + javascript);
            eval(webView, javascript);
        });
    }

    public static void eval(WebView webView, String javascript) {
        eval(webView, javascript, null);
    }

    public static void eval(WebView webView, String javascript, ValueCallback<String> valueCallback) {
        if (null == webView) {
            return;
        }
        webView.evaluateJavascript(javascript, valueCallback);
    }

    private static Boolean is64 = null;

    public static boolean is64() {
        if (null != is64) {
            return is64;
        }
        String[] supported64BitAbis = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            supported64BitAbis = Build.SUPPORTED_64_BIT_ABIS;
        }
        is64 = (null != supported64BitAbis && supported64BitAbis.length > 0);
        return is64;
    }
}
