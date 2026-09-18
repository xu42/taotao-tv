package tv.utao.x5;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.os.Build;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.Toast;

import java.util.List;

import tv.utao.x5.util.Util;
import tv.utao.x5.util.ValueUtil;
import tv.utao.x5.utils.ToastUtils;

/**
 * 首页：只有「直播」和「影视」两个入口。
 *
 * 交互：
 *   遥控器 左/右   在两个入口之间切换选中
 *   遥控器 OK     进入对应模块
 *   遥控器 返回   弹出退出确认
 */
public class HomeActivity extends Activity {

    private static final String TAG = "HomeActivity";

    private LinearLayout cardLive;
    private LinearLayout cardVideo;
    private View liveHint;
    private View videoHint;
    private View x5Tip;
    private View exitDialogContainer;

    private boolean isExitDialogShowing = false;

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
        x5Tip = findViewById(R.id.x5Tip);
        exitDialogContainer = findViewById(R.id.exitDialogContainer);

        setupCards();
        setupX5Tip();
        setupExitDialog();
    }

    /** 两个大入口：焦点放大 + 提示显隐 + 点击进入 */
    private void setupCards() {
        cardLive.setOnClickListener(v -> toLive());
        cardVideo.setOnClickListener(v -> toVideo());

        cardLive.setOnFocusChangeListener((v, hasFocus) -> {
            liveHint.setVisibility(hasFocus ? View.VISIBLE : View.INVISIBLE);
            scale(v, hasFocus);
        });
        cardVideo.setOnFocusChangeListener((v, hasFocus) -> {
            videoHint.setVisibility(hasFocus ? View.VISIBLE : View.INVISIBLE);
            scale(v, hasFocus);
        });

        // 页面打开后默认选中「直播」，符合大多数人的打开即看台的习惯
        cardLive.post(() -> cardLive.requestFocus());
    }

    private void scale(View v, boolean focused) {
        float target = focused ? 1.04f : 1.0f;
        v.animate().scaleX(target).scaleY(target).setDuration(160).start();
    }

    /** 未开启 X5 内核且设备支持时，给出一个可一键开启的提示 */
    private void setupX5Tip() {
        boolean x5Ok = "ok".equals(ValueUtil.getString(this, "x5", "0"));
        boolean canUseX5 = !Util.isX86();
        if (x5Ok || !canUseX5) {
            x5Tip.setVisibility(View.GONE);
            return;
        }
        x5Tip.setVisibility(View.VISIBLE);
        x5Tip.setOnClickListener(v -> {
            ValueUtil.putString(getApplicationContext(), "openX5", "1");
            ToastUtils.show(this, "已开启 X5 内核，应用将自动重启", Toast.LENGTH_SHORT);
            getWindow().getDecorView().postDelayed(this::restartApp, 600);
        });
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
        exitDialogContainer.setVisibility(View.VISIBLE);
        View btnCancel = findViewById(R.id.btnCancelExit);
        btnCancel.post(btnCancel::requestFocus);
    }

    private void hideExitDialog() {
        isExitDialogShowing = false;
        exitDialogContainer.setVisibility(View.GONE);
        cardLive.post(cardLive::requestFocus);
    }

    private void toLive() {
        startActivity(new Intent(this, LiveActivity.class));
        finish();
    }

    private void toVideo() {
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }

    /** 让改动在下次启动时生效（主要是 X5 内核安装） */
    private void restartApp() {
        try {
            ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
            List<ActivityManager.RunningAppProcessInfo> list = manager.getRunningAppProcesses();
            for (ActivityManager.RunningAppProcessInfo info : list) {
                if (info.pid != android.os.Process.myPid()) {
                    android.os.Process.killProcess(info.pid);
                }
            }
        } catch (Throwable ignore) {
        }
        finishAffinity();
        android.os.Process.killProcess(android.os.Process.myPid());
        System.exit(0);
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
        // 从直播/影视返回首页时，保证焦点回到「直播」
        cardLive.requestFocus();
    }
}
