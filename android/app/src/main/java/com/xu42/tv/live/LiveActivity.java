package com.xu42.tv.live;

import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.webkit.JavascriptInterface;
import android.webkit.PermissionRequest;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.widget.AbsListView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.databinding.DataBindingUtil;

import com.google.gson.reflect.TypeToken;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.xu42.tv.live.call.StringCallback;
import com.xu42.tv.live.dao.HistoryDaoX;
import com.xu42.tv.live.databinding.ActivityLiveBinding;
import com.xu42.tv.live.databinding.DialogExitBinding;
import com.xu42.tv.live.databinding.ItemHzLiveBinding;
import com.xu42.tv.live.domain.HzItem;
import com.xu42.tv.live.domain.live.Live;
import com.xu42.tv.live.domain.live.Vod;
import com.xu42.tv.live.impl.BaseBindingAdapter;
import com.xu42.tv.live.impl.BaseViewHolder;
import com.xu42.tv.live.impl.WebViewClientImpl;
import com.xu42.tv.live.service.FavoriteService;
import com.xu42.tv.live.service.UpdateService;
import com.xu42.tv.live.util.FileUtil;
import com.xu42.tv.live.util.HttpUtil;
import com.xu42.tv.live.util.JsonUtil;
import com.xu42.tv.live.util.LogUtil;
import com.xu42.tv.live.util.Util;
import com.xu42.tv.live.utils.ToastUtils;

/**
 * 电视直播。
 *
 * - 进入即播放上一次看的频道，没有记录时播 CCTV-1
 * - 上下左右：快速切台
 * - 设置(MENU)键：打开频道菜单并定位到「源」，左右切换播放源
 * - OK 键：打开频道菜单，定位到频道列表
 * - 数字键：跳到收藏栏的第 N 个频道
 */
public class LiveActivity extends BaseActivity {
    protected String TAG = "LiveActivity";

    protected ActivityLiveBinding binding;
    private Context thisContext;

    /** 当前播放的频道（静态保存，页面来回切换时保留） */
    private static Vod currentLive = null;

    /** 全量直播数据（含收藏分组） */
    private final List<Live> allLives = new ArrayList<>();
    /** 按当前源站过滤后的分组，界面与导航都以它为准 */
    private List<Live> provinces = new ArrayList<>();
    private int currentProvinceIndex = 0;
    private int currentDetailIndex = 0;

    /** 源站列表：[0] 为「全部源」，其余按数据里的频道数量排序 */
    private final List<String> sourceNames = new ArrayList<>();
    private int sourceIndex = 0;

    /** 把上游站点归到用户认得出来的名字 */
    private static final String[][] SOURCE_RULES = {
            {"tv.cctv.com", "央视网"},
            {"yangshipin.cn", "央视频"},
            {"mgtv.com", "芒果TV"},
            {"youku.com", "优酷"},
            {"iqiyi.com", "爱奇艺"},
            {"le.com", "乐视"},
            {"ixigua.com", "西瓜视频"},
            {"bilibili.com", "哔哩哔哩"},
    };
    private static final String SOURCE_ALL = "全部源";
    private static final String SOURCE_OTHER = "各地广电";

    private DialogExitBinding exitDialogBinding;
    private boolean isExitDialogShowing = false;
    private boolean isMenuShow = false;

    private FavoriteService favoriteService;

    // ---------------------------------------------------------------- 频道加载中动画
    /** 跳动的圆点数量 */
    private static final int DOT_COUNT = 5;
    /** 每个圆点的错峰启动间隔（毫秒），形成波浪感 */
    private static final long DOT_STAGGER_MS = 110L;
    /** 遮罩最短展示时长，避免「闪一下」的突兀感 */
    private static final long LOADING_MIN_MS = 600L;
    /** 兜底超时：即使没收到 100% 也要收起遮罩，防止卡死在加载态 */
    private static final long LOADING_TIMEOUT_MS = 15000L;

    private FrameLayout loadingOverlay;
    private LinearLayout loadingDots;
    private TextView loadingName;
    private final List<ObjectAnimator> dotAnimators = new ArrayList<>();
    private boolean isLoadingShown = false;
    private long loadingShownAt = 0L;

