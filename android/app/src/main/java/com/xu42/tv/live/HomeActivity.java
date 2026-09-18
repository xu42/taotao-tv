package com.xu42.tv.live;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.LinearLayout;

/**
 * 首页：只有「直播」和「影视」两个入口。
 *
 * 交互：
 *   遥控器 左/右   在两个入口之间切换选中（未选中浅色底，选中深色底）
 *   遥控器 OK     进入对应模块
 *   遥控器 返回   弹出退出确认
 */
public class HomeActivity extends Activity {

    private static final String TAG = "HomeActivity";
    /** 选中态放大倍数 */
    private static final float FOCUS_SCALE = 1.03f;
    private static final long SCALE_DURATION = 140L;

    private LinearLayout cardLive;
    private LinearLayout cardVideo;
    private View liveHint;
    private View videoHint;
    private View exitDialogContainer;

    private boolean isExitDialogShowing = false;
    /** 首次焦点直接落位、不做缩放动画，避免开屏"跳一下" */
    private boolean firstFocusSettled = false;
    /** 记录退出弹窗前选中的卡片，关闭弹窗后还原 */
    private View lastFocusedCard;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        super.onCreate(savedInstanceState);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        setContentView(R.layout.activity_home);

        cardLive = findViewById(R.id.cardLive);
        cardVideo = findViewById(R.id.cardVideo);
        liveHint = findViewById(R.id.liveHint);
        videoHint = findViewById(R.id.videoHint);
        exitDialogContainer = findViewById(R.id.exitDialogContainer);

        setupCards();
        setupExitDialog();
    }

    /** 两个大入口：焦点放大 + 提示显隐 + 点击进入 */
    private void setupCards() {
        cardLive.setOnClickListener(v -> toLive());
        cardVideo.setOnClickListener(v -> toVideo());

        cardLive.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                lastFocusedCard = v;
            }
            liveHint.setVisibility(hasFocus ? View.VISIBLE : View.INVISIBLE);
            applyFocusScale(v, hasFocus);
        });
        cardVideo.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                lastFocusedCard = v;
            }
            videoHint.setVisibility(hasFocus ? View.VISIBLE : View.INVISIBLE);
            applyFocusScale(v, hasFocus);
        });
    }

    /**
     * 焦点缩放。
     * 首次落位时直接设置最终值（不播动画），避免进入首页时卡片"跳一下"；
     * 之后切换选择时再走动画，并先取消上一次未播完的动画，避免来回切换时闪烁。
     */
    private void applyFocusScale(View v, boolean focused) {
        float target = focused ? FOCUS_SCALE : 1.0f;
        if (!firstFocusSettled) {
            firstFocusSettled = true;
            v.setScaleX(target);
            v.setScaleY(target);
            return;
        }
        v.animate().cancel();
        v.animate().scaleX(target).scaleY(target).setDuration(SCALE_DURATION).start();
    }

    private void setupExitDialog() {
        View btnExit = findViewById(R.id.btnExit);
        View btnCancel = findViewById(R.id.btnCancelExit);
        btnExit.setOnClickListener(v -> {
            hideExitDialog();
            finishAffinity();
            System.exit(0);
        });
        btnCancel.setOnClickListener(v -> hideExitDialog());
        exitDialogContainer.setOnClickListener(v -> hideExitDialog());
    }

    private void showExitDialog() {
        if (isExitDialogShowing) {
            return;
        }
        isExitDialogShowing = true;
        if (cardVideo.isFocused()) {
            lastFocusedCard = cardVideo;
        } else if (cardLive.isFocused()) {
            lastFocusedCard = cardLive;
        }
        exitDialogContainer.setVisibility(View.VISIBLE);
        View btnCancel = findViewById(R.id.btnCancelExit);
        btnCancel.post(btnCancel::requestFocus);
    }

    private void hideExitDialog() {
        isExitDialogShowing = false;
        exitDialogContainer.setVisibility(View.GONE);
        View target = (lastFocusedCard != null) ? lastFocusedCard : cardLive;
        target.post(target::requestFocus);
    }

    private void toLive() {
        startActivity(new Intent(this, LiveActivity.class));
        finish();
    }

    private void toVideo() {
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_UP) {
            return super.dispatchKeyEvent(event);
        }
        int keyCode = event.getKeyCode();

        if (isExitDialogShowing) {
            // 弹窗显示时把按键交给系统做焦点切换，返回键关闭弹窗
            if (keyCode == KeyEvent.KEYCODE_BACK) {
                hideExitDialog();
                return true;
            }
            return super.dispatchKeyEvent(event);
        }

        if (keyCode == KeyEvent.KEYCODE_BACK) {
            showExitDialog();
            return true;
        }
        // 菜单键不再使用，忽略以免误触
        if (keyCode == KeyEvent.KEYCODE_MENU || keyCode == KeyEvent.KEYCODE_TAB) {
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 从直播/影视返回首页时，保证有焦点可操作；已选中则不打断
        if (!cardLive.isFocused() && !cardVideo.isFocused()) {
            View target = (lastFocusedCard != null) ? lastFocusedCard : cardLive;
            target.post(target::requestFocus);
        }
    }
}
