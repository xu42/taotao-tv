package com.xu42.tv.live;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Toast;

import androidx.databinding.DataBindingUtil;

import java.io.File;

import com.xu42.tv.live.api.ConfigApi;
import com.xu42.tv.live.call.ConfigCallback;
import com.xu42.tv.live.call.DownloadProgressListener;
import com.xu42.tv.live.databinding.ActivityStartBinding;
import com.xu42.tv.live.domain.ApkInfo;
import com.xu42.tv.live.domain.ConfigDTO;
import com.xu42.tv.live.util.AppVersionUtils;
import com.xu42.tv.live.util.HttpUtil;
import com.xu42.tv.live.util.LogUtil;
import com.xu42.tv.live.util.Util;
import com.xu42.tv.live.util.ValueUtil;
import com.xu42.tv.live.utils.ToastUtils;

/**
 * 启动页：检查服务端配置与应用更新，然后进入「直播 / 影视」首页。
 * 网页渲染统一走设备自带的系统 WebView，无需额外内核。
 */
public class StartActivity extends Activity {
    private long mClickBackTime = 0;
    private static final String TAG = "StartActivity";
    protected ActivityStartBinding binding;
    private ConfigDTO thisConfigDTO;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);//隐藏标题栏
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        binding = DataBindingUtil.setContentView(this, R.layout.activity_start);
        final Context thisContext = this;
        binding.setUpdateHandler(new UpdateHandler(this));

        // 检查更新与资源版本
        ConfigApi.syncGetConfig(new ConfigCallback() {
            @Override
            public void getConfig(ConfigDTO configDTO) {
                // 服务端不可用时不再阻塞启动，直接进首页
                if (null == configDTO) {
                    LogUtil.i(TAG, "config 为空，直接进入首页");
                    runOnUiThread(StartActivity.this::to);
                    return;
                }
                thisConfigDTO = configDTO;
                int versionCode = AppVersionUtils.getVersionCode();
                ApkInfo apkInfo = configDTO.getApk();
                // 自建服务端可以只返回资源更新信息：缺少 apk 字段时跳过应用自更新，直接进入
                int updateCode = (null == apkInfo || null == apkInfo.getVersion()) ? 0 : apkInfo.getVersion();
                LogUtil.i(TAG, updateCode + " old " + versionCode);
                if (null != apkInfo && updateCode > versionCode) {
                    runOnUiThread(() -> {
                        if (!apkInfo.getForce() && isUpdateLater(thisContext)) {
                            to();
                            return;
                        }
                        binding.startWrapper.setVisibility(View.GONE);
                        if (apkInfo.getForce()) {
                            binding.updateCancelBtn.setVisibility(View.GONE);
                        }
                        binding.updateApkWrapper.setVisibility(View.VISIBLE);
                        binding.updateDesc.setText(apkInfo.getDesc());
                        binding.updateOkBtn.requestFocus();
                    });
                    return;
                }
                runOnUiThread(StartActivity.this::to);
            }
        });
    }

    private boolean isUpdateLater(Context context) {
        return "ok".equals(ValueUtil.getString(context, "updateLater", ""));
    }

    private void to() {
        // 启动后统一进入「直播 / 影视」二选一的首页
        Intent intent = new Intent(StartActivity.this, HomeActivity.class);
        LogUtil.i(TAG, "启动页面：首页");
        startActivity(intent);
        finish();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            keyBack();
        }
        return super.onKeyUp(keyCode, event);
    }

    private void keyBack() {
        long currentTime = System.currentTimeMillis();
        if (currentTime - mClickBackTime < 3000) {
            finish();
        } else {
            ToastUtils.show(this, "再按一次返回键退出", Toast.LENGTH_SHORT);
            mClickBackTime = currentTime;
        }
    }

    public class UpdateHandler {
        private Context thisContext;

        public UpdateHandler(Context context) {
            this.thisContext = context;
        }

        public void updateOk() {
            if (null == thisConfigDTO || null == thisConfigDTO.getApk()) {
                return;
            }
            binding.progressApk.setVisibility(View.VISIBLE);
            File targetFile = new File(thisContext.getFilesDir().getPath(), "update.apk");
            HttpUtil.downloadByProgress(thisConfigDTO.getApk().getUrl(),
                    targetFile, new DownloadProgressListener() {
                        @Override
                        public void onDownloadProgress(long sumReaded, long content, boolean done) {
                            int num = content > 0 ? (int) (sumReaded * 100 / content) : 0;
                            runOnUiThread(() -> binding.progressApk.setProgress(num));
                        }

                        @Override
                        public void onDownloadResult(File target, boolean done) {
                            runOnUiThread(() -> {
                                if (target != null && target.exists()) {
                                    try {
                                        Util.installApk(StartActivity.this, target);
                                    } catch (Exception e) {
                                        LogUtil.e(TAG, "Error installing APK: " + e.getMessage());
                                        ToastUtils.show(StartActivity.this, "安装失败，请重试", Toast.LENGTH_LONG);
                                    }
                                } else {
                                    ToastUtils.show(StartActivity.this, "下载文件不存在，请重试", Toast.LENGTH_LONG);
                                }
                            });
                        }

                        @Override
                        public void onFailResponse() {
                            runOnUiThread(() ->
                                    ToastUtils.show(thisContext, "下载失败，请检查网络后重试", Toast.LENGTH_SHORT));
                        }
                    });
        }

        public void updateCancel() {
            binding.updateApkWrapper.setVisibility(View.GONE);
            binding.startWrapper.setVisibility(View.VISIBLE);
            ValueUtil.putString(thisContext, "updateLater", "ok");
            to();
        }
    }
}
