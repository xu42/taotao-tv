package com.xu42.tv.live;

import static com.xu42.tv.live.util.PermissionUtil.REQUEST_EXTERNAL_STORAGE;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.Toast;

import androidx.databinding.ViewDataBinding;

import java.util.HashMap;
import java.util.Map;

import com.xu42.tv.live.impl.WebViewClientImpl;
import com.xu42.tv.live.util.LogUtil;
import com.xu42.tv.live.utils.ToastUtils;

/**
 * Activity 基类：负责创建并配置系统 WebView（android.webkit）。
 */
public abstract class BaseActivity extends Activity {
    protected String TAG = "BaseActivity";
    /** 伪装成桌面 Chrome，部分源站会按 UA 返回不同（可用的）播放地址 */
    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36";

    private boolean isWebViewDestroyed = false;
    protected WebView mWebView;
    protected Context thisContext;
    protected ViewDataBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        requestWindowFeature(Window.FEATURE_NO_TITLE);//隐藏标题栏
        super.onCreate(savedInstanceState);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM);
        // 禁用虚拟环境下的无障碍
        getWindow().getDecorView().setImportantForAccessibility(
                View.IMPORTANT_FOR_ACCESSIBILITY_NO
        );
        // 禁用内容捕获以规避部分机型系统 WebView 在初始绘制时的崩溃
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            try {
                getWindow().getDecorView().setImportantForContentCapture(
                        View.IMPORTANT_FOR_CONTENT_CAPTURE_NO_EXCLUDE_DESCENDANTS
                );
            } catch (NoSuchMethodError | NoClassDefFoundError ignored) {
                // 某些定制 ROM 在 Q 也可能缺此 API，安全忽略
            }
        }
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        mWebView = new WebView(this);
        createInit();
        thisContext = this;
        // 或者如果使用旧的 ActionBar
        if (getActionBar() != null) {
            getActionBar().hide();
        }
    }

    protected abstract void createInit();

    protected abstract void initWebChromeClient();

    /**
     * 自定义初始化WebView设置，此处为默认 BaseWebViewActivity 初始化
     * 可通过继承该 Activity Override 该方法做自己的实现
     */
    protected void initWebView() {
        WebSettings webSetting = mWebView.getSettings();
        // 针对 Android 10+ 禁用内容捕获，避免系统 ContentCaptureManager 相关崩溃
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            try {
                mWebView.setImportantForContentCapture(
                        View.IMPORTANT_FOR_CONTENT_CAPTURE_NO_EXCLUDE_DESCENDANTS
                );
            } catch (NoSuchMethodError | NoClassDefFoundError ignored) {
                // 某些定制 ROM 在 Q 也可能缺此 API，安全忽略
            }
        }
        webSetting.setJavaScriptEnabled(true);
        webSetting.setAllowFileAccess(true);
        webSetting.setDatabaseEnabled(true);
        webSetting.setDomStorageEnabled(true);
        webSetting.setNeedInitialFocus(false);
        // 禁用缩放
        webSetting.setSupportZoom(false);
        webSetting.setBuiltInZoomControls(false);
        webSetting.setDisplayZoomControls(false);
        //自适应屏幕
        webSetting.setUseWideViewPort(true);
        webSetting.setLoadWithOverviewMode(true);
        // 允许 https 页面内加载 http 资源：直播/影视源里有大量 http 流
        webSetting.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        //自动播放
        webSetting.setMediaPlaybackRequiresUserGesture(false);
        webSetting.setUserAgentString(USER_AGENT);
        webSetting.setCacheMode(WebSettings.LOAD_DEFAULT);
        webSetting.setJavaScriptCanOpenWindowsAutomatically(false);
        webSetting.setGeolocationEnabled(false);
        initWebViewClient();
        initWebChromeClient();
        //禁止上下左右滚动(不显示滚动条)
        mWebView.setScrollContainer(false);
        mWebView.setVerticalScrollBarEnabled(false);
        mWebView.setHorizontalScrollBarEnabled(false);
        //远程调试
        WebView.setWebContentsDebuggingEnabled(true);
        mWebView.addJavascriptInterface(getJsInterface(), "_api");
    }

    protected abstract Object getJsInterface();

    protected void initWebViewClient() {
        mWebView.setWebViewClient(new WebViewClientImpl(getBaseContext(), mWebView, 0));
    }

    /* Don't care about the Base UI Logic below ^_^ */
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_EXTERNAL_STORAGE) {
            if (grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // 权限被授予，可以进行文件操作
            } else {
                // 权限被拒绝，需要进行一些UX处理
            }
            LogUtil.i(TAG, "onRequestPermissionsResult: initWebView");
            if (null != mWebView) {
                initWebView();
            }
        }
    }

    //key event
    private static Instrumentation inst = new Instrumentation();

    private static Map<String, Integer> keyCodeMap = new HashMap<>();

    static {
        keyCodeMap.put("SPACE", 62);
        keyCodeMap.put("F", 34);
    }

    protected void keyCodeAllByCode(String keyCode) {
        Integer keyCodeNum = keyCodeMap.get(keyCode);
        if (null == keyCodeNum) {
            return;
        }
        LogUtil.i("onKeyEvent", "keyCodeStr " + keyCode);
        keyEventAll(keyCodeNum);
    }

    protected void keyEventAll(final int keyCode) {
        new Thread() {
            public void run() {
                try {
                    LogUtil.i("onKeyEvent", "onKeyEvent" + keyCode);
                    inst.sendKeySync(new KeyEvent(KeyEvent.ACTION_DOWN, keyCode));
                    inst.sendKeySync(new KeyEvent(KeyEvent.ACTION_UP, keyCode));
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }.start();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        destroyWebView();
    }

    /**
     * 安全地销毁 WebView
     */
    private void destroyWebView() {
        if (mWebView != null && !isWebViewDestroyed) {
            // 标记 WebView 已销毁，防止重复操作
            isWebViewDestroyed = true;

            try {
                // 移除所有 JS 接口
                mWebView.removeJavascriptInterface("_api");

                // 加载空白页面
                mWebView.loadUrl("about:blank");

                // 清除历史
                mWebView.clearHistory();

                // 从父视图中移除
                ViewParent parent = mWebView.getParent();
                if (parent instanceof ViewGroup) {
                    ((ViewGroup) parent).removeView(mWebView);
                }

                // 停止加载
                mWebView.stopLoading();

                // 清除缓存
                mWebView.clearCache(true);
                mWebView.clearFormData();
                mWebView.clearSslPreferences();

                // 销毁 WebView
                mWebView.destroy();
                // 设置为 null
                mWebView = null;
            } catch (Exception e) {
                LogUtil.e(TAG, "Error destroying WebView", e);
            }
        }
    }

    /**
     * 防止内存泄漏的额外措施
     */
    @Override
    protected void onStop() {
        super.onStop();
        if (mWebView != null) {
            try {
                // 停止所有可能的后台处理
                mWebView.stopLoading();
            } catch (Exception e) {
                LogUtil.e(TAG, "Error stopping WebView", e);
            }
        }
    }

    @Override
    protected void onPause() {
        if (mWebView != null) {
            try {
                // 暂停 WebView 以减少资源使用
                mWebView.onPause();
                // 暂停 JS 执行
                mWebView.getSettings().setJavaScriptEnabled(false);
            } catch (Exception e) {
                LogUtil.e(TAG, "Error pausing WebView", e);
            }
        }
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mWebView != null && !isWebViewDestroyed) {
            try {
                // 恢复 WebView
                mWebView.onResume();
                // 恢复 JS 执行
                mWebView.getSettings().setJavaScriptEnabled(true);
            } catch (Exception e) {
                LogUtil.e(TAG, "Error resuming WebView", e);
            }
        }
    }
}
