package com.xu42.tv.live.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import com.xu42.tv.live.MainActivity;
import com.xu42.tv.live.util.ValueUtil;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) {
            return;
        }
        String action = intent.getAction();
        if (!"android.intent.action.BOOT_COMPLETED".equals(action) &&
            !"android.intent.action.LOCKED_BOOT_COMPLETED".equals(action)) {
            return;
        }
        // 仅在用户开启自启动时启动主界面
        String autoStart = ValueUtil.getString(context, "autoStart", "0");
        if (!"1".equals(autoStart)) {
            return;
        }
        Intent start = new Intent(context, MainActivity.class);
        start.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(start);
    }
}


