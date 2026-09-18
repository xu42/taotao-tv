package tv.utao.x5;

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

import com.tencent.smtt.export.external.TbsCoreSettings;
import com.tencent.smtt.sdk.QbSdk;
import com.tencent.smtt.sdk.TbsListener;

import java.io.File;
import java.util.HashMap;

import tv.utao.x5.api.ConfigApi;
import tv.utao.x5.call.ConfigCallback;
import tv.utao.x5.call.DownloadProgressListener;
import tv.utao.x5.databinding.ActivityStartBinding;
import tv.utao.x5.domain.ApkInfo;
import tv.utao.x5.domain.ConfigDTO;
import tv.utao.x5.service.UpdateX5Service;
import tv.utao.x5.util.AppVersionUtils;
import tv.utao.x5.util.FileUtil;
import tv.utao.x5.util.HttpUtil;
import tv.utao.x5.util.LogUtil;
import tv.utao.x5.util.Util;
import tv.utao.x5.util.ValueUtil;
import tv.utao.x5.utils.ToastUtils;

public class StartActivity extends Activity {
    private long mClickBackTime = 0;
    private static final String TAG = "StartActivity";
    protected ActivityStartBinding binding;
   // private static Context thisContext;
    private ConfigDTO thisConfigDTO;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);//隐藏标题栏
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        //setContentView(R.layout.activity_start);
        binding = DataBindingUtil.setContentView(this, R.layout.activity_start);
        Context thisContext=this;
        binding.setUpdateHandler(new UpdateHandler(this));
        //check  更新应用
        //HttpUtil.getJson("https://www.baidu.com",null);
        ConfigApi.syncGetConfig(new ConfigCallback() {
            @Override
            public void getConfig(ConfigDTO configDTO) {
                if(null==configDTO){
                    runOnUiThread(()->{
                        if(Util.isX86()||x5Ok()||(Util.isNotNeedX5()&&!openX5())){
                            to();
                        }else{
                            ToastUtils.show(thisContext,"请求接口失败 请检查网络 2次返回键退出重进", Toast.LENGTH_SHORT);
                        }
                    });
                    return;
                }
                thisConfigDTO=configDTO;
                int versionCode=  AppVersionUtils.getVersionCode();
                ApkInfo apkInfo = configDTO.getApk();
                // 自建服务端可以只返回资源更新信息：缺少 apk 字段时跳过应用自更新，直接进入
                int updateCode = (null == apkInfo || null == apkInfo.getVersion()) ? 0 : apkInfo.getVersion();
                LogUtil.i(TAG,updateCode+" old "+versionCode);
                if(null != apkInfo && updateCode>versionCode){
                    //更新数据
                    runOnUiThread(()->{
                        if(!apkInfo.getForce()&&isUpdateLater(thisContext)){
                            installX5(thisContext,configDTO);
                            return;
                        }
                        binding.startX5Wrapper.setVisibility(View.GONE);
                        if(apkInfo.getForce()){
                            binding.updateCancelBtn.setVisibility(View.GONE);
                        }
                        binding.updateApkWrapper.setVisibility(View.VISIBLE);
                        //binding.updateApkWrapper.requestFocus();
                        binding.updateDesc.setText(apkInfo.getDesc());
                        binding.updateOkBtn.requestFocus();
                        //binding.updateOkBtn.setNextFocusRightId(R.id.updateCancelBtn);
                    });
                    return;
                }
                installX5(thisContext,configDTO);
            }
        });

    }
    private boolean isUpdateLater(Context context){
        String UpdateLater=ValueUtil.getString(context,"updateLater");
        if(UpdateLater.equals("ok")){
            return true;
        }
        return false;
    }
    private boolean openX5(){
        return "1".equals(ValueUtil.getString(getApplicationContext(),"openX5","0"));
    }
    private boolean x5Ok(){
        return "ok".equals(ValueUtil.getString(getApplicationContext(),"x5","0"));
    }
    private  void installX5(Context content,ConfigDTO configDTO){
        boolean isX5Ok=x5Ok();
        boolean is64=Util.is64();
        LogUtil.i(TAG,"is64::: "+is64+" id "+MyApplication.androidId);
        //new File(toFilePath).exists()
        //x5Ok.equals("ok")
        //boolean is64=Util.is64();
        boolean isX86 = Util.isX86();
        if(isX5Ok){
            LogUtil.i(TAG, "x5  install  "+isX5Ok);
            to();
            return;
        }
        //Build.VERSION_CODES.R 安卓11
        //Build.VERSION_CODES.P 安卓9
        if(isX86){
            LogUtil.i(TAG, "system  isX86");
            to();
            return;
        }
        boolean isOpenX5=openX5();
        LogUtil.i(TAG,"isOpenX5::::"+isOpenX5);
        //Util.isNotNeedX5()&&!isOpenX5
        if(Util.isNotNeedX5()&&!isOpenX5){
            to();
            return;
        }
        String targetDir = FileUtil.getTBSFileDir(content).getPath()+"/";
        runOnUiThread(()->{
            binding.progressWrapper.setVisibility(View.VISIBLE);
        });
        String toFilePath =  UpdateX5Service.updateX5(content,configDTO, new DownloadProgressListener() {
            @Override
            public void onDownloadProgress(long sumReaded, long content, boolean done) {
                int num = (int)(sumReaded*100/content);
                runOnUiThread(()->{
                    binding.progressbar.setProgress(num);
                    binding.progressTxt.setText("X5浏览器内核下载中。。。 "+num+"%");
                });
            }

            @Override
            public void onDownloadResult(File target, boolean done) {
                if (target != null && target.exists()) {
                    String fileName = target.getName();
                    String toFilePath=targetDir+fileName;
                    runOnUiThread(()->{
                        ToastUtils.show(content,"下载成功 正在安装启动中", Toast.LENGTH_SHORT);
                        binding.progressWrapper.setVisibility(View.GONE);
                        initX5(toFilePath);
                    });
                } else {
                    runOnUiThread(() -> {
                        ToastUtils.show(StartActivity.this, "下载文件不存在，请重试", Toast.LENGTH_LONG);
                    });
                }
            }

            @Override
            public void onFailResponse() {
                runOnUiThread(()->{
                    ToastUtils.show(content,"下载失败 请检查网络 2次返回退出重进", Toast.LENGTH_SHORT);
                });
            }
        });
        if(null!=toFilePath){
            runOnUiThread(()->{
                if("error".equals(toFilePath)){
                    ToastUtils.show(content,"获取数据出错 请检查网络", Toast.LENGTH_SHORT);
                    return;
                }
                binding.progressWrapper.setVisibility(View.GONE);
                initX5(toFilePath);
            });

        }
    }
    private void to(){
        // 启动后统一进入「直播 / 影视」二选一的首页
        Intent intent = new Intent(StartActivity.this, HomeActivity.class);
        LogUtil.i(TAG, "启动页面：首页");
        startActivity(intent);
        finish();
    }
    private  void resetSdk() {
        // 在调用TBS初始化、创建WebView之前进行如下配置
        HashMap<String, Object> map = new HashMap<>();
        map.put(TbsCoreSettings.TBS_SETTINGS_USE_SPEEDY_CLASSLOADER, true);
        map.put(TbsCoreSettings.TBS_SETTINGS_USE_DEXLOADER_SERVICE, true);
        map.put("disable_foreground_service", true); // 关键参数
        map.put("TBS_DISABLE_AUTO_INIT", true); // 禁止自动后台初始化
        // 关键参数：禁用无障碍相关功能
        map.put("TBS_DISABLE_ACCESSIBILITY", true);
        QbSdk.initTbsSettings(map);
        //QbSdk.setDownloadWithoutWifi(true);
       // QbSdk.initX5Environment(this, null);
       // QbSdk.setDisableUMLog(true);
//        QbSdk.disableAutoCreateX5Webview();
        //强制使用系统内核
        //QbSdk.forceSysWebView();

    }
    public void initX5(String toFilePath){
        LogUtil.i(TAG, "initX5  begin  "+toFilePath);
        resetSdk();
        QbSdk.installLocalTbsCore(getApplicationContext(), 1,
                toFilePath);
        QbSdk.setTbsListener(new TbsListener() {
            @Override
            public void onDownloadFinish(int i) {
                LogUtil.i(TAG, "onDownload Finish " + i);
            }

            @Override
            public void onDownloadProgress(int i) {
                LogUtil.i(TAG, "onDownload Progress " + i);
            }

            @Override
            public void onInstallFinish(int i) {
                LogUtil.i(TAG, "onInstallFinish " + i);
                if(i==200){
                    //记录
                    ValueUtil.putString(getApplicationContext(),"x5","ok");
                    boolean canLoadX5 = QbSdk.canLoadX5(getApplicationContext());
                    LogUtil.i(TAG, "升级成功 canLoadX5:"+canLoadX5+" isX5Core "
                             +" versionX5 "+QbSdk.getTbsVersion(getApplicationContext()));
                    to();
                }
            }
        });
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            keyBack();
        }
        return super.onKeyUp(keyCode, event);
    }

    private  void keyBack(){
        long currentTime = System.currentTimeMillis();
        if (currentTime - mClickBackTime < 3000) {
            //killAppProcess();
            finish();
            //super.onBackPressed();
           // System.exit(0);
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
            binding.progressApk.setVisibility(View.VISIBLE);
            File targetFile = new File(thisContext.getFilesDir().getPath(), "x5.apk");
            HttpUtil.downloadByProgress(thisConfigDTO.getApk().getUrl(),
                    targetFile, new DownloadProgressListener() {
                        @Override
                        public void onDownloadProgress(long sumReaded, long content, boolean done) {
                            int num = (int)(sumReaded*100/content);
                            runOnUiThread(() -> {
                                binding.progressApk.setProgress(num);
                            });
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
                            runOnUiThread(() -> {
                                ToastUtils.show(thisContext, "下载失败，请检查网络后重试", Toast.LENGTH_SHORT);
                            });
                        }
                    });
        }
        public  void updateCancel(){
            binding.updateApkWrapper.setVisibility(View.GONE);
            binding.startX5Wrapper.setVisibility(View.VISIBLE);
            installX5(thisContext,thisConfigDTO);
            ValueUtil.putString(thisContext,"updateLater","ok");
        }
    }



}