    @Override
    protected void createInit() {
        bind();
        UpdateService.baseFolder = this.getFilesDir().getPath();
        UpdateService.updateRes(this);
        UpdateService.initTvData();
        thisContext = this;
        favoriteService = FavoriteService.getInstance(this);

        if (null == currentLive) {
            // 记忆上次看的频道；没有记录时默认 CCTV-1
            currentLive = HistoryDaoX.currentChannel(this);
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

        showLoading(currentLive.getName());
        mWebView.loadUrl(currentLive.getUrl());
        ToastUtils.show(this, "已切到 " + currentLive.getName() + "，按设置键可切换源", Toast.LENGTH_SHORT);
    }

    /** 后台加载频道数据，回主线程构建源站与列表 */
    private void initData() {
        new Thread(() -> {
            List<Live> result = UpdateService.getByLivesWithFavorites(this);
            runOnUiThread(() -> {
                allLives.clear();
                allLives.addAll(result);
                buildSources();
                applySourceFilter();
                locateCurrent();
                showSourceName();
                showCurrentProvince(false);
            });
        }).start();
    }

    /** 统计各源站的频道数量，生成源站列表 */
    private void buildSources() {
        final Map<String, Integer> counter = new LinkedHashMap<>();
        for (Live live : allLives) {
            if ("favorite".equals(live.getTag())) {
                continue;
            }
            for (Vod vod : live.getVods()) {
                String name = sourceNameOf(vod.getUrl());
                Integer old = counter.get(name);
                counter.put(name, null == old ? 1 : old + 1);
            }
        }
        List<Map.Entry<String, Integer>> entries = new ArrayList<>(counter.entrySet());
        Collections.sort(entries, new Comparator<Map.Entry<String, Integer>>() {
            @Override
            public int compare(Map.Entry<String, Integer> a, Map.Entry<String, Integer> b) {
                return b.getValue() - a.getValue();
            }
        });

        sourceNames.clear();
        sourceNames.add(SOURCE_ALL);
        for (Map.Entry<String, Integer> entry : entries) {
            sourceNames.add(entry.getKey());
        }
        if (sourceIndex >= sourceNames.size()) {
            sourceIndex = 0;
        }
    }

    private String sourceNameOf(String url) {
        String host = hostOf(url);
        for (String[] rule : SOURCE_RULES) {
            if (host.contains(rule[0])) {
                return rule[1];
            }
        }
        return SOURCE_OTHER;
    }

    private String hostOf(String url) {
        if (null == url) {
            return "";
        }
        String u = url;
        int scheme = u.indexOf("://");
        if (scheme > 0) {
            u = u.substring(scheme + 3);
        }
        int slash = u.indexOf('/');
        if (slash > 0) {
            u = u.substring(0, slash);
        }
        int colon = u.indexOf(':');
        if (colon > 0) {
            u = u.substring(0, colon);
        }
        return u;
    }

    /** 按当前源站重新构建分组（收藏分组始终保留） */
    private void applySourceFilter() {
        String wantSource = sourceNames.isEmpty() ? SOURCE_ALL : sourceNames.get(sourceIndex);
        boolean all = SOURCE_ALL.equals(wantSource);

        List<Live> filtered = new ArrayList<>();
        for (Live live : allLives) {
            if ("favorite".equals(live.getTag())) {
                filtered.add(live);
                continue;
            }
            List<Vod> vods = new ArrayList<>();
            for (Vod vod : live.getVods()) {
                if (all || wantSource.equals(sourceNameOf(vod.getUrl()))) {
                    vods.add(vod);
                }
            }
            if (vods.isEmpty()) {
                continue;
            }
            Live group = new Live();
            group.setName(live.getName());
            group.setTag(live.getTag());
            group.setIndex(live.getIndex());
            group.setVods(vods);
            filtered.add(group);
        }
        provinces = filtered;
        clampIndex();
    }

    private void clampIndex() {
        if (provinces.isEmpty()) {
            currentProvinceIndex = 0;
            currentDetailIndex = 0;
            return;
        }
        if (currentProvinceIndex < 0) {
            currentProvinceIndex = 0;
        }
        if (currentProvinceIndex >= provinces.size()) {
            currentProvinceIndex = provinces.size() - 1;
        }
        List<Vod> vods = provinces.get(currentProvinceIndex).getVods();
        if (currentDetailIndex >= vods.size()) {
            currentDetailIndex = Math.max(0, vods.size() - 1);
        }
    }

    /** 把当前正在播放的频道定位到（过滤后的）列表位置 */
    private void locateCurrent() {
        clampIndex();
        if (null == currentLive) {
            return;
        }
        for (int i = 0; i < provinces.size(); i++) {
            List<Vod> vods = provinces.get(i).getVods();
            for (int j = 0; j < vods.size(); j++) {
                if (currentLive.getUrl() != null
                        && currentLive.getUrl().equals(vods.get(j).getUrl())) {
                    currentProvinceIndex = i;
                    currentDetailIndex = j;
                    return;
                }
            }
        }
    }

    // ------------------------------------------------------------------ 播放

    private long lastTime = 0;

    protected void initWebViewClient() {
        mWebView.setWebViewClient(new WebViewClientImpl(getBaseContext(), mWebView, 1));
    }

    private Handler handler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(@NonNull Message msg) {
            super.handleMessage(msg);
            switch (msg.what) {
                case 1:
                    String url = (String) msg.obj;
                    if (null != currentLive && null != mWebView
                            && null != url && url.equals(currentLive.getUrl())) {
                        mWebView.loadUrl(url);
                    }
                    break;
                case 2:
                    binding.liveName.setText("");
                    break;
                case 3:
                    // 加载兜底超时：直接收起遮罩
                    doHideLoading();
                    break;
                default:
                    break;
            }
        }
    };

