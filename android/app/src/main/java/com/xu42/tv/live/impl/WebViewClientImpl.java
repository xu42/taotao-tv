package com.xu42.tv.live.impl;

import android.content.Context;
import android.graphics.Bitmap;
import android.net.http.SslError;
import android.os.Build;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.SslErrorHandler;
import android.webkit.ValueCallback;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.annotation.RequiresApi;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;

import com.xu42.tv.live.MyApplication;
import com.xu42.tv.live.util.AppConfig;
import com.xu42.tv.live.util.AppVersionUtils;
import com.xu42.tv.live.util.ConstantMy;
import com.xu42.tv.live.util.FileUtil;
import com.xu42.tv.live.util.HttpUtil;
import com.xu42.tv.live.util.JsonUtil;
import com.xu42.tv.live.util.LogUtil;
import com.xu42.tv.live.util.TplUtil;
import com.xu42.tv.live.util.Util;

/**
 * 直播页专用的 WebViewClient（系统内核 android.webkit）。
 *
 * 两件核心事情：
 * 1) 内置页面走「伪源」https://tv.xu42.com/tv-web/ —— 任何路径里带 tv-web/ 的请求
 *    都由本类从 APK 的 assets 读取返回，不产生网络请求；
 * 2) 上报主文档加载失败，供直播页做「自动换源」。
 */
public class WebViewClientImpl extends WebViewClient {
    private static final String TAG = "WebViewClient";
    private final Context context;
    private final WebView mWebView;

    /**
     * 主文档加载失败的回调。直播页用它来触发「自动换源」：
     * 某个台的默认源（央视网）打不开时，自动切到下一个源。
     */
    public interface LoadStateListener {
        void onMainFrameError(String url, String reason);
    }

    private LoadStateListener loadStateListener;

    public void setLoadStateListener(LoadStateListener listener) {
        this.loadStateListener = listener;
    }

    private void notifyMainFrameError(String url, String reason) {
        LogUtil.e(TAG, "mainFrameError: " + reason + ", url: " + url);
        if (null != loadStateListener) {
            loadStateListener.onMainFrameError(url, reason);
        }
    }

    public WebViewClientImpl(Context context, WebView mWebView) {
        this.context = context;
        this.mWebView = mWebView;
    }

    @Override
    @RequiresApi(Build.VERSION_CODES.O)
    public boolean onRenderProcessGone(WebView view, RenderProcessGoneDetail detail) {
        LogUtil.i(TAG, "onRenderProcessGone");
        view.clearCache(false);
        view.clearHistory();
        return true;
    }

    @Override
    public void onReceivedSslError(WebView webView, SslErrorHandler handler, SslError error) {
        LogUtil.i(TAG, "onReceivedSslError");
        handler.proceed(); // 忽略 SSL 错误
    }

    @Override
    public void onPageStarted(WebView view, String url, Bitmap favicon) {
        LogUtil.i(TAG, "onPageStarted , url:" + url);
    }

    /** 页面加载完成后再注入「直播台页增强脚本」（统一播放器尺寸、修正视口等） */
    private String getFileContent() {
        String baseFolder = "tv-web/";
        String fileContent = FileUtil.readExt(MyApplication.getAppContext(), baseFolder + "js/end.js");
        return fileContent + FileUtil.readExt(MyApplication.getAppContext(),
                baseFolder + "js/load_detail_tv.js");
    }

    @Override
    public void onPageFinished(WebView view, String url) {
        LogUtil.i(TAG, "onPageFinished, url:" + url);
        if (mWebView.getProgress() == 100) {
            String fileContent = getFileContent();
            if (null == fileContent) {
                return;
            }
            view.evaluateJavascript(fileContent, new ValueCallback<String>() {
                @Override
                public void onReceiveValue(String s) {
                    LogUtil.i(TAG, "onReceiveValue:" + s);
                }
            });
        }
    }

    @Override
    public void onReceivedError(WebView webView, int errorCode, String description, String failingUrl) {
        LogUtil.e(TAG, "onReceivedError: " + errorCode
                + ", description: " + description
                + ", url: " + failingUrl);
        // 低版本系统只上报主文档错误
        notifyMainFrameError(failingUrl, description);
    }

    @RequiresApi(Build.VERSION_CODES.M)
    @Override
    public void onReceivedError(WebView webView, WebResourceRequest request, WebResourceError error) {
        // 只有主文档（当前频道页面）失败才需要换源，子资源失败忽略
        if (null != request && request.isForMainFrame()) {
            CharSequence desc = (null == error) ? null : error.getDescription();
            notifyMainFrameError(request.getUrl().toString(),
                    null == desc ? "页面加载失败" : desc.toString());
        }
    }

    @RequiresApi(Build.VERSION_CODES.M)
    @Override
    public void onReceivedHttpError(WebView webView, WebResourceRequest request,
                                    WebResourceResponse errorResponse) {
        if (null != request && request.isForMainFrame() && null != errorResponse
                && errorResponse.getStatusCode() >= 400) {
            notifyMainFrameError(request.getUrl().toString(),
                    "HTTP " + errorResponse.getStatusCode());
        }
    }

    @Override
    public WebResourceResponse shouldInterceptRequest(WebView webView, WebResourceRequest webResourceRequest) {
        return intercept(webView,
                webResourceRequest.getUrl().toString(),
                webResourceRequest.getMethod(),
                webResourceRequest.getRequestHeaders());
    }

