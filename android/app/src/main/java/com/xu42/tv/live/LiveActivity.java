package com.xu42.tv.live;

import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.webkit.JavascriptInterface;
import android.webkit.PermissionRequest;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.databinding.DataBindingUtil;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.xu42.tv.live.databinding.ActivityLiveBinding;
import com.xu42.tv.live.domain.live.Live;
import com.xu42.tv.live.domain.live.Vod;
import com.xu42.tv.live.impl.WebViewClientImpl;
import com.xu42.tv.live.service.UpdateService;
import com.xu42.tv.live.util.FileUtil;
import com.xu42.tv.live.util.HttpUtil;
import com.xu42.tv.live.util.JsonTypes;
import com.xu42.tv.live.util.JsonUtil;
import com.xu42.tv.live.util.LogUtil;
import com.xu42.tv.live.util.Util;
import com.xu42.tv.live.utils.ToastUtils;

/**
 * 电视直播（全应用唯一页面）。
 *
 * 播放时：
 *   上下   上一个 / 下一个频道（在当前分类内循环）
 *   左右   切换这个台的下一个 / 上一个源（默认央视网，失败会自动降级）
 *   OK / 设置键 / 点左半屏   打开切台菜单
 *   返回   第一次弹「再按一次退出」提示，1.2 秒内再按一次才真的退出
 *
 * 切台菜单（二级分类）：
 *   左栏是分类（央视 / 卫视 / 各省），右栏是该分类下的频道。
 *   左右在两栏间移动（当前有焦点的那一栏高亮，另一栏变暗），上下在本栏内移动，
 *   OK 播放并收起菜单，返回 / 设置键收起菜单。
 *
 * 平板触屏：
 *   点左半屏 = 打开切台菜单；点右半屏 = 相当于按一次返回键（连点两次即退出）
 */
public class LiveActivity extends BaseActivity {
    protected String TAG = "LiveActivity";

    protected ActivityLiveBinding binding;

    /** 当前播放的频道（静态保存，页面来回切换时保留） */
    private static Vod currentLive = null;
    /** 当前播放的源在该频道源列表里的下标 */
    private static int currentSourceIndex = 0;
    /** 菜单里选中的分类 / 频道 */
    private static int currentCategoryIndex = 0;
    private static int currentChannelIndex = 0;

    /** 全量分组（归一化后的分类） */
    private final List<Live> categories = new ArrayList<>();

    private boolean isMenuShow = false;
    /** 正在给菜单灌数据：此时的列表选择回调是副作用，不是用户操作 */
    private boolean syncingMenu = false;

    // ---------------------------------------------------------------- 返回键退出
    /** 连按两次返回键的窗口 */
    private static final long DOUBLE_BACK_MS = 1200L;
    /** 「再按一次返回键退出」提示的停留时长 */
    private static final long EXIT_HINT_MS = 2400L;
    /** 上一次按返回键的时刻（SystemClock.elapsedRealtime） */
    private long lastBackAt = 0L;
    private boolean isExitHintShowing = false;

    /** 菜单当前有焦点的那一栏：0 = 左栏分类，1 = 右栏频道 */
    private int menuColumn = 1;
    /** 没有焦点的那一栏整体压暗，用户一眼能看出左右键切到了哪一栏 */
    private static final float MENU_DIM_ALPHA = 0.4f;

    // ---------------------------------------------------------------- 频道加载中动画
    /** 跳动的圆点数量 */
    private static final int DOT_COUNT = 5;
    /** 每个圆点的错峰启动间隔（毫秒），形成波浪感 */
    private static final long DOT_STAGGER_MS = 110L;
    /** 遮罩最短展示时长，避免「闪一下」的突兀感 */
    private static final long LOADING_MIN_MS = 600L;
    /** 兜底超时：即使没收到 100% 也要收起遮罩，防止卡死在加载态 */
    private static final long LOADING_TIMEOUT_MS = 20000L;
    /** 主文档迟迟加载不完 -> 判定这一路源失败 */
    private static final long LOAD_WATCHDOG_MS = 22000L;
    /** 加载完成后再等一会儿探测页面里有没有播放器 */
    private static final long VIDEO_PROBE_DELAY_MS = 4500L;

    private FrameLayout loadingOverlay;
    private LinearLayout loadingDots;
    private TextView loadingName;
    private final List<ObjectAnimator> dotAnimators = new ArrayList<>();
    private boolean isLoadingShown = false;
    private long loadingShownAt = 0L;

    // ---------------------------------------------------------------- 换源状态机
    /** 每次发起加载自增，用来丢弃过期的回调 */
    private int loadGeneration = 0;
    /** 本轮加载里已经自动换源几次（手动操作会清零） */
    private int autoSwitchTried = 0;
    /** 已经判定失败并处理过的 generation */
    private int failedGeneration = -1;
    /** 当前是否已经加载出画面 */
    private boolean playingStarted = false;
    /** 本次正在加载的地址，用来识别上一次加载迟到的错误回调 */
    private String pendingUrl = null;

    // ---------------------------------------------------------------- 后台停播
    /** 进后台后先静音、再卸载页面之间的间隔（留一点时间让静音脚本生效） */
    private static final long SUSPEND_UNLOAD_DELAY_MS = 250L;
    /** 后台超过它才算「真离开」，回来时延迟一点再重载，等窗口稳定 */
    private static final long BG_SUSPEND_MIN_MS = 2000L;
    /** 长时间后台回到前台时的重载延迟，避免黑闪 */
    private static final long RESUME_RELOAD_DELAY_MS = 600L;