    /** 上下左右在「当前可见的」频道之间切换 */
    private boolean goNext(String nextType) {
        if (provinces.isEmpty()) {
            return true;
        }
        clampIndex();
        List<Vod> vods = provinces.get(currentProvinceIndex).getVods();
        if (vods.isEmpty()) {
            return true;
        }
        int tag = currentProvinceIndex;
        int detail = currentDetailIndex;
        switch (nextType) {
            case "up":
                detail = (detail <= 0) ? vods.size() - 1 : detail - 1;
                break;
            case "down":
                detail = (detail >= vods.size() - 1) ? 0 : detail + 1;
                break;
            case "left":
                tag = (tag <= 0) ? provinces.size() - 1 : tag - 1;
                detail = 0;
                break;
            case "right":
                tag = (tag >= provinces.size() - 1) ? 0 : tag + 1;
                detail = 0;
                break;
            default:
                return true;
        }
        List<Vod> target = provinces.get(tag).getVods();
        if (target.isEmpty()) {
            return true;
        }
        if (detail >= target.size()) {
            detail = 0;
        }
        currentProvinceIndex = tag;
        currentDetailIndex = detail;
        currentLive = target.get(detail);
        showToast(currentLive.getName(), this);
        showLoading(currentLive.getName());
        handler.sendMessageDelayed(handler.obtainMessage(1, currentLive.getUrl()), 700);
        return true;
    }

    private void playChannel(Vod channel) {
        if (null == channel || null == channel.getUrl()) {
            return;
        }
        currentLive = channel;
        showLoading(channel.getName());
        if (null != mWebView) {
            mWebView.loadUrl(channel.getUrl());
        }
        ToastUtils.show(this, "已切到 " + channel.getName(), Toast.LENGTH_SHORT);
    }

    protected void showToast(String text, Context context) {
        binding.liveName.setText(text);
    }