    /** Android 5.0 以下走这个重载；保留以维持最低版本兼容性 */
    @Override
    public WebResourceResponse shouldInterceptRequest(WebView webView, String url) {
        return intercept(webView, url, "GET", new HashMap<String, String>());
    }

    /**
     * 统一的请求拦截实现。
     * 注意：返回 null 等价于父类默认行为（即交给 WebView 自己去网络加载）。
     */
    private WebResourceResponse intercept(WebView webView, String url, String method,
                                          Map<String, String> orgHeader) {
        String accept = orgHeader == null ? null : orgHeader.get("Accept");
        if (orgHeader == null) {
            orgHeader = new HashMap<String, String>();
        }

        // 凤凰秀：站点把 /live/ 放在 tl/hkmolive 域上，换到真实域名并补上流响应头
        if (url.startsWith("https://tlive.fengshows.com/live/")
                || url.startsWith("https://hkmolive.fengshows.com/live/")) {
            String realUrl = "https://qctv.fengshows.cn" + url.substring(url.indexOf("/live"));
            LogUtil.i(TAG, realUrl);
            Map<String, String> headerMap = new HashMap<>();
            InputStream inputStream = HttpUtil.get(realUrl, new HashMap<String, String>());
            if (null == inputStream) {
                return null;
            }
            WebResourceResponse resp = new WebResourceResponse("video/x-flv",
                    ConstantMy.UTF8, inputStream);
            headerMap.put("access-control-allow-origin", "*");
            resp.setResponseHeaders(headerMap);
            return resp;
        }

        // 广东台：接口要求带 x-itouchtv-ca-key 等头部，浏览器发不出去，由原生代发
        if (url.startsWith("https://gdtv-api.gdtv.cn/api/tv/v2/tvChannel")) {
            LogUtil.i(TAG, url);
            if (!"GET".equalsIgnoreCase(method)) {
                return null;
            }
            Map<String, String> headerMap = new HashMap<>(orgHeader);
            headerMap.remove("x-requested-with");
            String json = HttpUtil.getJson(url, headerMap);
            LogUtil.i(TAG, json);
            WebResourceResponse resp = new WebResourceResponse("application/json;charset=UTF-8",
                    ConstantMy.UTF8, new ByteArrayInputStream(json.getBytes(Charset.defaultCharset())));
            headerMap.put("access-control-allow-origin", "*");
            resp.setResponseHeaders(headerMap);
            return resp;
        }

        // 电视/盒子上带宽宝贵：非必要的图片直接返回空响应体，避免拖慢播放页
        if (null != accept && accept.startsWith("image/") && !allowImage(url)) {
            return new WebResourceResponse(null, null, null);
        }

        int index = url.indexOf("tv-web");
        if (index < 0) {
            return null;
        }

        // 下面是「伪源」资源：一律从 APK 内置 assets 读取
        if (url.endsWith("tvImg=1")) {
            String fileName = url.substring(index, url.indexOf("?"));
            LogUtil.i(TAG, "fileName image " + fileName);
            return new WebResourceResponse("image/jpeg",
                    ConstantMy.UTF8, FileUtil.readExtIn(MyApplication.getAppContext(), fileName));
        }

        int indexWen = url.indexOf("?");
        if (indexWen > 0) {
            url = url.substring(0, indexWen);
        }

        String fileName = url.substring(index);

        if (url.endsWith("js")) {
            LogUtil.i(TAG, "fileName js " + fileName);
            if (fileName.endsWith("basex.js")) {
                return new WebResourceResponse("text/javascript",
                        ConstantMy.UTF8,
                        new ByteArrayInputStream(baseJs(fileName).getBytes(Charset.defaultCharset())));
            }
            return new WebResourceResponse("text/javascript",
                    ConstantMy.UTF8, FileUtil.readExtIn(MyApplication.getAppContext(), fileName));
        }
        if (url.endsWith("css")) {
            LogUtil.i(TAG, "fileName css " + fileName);
            return new WebResourceResponse("text/css",
                    ConstantMy.UTF8, FileUtil.readExtIn(MyApplication.getAppContext(), fileName));
        }
        if (url.endsWith(".html")) {
            LogUtil.i(TAG, "fileName html " + fileName);
            String html = FileUtil.readExt(MyApplication.getAppContext(), fileName);
            // base.js 需要做模板替换，用 basex.js 承接
            html = html.replace("base.js", "basex.js");
            return new WebResourceResponse("text/html",
                    ConstantMy.UTF8, new ByteArrayInputStream(html.getBytes(Charset.defaultCharset())));
        }
        if (url.endsWith(".woff2")) {
            LogUtil.i(TAG, "fileName woff2 " + fileName);
            return new WebResourceResponse("font/woff2",
                    ConstantMy.UTF8, FileUtil.readExtIn(MyApplication.getAppContext(), fileName));
        }

        return null;
    }

    /** 允许加载的图片：内置页面的台标，以及央视的频道图 */
    private boolean allowImage(String url) {
        return url.contains("tvImg") || url.contains("cctvpic.com") || url.contains("default");
    }

    /** basex.js 是带占位符的模板：只注入本地版本号，没有任何服务端地址 */
    private String baseJs(String fileName) {
        String baseStr = FileUtil.readExt(MyApplication.getAppContext(), fileName);
        Map<String, Object> data = new HashMap<>();
        data.put("version", AppVersionUtils.getVersionCode());
        return TplUtil.tpl(baseStr, data);
    }
}
