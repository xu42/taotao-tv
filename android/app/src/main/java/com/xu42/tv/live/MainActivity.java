package com.xu42.tv.live;

import android.content.Intent;
import android.content.res.Configuration;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;

import androidx.databinding.DataBindingUtil;

import com.xu42.tv.live.databinding.DialogExitBinding;
import com.xu42.tv.live.impl.WebViewClientImpl;
import com.xu42.tv.live.util.LogUtil;
import com.xu42.tv.live.util.WebViewDispatcher;

/**
 * 影视：承载影视聚合页（内置 video.html），按分类聚合各免费源。
 *
 * 按键：方向键/OK 交给页面处理，设置键调出播放菜单，返回键逐级退出。
 */
public class MainActivity extends BaseWebViewActivity {

    private DialogExitBinding exitDialogBinding;
    private boolean isExitDialogShowing = false;

    public boolean dispatchTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            float x = event.getX();
            float y = event.getY();
            if (x < 100f && y < 100f) {
                ctrl("menu");
            }
        }
        return super.dispatchTouchEvent(event);
    }

    private boolean ctrl(String code) {
        if (mWebView != null) {
            String js = "_menuCtrl." + code + "()";
            LogUtil.i(TAG, js);
            mWebView.evaluateJavascript(js, null);
        }
        return true;
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
    }

    @Override
    protected void onResume() {
        super.onResume();
        WebViewDispatcher.registerLoadUrlCallback(u -> {
            if (mWebView != null) {
                mWebView.loadUrl(u);
            }
        });
    }

    @Override
    protected void onPause() {
        WebViewDispatcher.unregister();
        super.onPause();
    }

    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_UP) {
            return super.dispatchKeyEvent(event);
        }
        int keyCode = event.getKeyCode();

        // 优先处理退出对话框
        if (isExitDialogShowing) {
            if (keyCode == KeyEvent.KEYCODE_BACK) {
                hideExitDialog();
                return true;
            }
            return super.dispatchKeyEvent(event);
        }

        if (isMenuShow()) {
            if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_MENU
                    || keyCode == KeyEvent.KEYCODE_TAB) {
                hideMenu();
                return true;
            }
            return super.dispatchKeyEvent(event);
        }

        if (keyCode == KeyEvent.KEYCODE_BACK) {
            return keyBack();
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
            return ctrl("ok");
        }
        if (keyCode == KeyEvent.KEYCODE_MENU || keyCode == KeyEvent.KEYCODE_TAB) {
            return ctrl("menu");
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
            return ctrl("right");
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
            return ctrl("left");
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
            return ctrl("down");
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
            return ctrl("up");
        }
        return super.dispatchKeyEvent(event);
    }

    private boolean keyBack() {
        String url = WebViewClientImpl.backUrl();
        LogUtil.i(TAG, "keyBack " + url);
        if (null != url && null != mWebView) {
            mWebView.loadUrl(url);
            return true;
        }
        showExitDialog();
        return true;
    }

    private void showExitDialog() {
        if (exitDialogBinding == null) {
            initExitDialog();
        }
        if (exitDialogBinding == null) {
            return;
        }
        isExitDialogShowing = true;
        exitDialogBinding.exitDialogContainer.setVisibility(View.VISIBLE);
        exitDialogBinding.exitTipTitle.setText("影视");
        exitDialogBinding.exitTipText.setText("左右：切换分类\n上下：切换片源分组\nOK：打开影片");
        // 影视页没有「频道」概念，隐藏收藏按钮
        exitDialogBinding.btnFavorite.setVisibility(View.GONE);
        exitDialogBinding.btnBackHome.setNextFocusUpId(exitDialogBinding.btnCancel.getId());
        exitDialogBinding.btnCancel.setNextFocusDownId(exitDialogBinding.btnBackHome.getId());
        exitDialogBinding.btnCancel.post(() -> exitDialogBinding.btnCancel.requestFocus());
    }

    private void hideExitDialog() {
        if (exitDialogBinding != null) {
            isExitDialogShowing = false;
            exitDialogBinding.exitDialogContainer.setVisibility(View.GONE);
        }
        if (null != mWebView) {
            mWebView.requestFocus();
        }
    }

    private void initExitDialog() {
        View dialogView = findViewById(R.id.exitDialog);
        exitDialogBinding = DataBindingUtil.bind(dialogView);
        if (exitDialogBinding == null) {
            return;
        }
        exitDialogBinding.exitDialogContainer.setFocusable(true);
        exitDialogBinding.exitDialogContainer.setFocusableInTouchMode(true);

        exitDialogBinding.btnCancel.setOnClickListener(v -> hideExitDialog());
        exitDialogBinding.btnBackHome.setOnClickListener(v -> {
            hideExitDialog();
            startActivity(new Intent(this, HomeActivity.class));
            finish();
        });
        exitDialogBinding.btnExitApp.setOnClickListener(v -> {
            finishAffinity();
            System.exit(0);
        });
        exitDialogBinding.dialogBackdrop.setOnClickListener(v -> hideExitDialog());
    }
}