    protected void initWebChromeClient() {
        mWebView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                isMenuShow = false;
                String url = view.getUrl();
                try {
                    url = URLDecoder.decode(url, "UTF-8");
                } catch (UnsupportedEncodingException ignore) {
                }
                LogUtil.i(TAG, "onProgressChangedX" + url);
                Vod vod = UpdateService.getByUrl(url);
                if (null != vod) {
                    currentLive = vod;
                    binding.liveName.setText(currentLive.getName() + " " + newProgress + "%");
                }
                if (newProgress >= 100) {
                    // 真实画面已加载完成：撤掉「加载中」遮罩，露出播放画面
                    HistoryDaoX.updateChannel(thisContext, url);
                    hideLoading();
                    handler.sendMessageDelayed(handler.obtainMessage(2, "noText"), 1000);
                } else if (newProgress > 12 && isLoadingShown) {
                    // 遮罩显示期间同步进度，让等待更有反馈
                    updateLoadingText(null != currentLive ? currentLive.getName() : null, newProgress);
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

    public boolean dispatchTouchEvent(MotionEvent event) {
        if (!isMenuShow && event.getAction() == MotionEvent.ACTION_DOWN) {
            showMenu(false);
            return true;
        }
        return super.dispatchTouchEvent(event);
    }

    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_UP) {
            return super.dispatchKeyEvent(event);
        }
        int keyCode = event.getKeyCode();

        if (isExitDialogShowing) {
            if (keyCode == KeyEvent.KEYCODE_BACK) {
                hideExitDialog();
                return true;
            }
            return super.dispatchKeyEvent(event);
        }

        if (isMenuShow) {
            if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_MENU
                    || keyCode == KeyEvent.KEYCODE_TAB) {
                hideMenu();
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                int dir = keyCode == KeyEvent.KEYCODE_DPAD_LEFT ? -1 : 1;
                if (focusInside(binding.sourceRow)) {
                    switchSource(dir);
                    return true;
                }
                if (focusInside(binding.prevProvinceArea) || focusInside(binding.nextProvinceArea)) {
                    switchCategory(dir);
                    return true;
                }
            }
            return super.dispatchKeyEvent(event);
        }

        if (keyCode == KeyEvent.KEYCODE_MENU || keyCode == KeyEvent.KEYCODE_TAB) {
            // 设置键：直接定位到「源」，方便切换播放源
            showMenu(true);
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
            showMenu(false);
            return true;
        }

        if (isDigitKey(keyCode)) {
            digitInputHandler.removeCallbacks(commitDigitRunnable);
            digitBuffer.append(digitFromKeyCode(keyCode));
            try {
                ToastUtils.show(this, "输入: " + digitBuffer.toString(), Toast.LENGTH_SHORT);
            } catch (Throwable ignore) {
            }
            digitInputHandler.postDelayed(commitDigitRunnable, DIGIT_TIMEOUT_MS);
            return true;
        }