    /** 收到 onStop、等 250ms 就卸载页面（这期间回前台可以取消，省掉一次重载） */
    private boolean pendingBackgroundUnload = false;
    /** 页面已经被卸载到空白页，等回前台重载 */
    private boolean unloadedForBackground = false;

    /** 后台静音脚本：页面内的 video 全部静音并暂停（跨域 iframe 里的尽力而为） */
    private static final String JS_MUTE_ALL_VIDEO =
            "(function(){try{"
                    + "var vs=document.getElementsByTagName('video');"
                    + "for(var i=0;i<vs.length;i++){try{vs[i].muted=true;vs[i].pause();}catch(e){}}"
                    + "var fs=document.getElementsByTagName('iframe');"
                    + "for(var j=0;j<fs.length;j++){try{var d=fs[j].contentDocument;if(!d){continue;}"
                    + "var v2=d.getElementsByTagName('video');"
                    + "for(var k=0;k<v2.length;k++){try{v2[k].muted=true;v2[k].pause();}catch(e){}}"
                    + "}catch(e){}}"
                    + "return 'ok';}catch(e){return 'err';}})()";

    /**
     * 进后台：先静音页面里的 video，稍后卸载到空白页。
     *
     * <p>为什么必须卸载页面：WebView.onPause() 只是暂停调度，已经在播的 HLS 分片请求、
     * 解码与音频不会停 —— App 退到后台仍在偷跑流量和硬件解码器。
     */
    @Override
    protected void onEnterBackground() {
        if (null == mWebView || null == currentLive) {
            return;
        }
        pendingBackgroundUnload = true;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            try {
                Util.eval(mWebView, JS_MUTE_ALL_VIDEO);
            } catch (Throwable ignore) {
            }
        }
        handler.removeCallbacks(unloadForBackgroundRunnable);
        handler.postDelayed(unloadForBackgroundRunnable, SUSPEND_UNLOAD_DELAY_MS);
    }

    @Override
    protected void onLeaveBackground(long backgroundMillis) {
        handler.removeCallbacks(unloadForBackgroundRunnable);
        pendingBackgroundUnload = false;
        if (!unloadedForBackground) {
            // 离开时间太短，页面还没卸载：什么都不用做，画面原样还在
            return;
        }
        unloadedForBackground = false;
        handler.removeCallbacks(restoreAfterBackgroundRunnable);
        long delay = backgroundMillis >= BG_SUSPEND_MIN_MS ? RESUME_RELOAD_DELAY_MS : 0L;
        LogUtil.i(TAG, "回前台，后台停留 " + backgroundMillis + "ms，延迟 " + delay + "ms 重载");
        handler.postDelayed(restoreAfterBackgroundRunnable, delay);
    }

    /** 卸载到空白页：一定要先作废当前这一代，否则 watchdog / 视频探测会把「主动卸载」当成播放失败并换源 */
    private final Runnable unloadForBackgroundRunnable = new Runnable() {
        @Override
        public void run() {
            if (!pendingBackgroundUnload) {
                return;
            }
            pendingBackgroundUnload = false;
            if (null == mWebView || isFinishing()) {
                return;
            }
            unloadedForBackground = true;
            loadGeneration++;
            failedGeneration = -1;
            playingStarted = false;
            pendingUrl = null;
            handler.removeMessages(MSG_LOAD_WATCHDOG);
            handler.removeMessages(MSG_VIDEO_PROBE);
            doHideLoading();
            LogUtil.i(TAG, "进后台，卸载到空白页停播");
            try {
                mWebView.stopLoading();
                mWebView.loadUrl("about:blank");
            } catch (Throwable ignore) {
            }
        }
    };

    /** 回前台重载当前频道：走完整的 startLoad，遮罩 / 看门狗 / 自动换源都能正常工作 */
    private final Runnable restoreAfterBackgroundRunnable = new Runnable() {
        @Override
        public void run() {
            if (null == mWebView || null == currentLive || isFinishing()) {
                return;
            }
            LogUtil.i(TAG, "后台恢复：重载 " + loadingLabel(currentLive, currentSourceIndex));
            startLoad(currentSourceIndex, false);
        }
    };

    @Override
    protected void createInit() {
        bind();
        UpdateService.initTvData();

        // 不做任何观看记录：每次启动都是默认频道 CCTV-1
        if (null == currentLive) {
            currentLive = UpdateService.getDefaultChannel();
            currentSourceIndex = UpdateService.sourceIndexOf(
                    currentLive, null == currentLive ? null : currentLive.getUrl());
        }
        if (null == currentLive) {
            ToastUtils.show(this, "频道数据加载失败，请检查网络后重试", Toast.LENGTH_SHORT);
            finish();
            return;
        }

        initData();
        initWebView();
        mWebView.requestFocus();
        binding.webviewWrapper.requestFocus();

        startLoad(currentSourceIndex, false);
        ToastUtils.show(this, "已切到 " + currentLive.getName() + "，按 OK 键可切换频道", Toast.LENGTH_SHORT);
    }

    /** 后台加载频道数据，回主线程构建分类与菜单 */
    private void initData() {
        new Thread(() -> {
            List<Live> result = UpdateService.getByLives();
            runOnUiThread(() -> {
                categories.clear();
                categories.addAll(result);
                locateCurrent();
                if (isMenuShow) {
                    syncMenu();
                }
            });
        }).start();
    }

    /** 把当前播放的频道定位到分类/频道下标上 */
    private void locateCurrent() {
        if (null == currentLive || categories.isEmpty()) {
            return;
        }
        for (int i = 0; i < categories.size(); i++) {
            List<Vod> vods = categories.get(i).getVods();
            if (null == vods) {
                continue;
            }
            for (int j = 0; j < vods.size(); j++) {
                if (sameChannel(vods.get(j), currentLive)) {
                    currentCategoryIndex = i;
                    currentChannelIndex = j;
                    return;
                }
            }
        }
    }

    /** 两个 Vod 是否是「同一个台」（多源频道只要有任何一路源相同就算） */
    private static boolean sameChannel(Vod a, Vod b) {
        if (a == b) {
            return true;
        }
        if (null == a || null == b) {
            return false;
        }
        String ua = UpdateService.cleanUrl(a.getUrl());
        String ub = UpdateService.cleanUrl(b.getUrl());
        if (null != ua && ua.equals(ub)) {
            return true;
        }
        String ka = identityOf(a);
        String kb = identityOf(b);
        return !ka.isEmpty() && ka.equals(kb);
    }

    /** 频道的身份：取默认源的地址 */
    private static String identityOf(Vod vod) {
        if (null == vod) {
            return "";
        }
        List<Vod> sources = vod.getSources();
        if (null != sources && !sources.isEmpty()) {
            String url = UpdateService.cleanUrl(sources.get(0).getUrl());
            return null == url ? "" : url;
        }
        String url = UpdateService.cleanUrl(vod.getUrl());
        return null == url ? "" : url;
    }

    // ------------------------------------------------------------------ 播放

    protected void initWebViewClient() {
        WebViewClientImpl client = new WebViewClientImpl(getBaseContext(), mWebView);
        client.setLoadStateListener(new WebViewClientImpl.LoadStateListener() {
            @Override
            public void onMainFrameError(String url, String reason) {
                onSourceFailed(url, "页面加载失败");
            }
        });
        mWebView.setWebViewClient(client);
    }

    // 消息常量
    private static final int MSG_CLEAR_NAME = 2;
    private static final int MSG_LOADING_TIMEOUT = 3;
    private static final int MSG_LOAD_WATCHDOG = 4;
    private static final int MSG_VIDEO_PROBE = 5;
    private static final int MSG_HIDE_LOADING = 6;
    private static final int MSG_HIDE_EXIT_HINT = 7;

    private Handler handler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(@NonNull Message msg) {
            super.handleMessage(msg);
            switch (msg.what) {
                case MSG_CLEAR_NAME:
                    binding.liveName.setText("");
                    break;
                case MSG_LOADING_TIMEOUT:
                    // 兜底：无论如何都要把遮罩收起来
                    doHideLoading();
                    break;
                case MSG_LOAD_WATCHDOG:
                    if (msg.arg1 == loadGeneration && !playingStarted) {
                        onSourceFailed(pendingUrl, "加载超时");
                    }
                    break;
                case MSG_VIDEO_PROBE:
                    if (msg.arg1 == loadGeneration) {
                        probeVideoElement(msg.arg1);
                    }
                    break;
                case MSG_HIDE_LOADING:
                    doHideLoading();
                    break;
                case MSG_HIDE_EXIT_HINT:
                    hideExitHint();
                    break;
                default:
                    break;
            }
        }
    };

    /** 某个频道的第 index 路源地址 */
    private String sourceUrl(Vod channel, int index) {
        if (null == channel) {
            return null;
        }
        List<Vod> sources = channel.getSources();
        if (null == sources || sources.isEmpty()) {
            return index == 0 ? channel.getUrl() : null;
        }
        if (index < 0 || index >= sources.size()) {
            index = 0;
        }
        return sources.get(index).getUrl();
    }

    /** 加载中的文案：频道名 · 源名 (1/2) */
    private String loadingLabel(Vod channel, int index) {
        if (null == channel) {
            return "频道";
        }
        List<Vod> sources = channel.getSources();
        if (null == sources || sources.isEmpty()) {
            return channel.getName();
        }
        if (index < 0 || index >= sources.size()) {
            index = 0;
        }
        if (sources.size() <= 1) {
            return channel.getName();
        }
        return channel.getName() + " · " + sources.get(index).getName()
                + " (" + (index + 1) + "/" + sources.size() + ")";
    }

    /**
     * 加载当前频道的指定源。
     *
     * @param index  源下标
     * @param byAuto true 表示这次是「上一个源播放失败后自动降级」，会计入自动换源次数
     */
    private void startLoad(int index, boolean byAuto) {
        if (null == currentLive || null == mWebView) {
            return;
        }
        List<Vod> sources = currentLive.getSources();
        int total = (null == sources || sources.isEmpty()) ? 1 : sources.size();
        if (index < 0 || index >= total) {
            index = 0;
        }
        String url = sourceUrl(currentLive, index);
        if (null == url || url.trim().isEmpty()) {
            ToastUtils.show(this, "「" + currentLive.getName() + "」没有可用的播放地址", Toast.LENGTH_SHORT);
            return;
        }
        currentSourceIndex = index;
        pendingUrl = url;
        if (byAuto) {
            autoSwitchTried++;
        } else {
            autoSwitchTried = 0;
        }
        loadGeneration++;
        failedGeneration = -1;
        playingStarted = false;
        final int generation = loadGeneration;

        showLoading(loadingLabel(currentLive, index));
        handler.removeMessages(MSG_LOAD_WATCHDOG);
        handler.sendMessageDelayed(
                handler.obtainMessage(MSG_LOAD_WATCHDOG, generation, 0), LOAD_WATCHDOG_MS);
        LogUtil.i(TAG, "startLoad#" + generation + " source#" + index + " " + url);
        mWebView.loadUrl(url);
    }

    /** 手动换源：播放时按左右键 */
    private void switchSource(int dir) {
        if (null == currentLive) {
            return;
        }
        List<Vod> sources = currentLive.getSources();
        int total = (null == sources || sources.isEmpty()) ? 1 : sources.size();
        if (total <= 1) {
            ToastUtils.show(this, "「" + currentLive.getName() + "」只有 1 个源", Toast.LENGTH_SHORT);
            return;
        }
        int next = (currentSourceIndex + dir + total) % total;
        ToastUtils.show(this, "已切到 " + sources.get(next).getName()
                + " (" + (next + 1) + "/" + total + ")", Toast.LENGTH_SHORT);
        startLoad(next, false);
    }

    /** 某一路源播不出来：自动切到下一路，全试完就提示放弃 */
    private void onSourceFailed(String failedUrl, String reason) {
        // 后台停播期间的错误（多半是卸载到 about:blank 引起的）一律忽略：
        // 这时既不该提示，更不该在后台偷偷换源
        if (unloadedForBackground || pendingBackgroundUnload) {
            doHideLoading();
            return;
        }
        if (null == currentLive) {
            doHideLoading();
            return;
        }
        if (isStaleError(failedUrl)) {
            LogUtil.i(TAG, "忽略上一次加载的迟到错误回调 " + failedUrl);
            return;
        }
        if (loadGeneration == failedGeneration) {
            return;
        }
        failedGeneration = loadGeneration;

        List<Vod> sources = currentLive.getSources();
        int total = (null == sources || sources.isEmpty()) ? 1 : sources.size();
        LogUtil.i(TAG, "onSourceFailed[" + reason + "] " + loadingLabel(currentLive, currentSourceIndex)
                + " autoTried=" + autoSwitchTried);

        if (total <= 1) {
            doHideLoading();
            ToastUtils.show(this, "「" + currentLive.getName() + "」暂时播放不了（" + reason + "）",
                    Toast.LENGTH_SHORT);
            return;
        }
        if (autoSwitchTried >= total - 1) {
            doHideLoading();
            ToastUtils.show(this, "「" + currentLive.getName() + "」所有源都播放失败，换个频道试试",
                    Toast.LENGTH_SHORT);
            return;
        }
        int next = (currentSourceIndex + 1) % total;
        String from = sources.get(currentSourceIndex).getName();
        String to = sources.get(next).getName();
        ToastUtils.show(this, "「" + from + "」播放失败，自动切到「" + to + "」", Toast.LENGTH_SHORT);
        startLoad(next, true);
    }

    /**
     * 判断这个错误是不是「上一次加载」迟到的回调。
     *
     * 换源时会立刻发起新的加载，但旧页面的 onReceivedError 可能晚一步才回来；
     * 只要失败地址正好是本频道的另一路源，就说明它属于上一次加载，直接忽略。
     */
    private boolean isStaleError(String url) {
        if (null == url || null == pendingUrl || sameUrl(url, pendingUrl)) {
            return false;
        }
        List<Vod> sources = (null == currentLive) ? null : currentLive.getSources();
        if (null == sources) {
            return false;
        }
        for (Vod source : sources) {
            if (sameUrl(url, source.getUrl())) {
                return true;
            }
        }
        return false;
    }

    private static boolean sameUrl(String a, String b) {
        if (null == a || null == b) {
            return false;
        }
        return a.equals(b) || UpdateService.cleanUrl(a).equals(UpdateService.cleanUrl(b));
    }

    /** 加载完成后看看页面里到底有没有播放器 */
    private void probeVideoElement(final int generation) {
        if (generation != loadGeneration || null == mWebView
                || Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
            return;
        }
        String js = "(function(){try{var n=document.getElementsByTagName('video').length"
                + "+document.getElementsByTagName('iframe').length"
                + "+document.querySelectorAll('video,.xgplayer,#mse,.vjs-tech,.prism-player').length;"
                + "return ''+n;}catch(e){return 'err';}})()";
        try {
            mWebView.evaluateJavascript(js, new ValueCallback<String>() {
                @Override
                public void onReceiveValue(String value) {
                    if (generation != loadGeneration) {
                        return;
                    }
                    LogUtil.i(TAG, "probeVideoElement -> " + value);
                    if (null == value || value.contains("err")) {
                        return;
                    }
                    String num = value.replace("\"", "").trim();
                    try {
                        if (Integer.parseInt(num) <= 0) {
                            onSourceFailed(pendingUrl, "页面上没有找到播放画面");
                        }
                    } catch (NumberFormatException ignore) {
                    }
                }
            });
        } catch (Throwable ignore) {
        }
    }

    /** 播放当前分类里的第 index 个频道 */
    private void playChannel(int index) {
        List<Vod> vods = currentChannelList();
        if (index < 0 || index >= vods.size()) {
            return;
        }
        currentChannelIndex = index;
        currentLive = vods.get(index);
        // 频道默认播第 1 路源
        currentSourceIndex = UpdateService.sourceIndexOf(currentLive, currentLive.getUrl());
        ToastUtils.show(this, currentLive.getName(), Toast.LENGTH_SHORT);
        startLoad(currentSourceIndex, false);
    }

    /** 播放中按上下键：在当前分类里前后换台 */
    private boolean nextChannel(int dir) {
        List<Vod> vods = currentChannelList();
        if (vods.isEmpty()) {
            return true;
        }
        int index = (currentChannelIndex + dir + vods.size()) % vods.size();
        playChannel(index);
        return true;
    }

    private List<Vod> currentChannelList() {
        if (categories.isEmpty()) {
            return new ArrayList<>();
        }
        clampIndex();
        List<Vod> vods = categories.get(currentCategoryIndex).getVods();
        return null == vods ? new ArrayList<Vod>() : vods;
    }

    private void clampIndex() {
        if (categories.isEmpty()) {
            currentCategoryIndex = 0;
            currentChannelIndex = 0;
            return;
        }
        if (currentCategoryIndex < 0) {
            currentCategoryIndex = 0;
        }
        if (currentCategoryIndex >= categories.size()) {
            currentCategoryIndex = categories.size() - 1;
        }
        List<Vod> vods = categories.get(currentCategoryIndex).getVods();
        int size = null == vods ? 0 : vods.size();
        if (currentChannelIndex < 0) {
            currentChannelIndex = 0;
        }
        if (currentChannelIndex >= size) {
            currentChannelIndex = size > 0 ? size - 1 : 0;
        }
    }

    protected void showToast(String text, Context context) {
        binding.liveName.setText(text);
    }

    protected void initWebChromeClient() {
        mWebView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                String url = view.getUrl();
                try {
                    url = URLDecoder.decode(url, "UTF-8");
                } catch (UnsupportedEncodingException ignore) {
                }
                // 后台停播会卸载到 about:blank，别把它当成一次「播放完成」，
                // 否则会在回到前台前误触发一次换源
                if (null == url || url.startsWith("about:")) {
                    return;
                }
                LogUtil.i(TAG, "onProgressChanged " + newProgress + " " + url);
                Vod vod = UpdateService.getByUrl(url);
                if (null != vod) {
                    currentLive = vod;
                    currentSourceIndex = UpdateService.sourceIndexOf(vod, url);
                    binding.liveName.setText(vod.getName() + " " + newProgress + "%");
                }
                if (newProgress >= 100) {
                    // 真实画面已经加载完成：撤掉「加载中」遮罩，露出播放画面
                    playingStarted = true;
                    handler.removeMessages(MSG_LOAD_WATCHDOG);
                    hideLoading();
                    handler.sendMessageDelayed(handler.obtainMessage(MSG_CLEAR_NAME, "noText"), 1000);
                    handler.removeMessages(MSG_VIDEO_PROBE);
                    handler.sendMessageDelayed(
                            handler.obtainMessage(MSG_VIDEO_PROBE, loadGeneration, 0),
                            VIDEO_PROBE_DELAY_MS);
                } else if (newProgress > 12 && isLoadingShown) {
                    // 遮罩显示期间同步进度，让等待更有反馈
                    updateLoadingText(loadingLabel(currentLive, currentSourceIndex), newProgress);
                }
            }

            @Override
            public void onShowCustomView(View view, CustomViewCallback callback) {
                LogUtil.i("WebChromeClient", "onShowCustomView");
                binding.fullscreen.addView(view);
                binding.fullscreen.setVisibility(View.VISIBLE);
            }

            @Override
            public void onPermissionRequest(PermissionRequest request) {
                LogUtil.i("WebChromeClient", "onPermissionRequest " + request.getOrigin());
                request.deny();
            }

            @Override
            public void onHideCustomView() {
                LogUtil.i("WebChromeClient", "onHideCustomView");
                binding.fullscreen.removeAllViews();
                binding.fullscreen.setVisibility(View.GONE);
            }
        });
    }

    @Override
    protected Object getJsInterface() {
        return new JsInterface();
    }

    // ------------------------------------------------------------------ 按键

    /**
     * 平板触屏：
     *   点左半屏 -> 拉起切台菜单（分类 + 频道）
     *   点右半屏 -> 相当于按一次返回键（1.2 秒内连点两次即退出应用）
     */
    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (!isMenuShow && event.getAction() == MotionEvent.ACTION_DOWN) {
            if (event.getX() < screenWidth() / 2f) {
                showMenu(false);
            } else {
                handleBackKey(isExitHintShowing);
            }
            return true;
        }
        return super.dispatchTouchEvent(event);
    }

    /** 当前窗口宽度；拿不到时退回屏幕宽度 */
    private int screenWidth() {
        View decor = getWindow() == null ? null : getWindow().getDecorView();
        if (null != decor && decor.getWidth() > 0) {
            return decor.getWidth();
        }
        return getResources().getDisplayMetrics().widthPixels;
    }

    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_UP) {
            return super.dispatchKeyEvent(event);
        }
        int keyCode = event.getKeyCode();

        if (isMenuShow) {
            return dispatchMenuKey(keyCode, event);
        }

        if (keyCode == KeyEvent.KEYCODE_MENU || keyCode == KeyEvent.KEYCODE_TAB
                || keyCode == KeyEvent.KEYCODE_SETTINGS) {
            showMenu(false);
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
            showMenu(false);
            return true;
        }

        // 播放中左右键换源
        if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
            switchSource(1);
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
            switchSource(-1);
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
            return nextChannel(1);
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
            return nextChannel(-1);
        }
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (event.getRepeatCount() == 0) {
                handleBackKey(isExitHintShowing);
            }
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    /**
     * 返回键：
     *   提示没在显示 -> 弹出「再按一次返回键退出应用」提示，并记下时刻
     *   提示正在显示且距上次在 {@link #DOUBLE_BACK_MS} 内 -> 直接退出应用
     */
    private void handleBackKey(boolean hintShowing) {
        long now = SystemClock.elapsedRealtime();
        if (hintShowing && now - lastBackAt <= DOUBLE_BACK_MS) {
            exitApp();
            return;
        }
        lastBackAt = now;
        showExitHint();
    }

    private void exitApp() {
        finishAffinity();
        System.exit(0);
    }

    /** 菜单打开时的按键：左右切栏、上下选择由 ListView 自己处理 */
    private boolean dispatchMenuKey(int keyCode, KeyEvent event) {
        // 用户真正按了键，之后的选择变化都要当真
        syncingMenu = false;

        if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_TAB
                || keyCode == KeyEvent.KEYCODE_MENU || keyCode == KeyEvent.KEYCODE_SETTINGS) {
            hideMenu();
            return true;
        }
        // 自己处理 OK 键，避免不同 ROM 对 ListView 的回车行为不一致
        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
            if (focusInside(binding.channelList)) {
                int position = binding.channelList.getSelectedItemPosition();
                playChannel(position);
                hideMenu();
            } else if (focusInside(binding.categoryList)) {
                int position = binding.categoryList.getSelectedItemPosition();
                if (position >= 0) {
                    currentCategoryIndex = position;
                    currentChannelIndex = 0;
                    refreshChannelList();
                }
                binding.channelList.requestFocus();
            }
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT && focusInside(binding.categoryList)) {
            binding.channelList.requestFocus();
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT && focusInside(binding.channelList)) {
            binding.categoryList.requestFocus();
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    private boolean focusInside(View group) {
        if (null == group) {
            return false;
        }
        View focus = getCurrentFocus();
        while (null != focus) {
            if (focus == group) {
                return true;
            }
            if (!(focus.getParent() instanceof View)) {
                return false;
            }
            focus = (View) focus.getParent();
        }
        return false;
    }

    // ------------------------------------------------------------------ 菜单

    private void showMenu(boolean focusCategory) {
        if (categories.isEmpty()) {
            ToastUtils.show(this, "频道数据加载中，请稍候", Toast.LENGTH_SHORT);
            return;
        }
        hideExitHint();
        isMenuShow = true;
        binding.menuContainer.setVisibility(View.VISIBLE);
        syncMenu();
        View target = focusCategory ? binding.categoryList : binding.channelList;
        target.post(target::requestFocus);
        applyMenuColumnHighlight();
    }

    /** 让菜单内容与当前分类 / 频道保持一致 */
    private void syncMenu() {
        if (categories.isEmpty()) {
            return;
        }
        clampIndex();
        // 灌数据期间 ListView 会回调 onItemSelected(0)，先屏蔽掉
        syncingMenu = true;
        binding.categoryList.setAdapter(categoryAdapter);
        binding.channelList.setAdapter(channelAdapter);
        binding.categoryList.setSelection(currentCategoryIndex);
        refreshChannelList();
        categoryAdapter.notifyDataSetChanged();
        binding.channelList.setSelection(currentChannelIndex);
    }

    /** 右栏跟随选中的分类刷新 */
    private void refreshChannelList() {
        if (categories.isEmpty()) {
            return;
        }
        clampIndex();
        Live live = categories.get(currentCategoryIndex);
        List<Vod> vods = live.getVods();
        int size = null == vods ? 0 : vods.size();
        int keep = currentChannelIndex;
        binding.categoryTitle.setText(live.getName());
        binding.categoryCount.setText(size + " 个频道");
        channelAdapter.submit(vods);
        binding.channelList.setSelection(keep);
        currentChannelIndex = keep;
        // 左栏的「当前分类」高亮要跟着一起刷新
        categoryAdapter.notifyDataSetChanged();
        updateSourceHint();
    }

    /** 右上角的「当前源 · 共 N 个源」提示 */
    private void updateSourceHint() {
        if (null == currentLive || null == binding.sourceHint) {
            return;
        }
        List<Vod> sources = currentLive.getSources();
        if (null == sources || sources.isEmpty()) {
            binding.sourceHint.setText("");
            return;
        }
        String label = sources.get(Math.min(currentSourceIndex, sources.size() - 1)).getName();
        binding.sourceHint.setText(sources.size() > 1
                ? label + " · 共 " + sources.size() + " 个源"
                : label);
    }

    private void hideMenu() {
        binding.menuContainer.setVisibility(View.GONE);
        isMenuShow = false;
        if (null != mWebView) {
            mWebView.requestFocus();
        }
        binding.webviewWrapper.requestFocus();
    }

    /**
     * 两栏的重点区分：只有当前有焦点的那一栏是亮的，另一栏压暗。
     *
     * <p>光靠 ListView 自己的选中高亮不够 —— 两栏都会保留各自的选中项，
     * 按左右键时从画面上看不出焦点到底在哪一栏。
     */
    private void applyMenuColumnHighlight() {
        boolean leftActive = menuColumn == 0;
        setColumnActive(binding.categoryHeader, binding.categoryList, leftActive);
        setColumnActive(binding.channelHeader, binding.channelList, !leftActive);
    }

    private void setColumnActive(View header, View list, boolean active) {
        float alpha = active ? 1f : MENU_DIM_ALPHA;
        if (null != header) {
            header.setAlpha(alpha);
        }
        if (null != list) {
            list.setAlpha(alpha);
        }
    }

    // ------------------------------------------------------------------ 退出提示

    /** 「再按一次返回键退出应用」提示：水平居中、垂直靠下（不贴底），2.4 秒后自动消失 */
    private void showExitHint() {
        if (null == binding.exitHint) {
            return;
        }
        isExitHintShowing = true;
        handler.removeMessages(MSG_HIDE_EXIT_HINT);
        binding.exitHint.animate().cancel();
        binding.exitHint.setVisibility(View.VISIBLE);
        binding.exitHint.setAlpha(0f);
        binding.exitHint.animate().alpha(1f).setDuration(150L).start();
        handler.sendMessageDelayed(handler.obtainMessage(MSG_HIDE_EXIT_HINT), EXIT_HINT_MS);
    }

    private void hideExitHint() {
        if (null == binding.exitHint) {
            return;
        }
        isExitHintShowing = false;
        handler.removeMessages(MSG_HIDE_EXIT_HINT);
        binding.exitHint.animate().cancel();
        binding.exitHint.animate().alpha(0f).setDuration(180L).withEndAction(new Runnable() {
            @Override
            public void run() {
                if (!isExitHintShowing && null != binding.exitHint) {
                    binding.exitHint.setVisibility(View.GONE);
                }
            }
        }).start();
    }

    // ------------------------------------------------------------------ 列表适配器

    private final CategoryAdapter categoryAdapter = new CategoryAdapter();
    private final ChannelAdapter channelAdapter = new ChannelAdapter();

    private class CategoryAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            return categories.size();
        }

        @Override
        public Object getItem(int position) {
            return categories.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (null == convertView) {
                convertView = LayoutInflater.from(LiveActivity.this)
                        .inflate(R.layout.item_live_category, parent, false);
            }
            Live live = categories.get(position);
            View bar = convertView.findViewById(R.id.catBar);
            TextView name = convertView.findViewById(R.id.catName);
            TextView count = convertView.findViewById(R.id.catCount);
            List<Vod> vods = live.getVods();
            int size = null == vods ? 0 : vods.size();
            boolean active = position == currentCategoryIndex;
            name.setText(live.getName());
            count.setText(size + "");
            bar.setVisibility(active ? View.VISIBLE : View.INVISIBLE);
            name.setTextColor(active ? 0xFFFFFFFF : 0xFFC7CEDB);
            return convertView;
        }
    }

    private class ChannelAdapter extends BaseAdapter {
        private List<Vod> vods = new ArrayList<>();

        void submit(List<Vod> list) {
            vods = (null == list) ? new ArrayList<Vod>() : list;
            notifyDataSetChanged();
        }

        @Override
        public int getCount() {
            return vods.size();
        }

        @Override
        public Object getItem(int position) {
            return vods.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (null == convertView) {
                convertView = LayoutInflater.from(LiveActivity.this)
                        .inflate(R.layout.item_live_channel, parent, false);
            }
            Vod channel = vods.get(position);
            TextView marker = convertView.findViewById(R.id.chMarker);
            TextView name = convertView.findViewById(R.id.chName);
            TextView sourceCount = convertView.findViewById(R.id.chSources);

            boolean playing = sameChannel(channel, currentLive);
            marker.setVisibility(playing ? View.VISIBLE : View.INVISIBLE);
            name.setText(displayNameOf(channel));
            name.setTextColor(playing ? 0xFFFFB37A : 0xFFEDF0F5);

            int total = channel.sourceCount();
            if (total > 1) {
                sourceCount.setVisibility(View.VISIBLE);
                sourceCount.setText(total + " 源");
            } else {
                sourceCount.setVisibility(View.GONE);
            }
            return convertView;
        }
    }

    /** 频道名（去掉可能存在的「1.」之类序号前缀） */
    private String displayNameOf(Vod channel) {
        String name = channel.getName();
        if (null == name) {
            return "";
        }
        return name.replaceFirst("^[0-9]+\\.[ \u3000]*", "");
    }

    private void setupListListeners() {
        binding.categoryList.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (syncingMenu || position == currentCategoryIndex || position < 0
                        || position >= categories.size()) {
                    return;
                }
                currentCategoryIndex = position;
                currentChannelIndex = 0;
                refreshChannelList();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
        binding.categoryList.setOnItemClickListener((parent, view, position, id) -> {
            if (position < 0 || position >= categories.size()) {
                return;
            }
            currentCategoryIndex = position;
            currentChannelIndex = 0;
            refreshChannelList();
            binding.channelList.requestFocus();
        });
        binding.categoryList.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                menuColumn = 0;
                applyMenuColumnHighlight();
            }
        });

        binding.channelList.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (syncingMenu) {
                    return;
                }
                currentChannelIndex = position;
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
        binding.channelList.setOnItemClickListener((parent, view, position, id) -> {
            playChannel(position);
            hideMenu();
        });
        binding.channelList.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                menuColumn = 1;
                applyMenuColumnHighlight();
            }
        });
    }

    // ------------------------------------------------------------------ 布局与 JS 桥

    private void bind() {
        binding = DataBindingUtil.setContentView(this, R.layout.activity_live);
        ViewGroup container = binding.webviewWrapper;
        container.addView(mWebView, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        loadingOverlay = binding.loadingOverlay;
        loadingDots = binding.loadingDots;
        loadingName = binding.loadingName;
        buildLoadingDots();
        setupListListeners();
        // 平板：点菜单右边那块留白即收起菜单
        binding.menuBlank.setOnClickListener(v -> hideMenu());
        // 退出提示：靠下但不贴底、不参与焦点；偏移按屏幕高度的比例算，
        // 平板（600dp 高）和电视（1080dp 高）上观感一致
        if (binding.exitHint.getLayoutParams() instanceof FrameLayout.LayoutParams) {
            FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) binding.exitHint.getLayoutParams();
            lp.bottomMargin = Math.round(getResources().getDisplayMetrics().heightPixels * 0.16f);
            binding.exitHint.setLayoutParams(lp);
        }
        binding.exitHint.setClickable(false);
        binding.exitHint.setFocusable(false);
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        for (ObjectAnimator animator : dotAnimators) {
            animator.cancel();
        }
        dotAnimators.clear();
        super.onDestroy();
    }

    // ------------------------------------------------------------ 加载中动画实现

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    /** 动态生成跳动的圆点（XML 里只放一个空容器，避免重复标签） */
    private void buildLoadingDots() {
        if (null == loadingDots || loadingDots.getChildCount() > 0) {
            return;
        }
        int size = dp(13);
        int gap = dp(8);
        final float rise = dp(17);
        for (int i = 0; i < DOT_COUNT; i++) {
            View dot = new View(this);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
            if (i > 0) {
                lp.leftMargin = gap;
            }
            dot.setLayoutParams(lp);
            GradientDrawable bg = new GradientDrawable();
            bg.setShape(GradientDrawable.OVAL);
            bg.setColor(0xFFFF8A3D);
            dot.setBackground(bg);
            loadingDots.addView(dot);

            PropertyValuesHolder ty = PropertyValuesHolder.ofFloat("translationY", 0f, -rise);
            PropertyValuesHolder alpha = PropertyValuesHolder.ofFloat("alpha", 0.45f, 1f);
            ObjectAnimator animator = ObjectAnimator.ofPropertyValuesHolder(dot, ty, alpha);
            animator.setDuration(360L);
            animator.setStartDelay(i * DOT_STAGGER_MS);
            animator.setRepeatCount(ValueAnimator.INFINITE);
            animator.setRepeatMode(ValueAnimator.REVERSE);
            animator.setInterpolator(new AccelerateDecelerateInterpolator());
            dotAnimators.add(animator);
        }
    }

    /** 显示加载遮罩；已显示时只更新文案，不重复入场动画 */
    private void showLoading(String label) {
        if (null == loadingOverlay) {
            return;
        }
        if (isLoadingShown) {
            updateLoadingText(label, -1);
            return;
        }
        isLoadingShown = true;
        loadingShownAt = System.currentTimeMillis();
        updateLoadingText(label, -1);
        loadingOverlay.setVisibility(View.VISIBLE);
        loadingOverlay.setAlpha(0f);
        loadingOverlay.animate().alpha(1f).setDuration(180L).start();
        for (ObjectAnimator animator : dotAnimators) {
            animator.start();
        }
        // 兜底：超时后强制收起，避免永远停在加载态
        handler.removeMessages(MSG_LOADING_TIMEOUT);
        handler.sendMessageDelayed(handler.obtainMessage(MSG_LOADING_TIMEOUT), LOADING_TIMEOUT_MS);
    }

    /** 隐藏加载遮罩；为保证不「闪一下」，至少展示 LOADING_MIN_MS */
    private void hideLoading() {
        if (null == loadingOverlay || !isLoadingShown) {
            return;
        }
        long wait = LOADING_MIN_MS - (System.currentTimeMillis() - loadingShownAt);
        if (wait > 0) {
            handler.removeMessages(MSG_HIDE_LOADING);
            handler.sendMessageDelayed(handler.obtainMessage(MSG_HIDE_LOADING), wait);
            return;
        }
        doHideLoading();
    }

    /** 立即淡出加载遮罩并停止圆点动画 */
    private void doHideLoading() {
        if (null == loadingOverlay || !isLoadingShown) {
            return;
        }
        isLoadingShown = false;
        handler.removeMessages(MSG_LOADING_TIMEOUT);
        handler.removeMessages(MSG_HIDE_LOADING);
        for (ObjectAnimator animator : dotAnimators) {
            animator.cancel();
        }
        loadingOverlay.animate().alpha(0f).setDuration(220L).withEndAction(new Runnable() {
            @Override
            public void run() {
                if (!isLoadingShown && null != loadingOverlay) {
                    loadingOverlay.setVisibility(View.GONE);
                }
            }
        }).start();
    }

    /** 更新加载文案；progress 传负数表示不带百分比 */
    private void updateLoadingText(String label, int progress) {
        if (null == loadingName) {
            return;
        }
        String text = (null == label || label.isEmpty()) ? "频道" : label;
        if (progress < 0) {
            loadingName.setText("正在加载 " + text + " …");
        } else {
            loadingName.setText("正在加载 " + text + " … " + progress + "%");
        }
    }

    public class JsInterface {

        @JavascriptInterface
        public void toast(String message) {
            try {
                ToastUtils.show(MyApplication.getContext(), message, Toast.LENGTH_SHORT);
            } catch (Throwable ignore) {
            }
        }

        @JavascriptInterface
        public void message(String service, String data) {
            LogUtil.i(TAG, "service " + service + " data " + data);
            if ("menuShow".equals(service)) {
                isMenuShow = "1".equals(data);
                return;
            }
            if ("js".equals(service)) {
                Util.evalOnUi(mWebView, data);
                return;
            }
            if ("key".equals(service)) {
                keyCodeAllByCode(data);
                return;
            }
            if ("keyNum".equals(service)) {
                keyEventAll(Integer.parseInt(data));
            }
        }

        @JavascriptInterface
        public String postJson(String url, String header, String requestBody) {
            Map<String, String> headerMap = JsonUtil.fromJson(header, JsonTypes.STRING_MAP);
            if (!url.startsWith("http")) {
                return FileUtil.readExt(MyApplication.getAppContext(), "tv-web/" + url);
            }
            return HttpUtil.postJson(url, headerMap, requestBody);
        }

        @JavascriptInterface
        public String getJson(String url, String header) {
            Map<String, String> headerMap = JsonUtil.fromJson(header, JsonTypes.STRING_MAP);
            if (!url.startsWith("http")) {
                return FileUtil.readExt(MyApplication.getAppContext(), "tv-web/" + url);
            }
            return HttpUtil.getJson(url, headerMap);
        }

        @JavascriptInterface
        public String getHtml(String url, String header) {
            Map<String, String> headerMap = JsonUtil.fromJson(header, JsonTypes.STRING_MAP);
            return HttpUtil.getJson(url, headerMap);
        }
    }
}
