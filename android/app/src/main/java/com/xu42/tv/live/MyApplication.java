package com.xu42.tv.live;

import android.app.ActivityManager;
import android.app.Application;
import android.content.Context;
import android.os.Build;
import android.provider.Settings;
import android.webkit.WebView;

import androidx.multidex.MultiDex;

import java.util.Random;
import java.util.UUID;

import com.xu42.tv.live.service.CrashHandler;
import com.xu42.tv.live.util.LogUtil;


public class MyApplication extends Application  {

    private static Context context;
    private static final String TAG = "MyApplication";
   @Override
   protected void attachBaseContext(Context base) {
       super.attachBaseContext(base);
       MultiDex.install(base);
   }

   public static  String androidId=null;


    @Override
    public void onCreate() {
        super.onCreate();
        LogUtil.i(TAG, "onViewInitBegin: ");
        allErrorCatch();
        context = getApplicationContext();
        // Android P 及以上，确保多进程使用 WebView 时数据目录隔离，避免初始化崩溃
        initPieWebView();
        androidId = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);
        if(null==androidId){
            LogUtil.i(TAG, "androidId: getUUID");
            androidId=getUUID();
        }
        CrashHandler.getInstance().init(this);
        try {
            System.setProperty("persist.sys.media.use-mediaDrm", "false");
        } catch (Exception e) {
            // 安全处理异常
            LogUtil.e("use-mediaDrm:"+e.getMessage());
        }
    }
    private String randomStr(int length){
        StringBuilder result = new StringBuilder();
        Random random = new Random();
        for (int i = 0; i < length; i++) {
            // 生成97-122之间的随机整数，对应ASCII码中的a-z
            int randomInt = random.nextInt(26) + 97;
            result.append((char) randomInt);
        }
        return result.toString();
    }
    private void allErrorCatch(){
        final Thread.UncaughtExceptionHandler systemDefault = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(Thread thread, Throwable throwable) {
                // 检查是否为 SurfaceTexture 相关异常
                if (throwable != null && throwable.getMessage() != null &&
                        (throwable instanceof NullPointerException) &&
                        (throwable.getStackTrace() != null && throwable.getStackTrace().length > 0 &&
                                containsSurfaceTextureInStackTrace(throwable.getStackTrace()))) {

                    LogUtil.e("Application", "捕获到 SurfaceTexture 相关异常: " + throwable.getMessage());

                    // 记录非致命异常但不终止应用
                    CrashHandler.recordNonFatal(getApplicationContext(), throwable);
                    return;
                }

                // 其他异常，交给系统默认处理器，避免递归
                if (systemDefault != null) {
                    systemDefault.uncaughtException(thread, throwable);
                } else {
                    android.os.Process.killProcess(android.os.Process.myPid());
                }
            }

            // 检查堆栈跟踪是否包含 SurfaceTexture 相关内容
            private boolean containsSurfaceTextureInStackTrace(StackTraceElement[] stackTrace) {
                for (StackTraceElement element : stackTrace) {
                    if (element.getClassName().contains("SurfaceTexture") ||
                            element.getMethodName().contains("SurfaceTexture")) {
                        return true;
                    }
                }
                return false;
            }
        });
    }
    public static Context getAppContext() {
        return context;
    }
    private static final String PROCESS = "com.xu42.tv.live";
    private void initPieWebView() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            String processName = getProcessName(this);
            if (!PROCESS.equals(processName)) {
                WebView.setDataDirectorySuffix(getString(processName, "utao"));
            }
        }
    }
    public String getProcessName(Context context) {
        if (context == null) return null;
        ActivityManager manager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningAppProcessInfo processInfo : manager.getRunningAppProcesses()) {
            if (processInfo.pid == android.os.Process.myPid()) {
                return processInfo.processName;
            }
        }
        return null;
    }

    public String getString(String s, String defValue) {
        return isEmpty(s) ? defValue : s;
    }

    public boolean isEmpty(String s) {
        return s == null || s.trim().length() == 0;
    }

    public static Context getContext() {
        return context;
    }
    public static String getUUID() {
        String serial = null;
        String m_szDevIDShort = "随机两位数" +
                Build.BOARD.length() % 10 + Build.BRAND.length() % 10 +
                Build.CPU_ABI.length() % 10 + Build.DEVICE.length() % 10 +
                Build.DISPLAY.length() % 10 + Build.HOST.length() % 10 +
                Build.ID.length() % 10 + Build.MANUFACTURER.length() % 10 +
                Build.MODEL.length() % 10 + Build.PRODUCT.length() % 10 +
                Build.TAGS.length() % 10 + Build.TYPE.length() % 10 +
                Build.USER.length() % 10; //13 位
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                serial = "默认值";
            } else {
                serial = Build.SERIAL;
            }
            //API>=9 使用serial号
            return new UUID(m_szDevIDShort.hashCode(), serial.hashCode()).toString();
        } catch (Exception exception) {
            //serial需要一个初始化
            serial = "默认值"; // 随便一个初始化
        }
        //使用硬件信息拼凑出来的15位号码
        return new UUID(m_szDevIDShort.hashCode(), serial.hashCode()).toString();
    }


}