        if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
            return goNext("right");
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
            return goNext("left");
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
            return goNext("down");
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
            return goNext("up");
        }
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            showExitDialog();
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

    private void showMenu(boolean focusSource) {
        if (provinces.isEmpty()) {
            ToastUtils.show(this, "频道数据加载中，请稍候", Toast.LENGTH_SHORT);
            return;
        }
        isMenuShow = true;
        binding.menuContainer.setVisibility(View.VISIBLE);

        showSourceName();
        showCurrentProvince(false);

        binding.prevSourceArea.setOnClickListener(v -> switchSource(-1));
        binding.nextSourceArea.setOnClickListener(v -> switchSource(1));
        binding.prevProvinceArea.setOnClickListener(v -> switchCategory(-1));
        binding.nextProvinceArea.setOnClickListener(v -> switchCategory(1));
        binding.menuContainer.setOnClickListener(v -> hideMenu());

        View target = focusSource ? binding.prevSourceArea : binding.channelList;
        binding.channelList.setSelection(currentDetailIndex);
        target.post(target::requestFocus);
    }

    private void hideMenu() {
        binding.menuContainer.setVisibility(View.GONE);
        isMenuShow = false;
        binding.menuContainer.setOnClickListener(null);
    }

    private void switchSource(int dir) {
        if (sourceNames.size() <= 1) {
            showSourceName();
            return;
        }
        sourceIndex = (sourceIndex + dir + sourceNames.size()) % sourceNames.size();
        applySourceFilter();
        locateCurrent();
        showSourceName();
        showCurrentProvince(false);
    }

    private void switchCategory(int dir) {
        if (provinces.isEmpty()) {
            return;
        }
        currentProvinceIndex = (currentProvinceIndex + dir + provinces.size()) % provinces.size();
        currentDetailIndex = 0;
        showCurrentProvince(false);
    }

    private void showSourceName() {
        if (sourceNames.isEmpty()) {
            binding.sourceName.setText(SOURCE_ALL);
            return;
        }
        String name = sourceNames.get(sourceIndex);
        binding.sourceName.setText(SOURCE_ALL.equals(name) ? name : ("源 · " + name));
    }

    private void showCurrentProvince(boolean focusList) {
        if (provinces.isEmpty()) {
            binding.provinceName.setText("暂无频道");
            setupChannelList(new ArrayList<>(), focusList);
            return;
        }
        clampIndex();
        Live currentProvince = provinces.get(currentProvinceIndex);
        List<Vod> vods = currentProvince.getVods();
        binding.provinceName.setText(currentProvince.getName() + "(" + vods.size() + ")");
        setupChannelList(vods, focusList);
    }

    private void setupChannelList(List<Vod> channels, boolean focusList) {
        ArrayAdapter<Vod> adapter = new ArrayAdapter<Vod>(this, android.R.layout.simple_list_item_1, channels) {
            @NonNull
            @Override
            public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
                Button btn;
                if (convertView == null) {
                    btn = new Button(getContext());
                    btn.setLayoutParams(new AbsListView.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT));
                    btn.setTextColor(Color.WHITE);
                    btn.setTextSize(16);
                    btn.setPadding(24, 16, 24, 16);
                    btn.setBackgroundResource(R.drawable.menu_button_background);
                    btn.setClickable(false);
                    btn.setFocusable(false);
                } else {
                    btn = (Button) convertView;
                    if (!(btn.getLayoutParams() instanceof AbsListView.LayoutParams)) {
                        btn.setLayoutParams(new AbsListView.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.WRAP_CONTENT));
                    }
                }
                Vod channel = getItem(position);
                if (null != channel) {
                    boolean isCurrent = null != currentLive
                            && channel.getUrl() != null
                            && channel.getUrl().equals(currentLive.getUrl());
                    btn.setText((isCurrent ? "▶ " : "") + channel.getName());
                }
                return btn;
            }
        };
        binding.channelList.setAdapter(adapter);
        binding.channelList.setOnItemClickListener((parent, view, position, id) -> {
            if (position < 0 || position >= channels.size()) {
                return;
            }
            Vod channel = channels.get(position);
            currentDetailIndex = position;
            playChannel(channel);
            hideMenu();
        });
        binding.channelList.setSelection(currentDetailIndex);
        if (focusList) {
            binding.channelList.post(() -> binding.channelList.requestFocus());
        }
    }

    // ------------------------------------------------------------------ 收藏 / 数字键

    private void toggleFavorite(Vod vod) {
        if (null == vod) {
            return;
        }
        if (favoriteService.isFavorite(vod.getUrl())) {
            favoriteService.removeFavorite(vod.getUrl());
            ToastUtils.show(this, "已取消收藏：" + vod.getName(), Toast.LENGTH_SHORT);
        } else {
            favoriteService.addFavorite(vod);
            ToastUtils.show(this, "已收藏：" + vod.getName(), Toast.LENGTH_SHORT);
        }
        initData();
    }

    private final StringBuilder digitBuffer = new StringBuilder();
    private final Handler digitInputHandler = new Handler(Looper.getMainLooper());
    private static final int DIGIT_TIMEOUT_MS = 1000;
    private final Runnable commitDigitRunnable = new Runnable() {
        @Override
        public void run() {
            String s = digitBuffer.toString();
            digitBuffer.setLength(0);
            if (s.isEmpty()) {
                return;
            }
            try {
                jumpToFavoriteByNumber(Integer.parseInt(s));
            } catch (NumberFormatException ignore) {
            }
        }
    };

    private boolean isDigitKey(int keyCode) {
        return keyCode >= KeyEvent.KEYCODE_0 && keyCode <= KeyEvent.KEYCODE_9;
    }

    private char digitFromKeyCode(int keyCode) {
        return (char) ('0' + (keyCode - KeyEvent.KEYCODE_0));
    }

    /** 数字键跳到「收藏」分组的第 N 个频道并播放 */
    private void jumpToFavoriteByNumber(int num) {
        Live favoriteLive = null;
        int tagIndex = -1;
        for (int i = 0; i < provinces.size(); i++) {
            if ("favorite".equals(provinces.get(i).getTag())) {
                favoriteLive = provinces.get(i);
                tagIndex = i;
                break;
            }
        }
        if (null == favoriteLive || null == favoriteLive.getVods()) {
            return;
        }
        int idx = num - 1;
        if (idx < 0 || idx >= favoriteLive.getVods().size()) {
            return;
        }
        currentProvinceIndex = tagIndex;
        currentDetailIndex = idx;
        showToast(favoriteLive.getVods().get(idx).getName(), this);
        playChannel(favoriteLive.getVods().get(idx));
    }

    // ------------------------------------------------------------------ 退出对话框

    private void showExitDialog() {
        if (null == exitDialogBinding) {
            initExitDialog();
        }
        if (null == exitDialogBinding) {
            return;
        }
        isExitDialogShowing = true;
        exitDialogBinding.exitDialogContainer.setVisibility(View.VISIBLE);
        setupHzListInExit();
        updateFavoriteButtonInDialog();
        exitDialogBinding.btnCancel.post(() -> exitDialogBinding.btnCancel.requestFocus());
    }

    private void updateFavoriteButtonInDialog() {
        if (null != currentLive && null != favoriteService
                && favoriteService.isFavorite(currentLive.getUrl())) {
            exitDialogBinding.btnFavorite.setText("取消收藏当前频道");
        } else {
            exitDialogBinding.btnFavorite.setText("收藏当前频道");
        }
    }

    private void hideExitDialog() {
        if (null != exitDialogBinding) {
            isExitDialogShowing = false;
            exitDialogBinding.exitDialogContainer.setVisibility(View.GONE);
        }
        mWebView.requestFocus();
    }

    private void initExitDialog() {
        View dialogView = findViewById(R.id.exitDialog);
        exitDialogBinding = DataBindingUtil.bind(dialogView);
        if (null == exitDialogBinding) {
            return;
        }
        exitDialogBinding.exitDialogContainer.setFocusable(true);
        exitDialogBinding.exitDialogContainer.setFocusableInTouchMode(true);

        exitDialogBinding.btnFavorite.setOnClickListener(v -> {
            toggleFavorite(currentLive);
            updateFavoriteButtonInDialog();
            exitDialogBinding.btnFavorite.post(() -> exitDialogBinding.btnFavorite.requestFocus());
        });
        exitDialogBinding.btnCancel.setOnClickListener(v -> hideExitDialog());
        exitDialogBinding.btnBackHome.setOnClickListener(v -> {
            hideExitDialog();
            toHome();
        });
        exitDialogBinding.btnExitApp.setOnClickListener(v -> {
            finishAffinity();
            System.exit(0);
        });
        exitDialogBinding.dialogBackdrop.setOnClickListener(v -> hideExitDialog());
    }

    private void toHome() {
        startActivity(new Intent(this, HomeActivity.class));
        finish();
    }

    // ------------------------------------------------------------------ 画质（页面提供时）

    protected static String videoQualityData = null;

    private void setupHzListInExit() {
        List<HzItem> hzItems = new ArrayList<>();
        if (null != videoQualityData) {
            try {
                hzItems = JsonUtil.fromJson(videoQualityData, new TypeToken<List<HzItem>>() {
                }.getType());
            } catch (Exception ignore) {
            }
        }
        BaseBindingAdapter hzAdapter = new BaseBindingAdapter<HzItem, ItemHzLiveBinding>(hzItems, R.layout.item_hz_live) {
            @Override
            public void doBindViewHolder(BaseViewHolder<ItemHzLiveBinding> holder, HzItem item) {
                holder.getBinding().setVariable(BR.item, item);
                holder.getBinding().setVariable(BR.itemPresenter, ItemPresenter);
            }
        };
        hzAdapter.setItemPresenter(new HzLiveBindPresenter());
        exitDialogBinding.hzListInExit.setLayoutManager(
                new androidx.recyclerview.widget.LinearLayoutManager(this,
                        androidx.recyclerview.widget.LinearLayoutManager.HORIZONTAL, false));
        exitDialogBinding.hzListInExit.setAdapter(hzAdapter);
        exitDialogBinding.hzListInExit.addOnChildAttachStateChangeListener(
                new androidx.recyclerview.widget.RecyclerView.OnChildAttachStateChangeListener() {
                    @Override
                    public void onChildViewAttachedToWindow(View view) {
                        View btn = view.findViewById(R.id.hzItem);
                        if (null != btn) {
                            if (btn.getId() == View.NO_ID) {
                                btn.setId(View.generateViewId());
                            }
                            btn.setNextFocusUpId(exitDialogBinding.btnCancel.getId());
                            btn.setNextFocusDownId(exitDialogBinding.btnFavorite.getId());
                        }
                    }

                    @Override
                    public void onChildViewDetachedFromWindow(View view) {
                    }
                });
    }

    public class HzLiveBindPresenter implements com.xu42.tv.live.impl.IBaseBindingPresenter {
        public void onClick(HzItem item) {
            if (item.getAction() != null && item.getAction().trim().length() > 0) {
                Util.evalOnUi(mWebView, item.getAction());
            } else if (item.getId() != null) {
                String js = "$$(\\\"#" + item.getId() + "\\\").click()";
                Util.evalOnUi(mWebView, js);
            }
        }
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
    private void showLoading(String channelName) {
        if (null == loadingOverlay) {
            return;
        }
        if (isLoadingShown) {
            updateLoadingText(channelName, -1);
            return;
        }
        isLoadingShown = true;
        loadingShownAt = System.currentTimeMillis();
        updateLoadingText(channelName, -1);
        loadingOverlay.setVisibility(View.VISIBLE);
        loadingOverlay.setAlpha(0f);
        loadingOverlay.animate().alpha(1f).setDuration(180L).start();
        for (ObjectAnimator animator : dotAnimators) {
            animator.start();
        }
        // 兜底：超时后强制收起，避免永远停在加载态
        handler.removeMessages(3);
        handler.sendMessageDelayed(handler.obtainMessage(3), LOADING_TIMEOUT_MS);
    }

    /** 隐藏加载遮罩；为保证不「闪一下」，至少展示 LOADING_MIN_MS */
    private void hideLoading() {
        if (null == loadingOverlay || !isLoadingShown) {
            return;
        }
        long wait = LOADING_MIN_MS - (System.currentTimeMillis() - loadingShownAt);
        if (wait > 0) {
            handler.postDelayed(hideLoadingRunnable, wait);
            return;
        }
        doHideLoading();
    }

    private final Runnable hideLoadingRunnable = new Runnable() {
        @Override
        public void run() {
            doHideLoading();
        }
    };

    /** 立即淡出加载遮罩并停止圆点动画 */
    private void doHideLoading() {
        if (null == loadingOverlay || !isLoadingShown) {
            return;
        }
        isLoadingShown = false;
        handler.removeMessages(3);
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
    private void updateLoadingText(String channelName, int progress) {
        if (null == loadingName) {
            return;
        }
        String name = (null == channelName || channelName.isEmpty()) ? "频道" : channelName;
        if (progress < 0) {
            loadingName.setText("正在加载 " + name + " …");
        } else {
            loadingName.setText("正在加载 " + name + " … " + progress + "%");
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
            if ("history.save".equals(service)) {
                HistoryDaoX.save(thisContext, data, new StringCallback() {
                    @Override
                    public void data(String data) {
                        runOnUiThread(() -> {
                            if (null != mWebView) {
                                mWebView.loadUrl(data);
                            }
                        });
                    }
                });
                return;
            }
            if ("history.update".equals(service)) {
                HistoryDaoX.update(thisContext, data);
                return;
            }
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
                return;
            }
            if ("videoQuality".equals(service)) {
                videoQualityData = data;
            }
        }

        @JavascriptInterface
        public String postJson(String url, String header, String requestBody) {
            Map<String, String> headerMap = JsonUtil.fromJson(header,
                    new TypeToken<Map<String, String>>() {
                    }.getType());
            if (!url.startsWith("http")) {
                return FileUtil.readExt(MyApplication.getAppContext(), "tv-web/" + url);
            }
            return HttpUtil.postJson(url, headerMap, requestBody);
        }

        @JavascriptInterface
        public String getJson(String url, String header) {
            Map<String, String> headerMap = JsonUtil.fromJson(header,
                    new TypeToken<Map<String, String>>() {
                    }.getType());
            if (!url.startsWith("http")) {
                return FileUtil.readExt(MyApplication.getAppContext(), "tv-web/" + url);
            }
            return HttpUtil.getJson(url, headerMap);
        }

        @JavascriptInterface
        public String getHtml(String url, String header) {
            Map<String, String> headerMap = JsonUtil.fromJson(header,
                    new TypeToken<Map<String, String>>() {
                    }.getType());
            return HttpUtil.getJson(url, headerMap);
        }
    }
}
