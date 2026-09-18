package com.xu42.tv.live.impl;

import android.content.Context;
import android.graphics.Bitmap;
import android.net.http.SslError;
import android.os.Build;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.SslErrorHandler;
import android.webkit.ValueCallback;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.annotation.RequiresApi;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.text.MessageFormat;
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
 * 基于系统内核（android.webkit）的 WebViewClient 实现。
 * 内置网页走「伪源」https://tv.xu42.com/tv-web/，由本类从 assets / 私有目录读取，不产生网络请求。
 */
public class WebViewClientImpl extends WebViewClient {
    private static String TAG = "WebViewClient";
    private Context context;
    private WebView mWebView;

    private static String lastUrl = null;
    private static String rootUrl = null;
    private static String currentUrl = null;
    private int type;

    public WebViewClientImpl(Context context, WebView mWebView, int type) {
        this.context = context;
        this.mWebView = mWebView;
        this.type = type;
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
        currentUrl = url;
        if (url.contains("tv-web")) {
            if (isRootPage(url)) {
                rootUrl = url;
            }
            lastUrl = url;
        }
    }

    /** 应用内置页面的「根页面」：影视聚合页（旧版为 index.html） */
    public static boolean isRootPage(String url) {
        if (null == url) {
            return false;
        }
        int idx = url.indexOf("?");
        String pure = idx > 0 ? url.substring(0, idx) : url;
        return pure.endsWith("video.html") || pure.endsWith("index.html");
    }

    private String getFileContent(String url) {
        String baseFolder = "tv-web/";
        if (url.contains(baseFolder)) {
            return null;
        }
        String fileContent = FileUtil.readExt(MyApplication.getAppContext(), baseFolder + "js/end.js");
        String detailFile = "js/load_detail_video.js";
        if (type == 1) {
            detailFile = "js/load_detail_tv.js";
        }
        return fileContent + FileUtil.readExt(MyApplication.getAppContext(), baseFolder + detailFile);
    }

    @Override
    public void onPageFinished(WebView view, String url) {
        LogUtil.i(TAG, "onPageFinished, url:" + url);
        if (mWebView.getProgress() == 100) {
            LogUtil.i(TAG, "onPageFinished XX, url:" + url);
            String fileContent = getFileContent(url);
            if (null == fileContent) {
                return;
            }
            LogUtil.i(TAG, "fileContent end:");
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
        //无图 Dec-Fetch-Dest
        String accept = orgHeader == null ? null : orgHeader.get("Accept");
        String orgUrl = url;
        if (orgHeader == null) {
            orgHeader = new HashMap<String, String>();
        }

        if (type == 1) {
            if (orgUrl.startsWith("https://tlive.fengshows.com/live/")
                    || orgUrl.startsWith("https://hkmolive.fengshows.com/live/")) {
                String realUrl = "https://qctv.fengshows.cn" + orgUrl.substring(orgUrl.indexOf("/live"));
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

            //广东
            if (orgUrl.startsWith("https://gdtv-api.gdtv.cn/api/tv/v2/tvChannel")) {
                LogUtil.i(TAG, orgUrl);
                LogUtil.i(TAG, orgHeader.get("x-itouchtv-ca-key"));
                // 仅在 GET 请求时转发请求头；预检(OPTIONS)直接放过
                if (!"GET".equalsIgnoreCase(method)) {
                    return null;
                }
                Map<String, String> headerMap = new HashMap<>(orgHeader);
                headerMap.remove("x-requested-with");
                LogUtil.i(TAG, JsonUtil.toJson(headerMap));
                String json = HttpUtil.getJson(orgUrl, headerMap);
                LogUtil.i(TAG, json);
                WebResourceResponse resp = new WebResourceResponse("application/json;charset=UTF-8",
                        ConstantMy.UTF8, new ByteArrayInputStream(json.getBytes(Charset.defaultCharset())));
                headerMap.put("access-control-allow-origin", "*");
                resp.setResponseHeaders(headerMap);
                return resp;
            }

            //拦截m3u8链接
            if (url.contains(".m3u8") && currentUrl != null && currentUrl.contains("u-link=1")) {
                String js = MessageFormat.format(
                        "sessionStorage.setItem(\"{0}\",\"{1}\");sessionStorage.setItem(\"{2}\",\"{3}\");",
                        "u-m3u8", url, "u-loc", currentUrl);
                Util.evalOnUi(webView, js);
            }
        }

        if (null != accept && accept.startsWith("image/") && !imageLoad(url)) {
            // 返回空响应体以阻止该图片请求
            return new WebResourceResponse(null, null, null);
        }

        int index = url.indexOf("tv-web");
        if (index < 0) {
            if ("GET".equals(method) && url.startsWith("https://mesh.if.iqiyi")) {
                if (url.startsWith("https://mesh.if.iqiyi.com/tvg/v2/lw/base_info")) {
                    Util.evalOnUi(webView, Util.sessionStorageWithTime("iqiyiXj", url));
                }
            }
            return null;
        }

        if (url.endsWith("tvImg=1")) {
            if (index > 0) {
                String fileName = url.substring(index, url.indexOf("?"));
                LogUtil.i(TAG, "fileName image " + fileName);
                return new WebResourceResponse("image/jpeg",
                        ConstantMy.UTF8, FileUtil.readExtIn(MyApplication.getAppContext(), fileName));
            }
        }

        int indexWen = url.indexOf("?");
        if (indexWen > 0) {
            url = url.substring(0, indexWen);
        }

        if (url.endsWith("js")) {
            if (index > 0) {
                String fileName = url.substring(index);
                LogUtil.i(TAG, "fileName js " + fileName);
                if (fileName.endsWith("basex.js")) {
                    return new WebResourceResponse("text/html",
                            ConstantMy.UTF8,
                            new ByteArrayInputStream(baseJs(fileName).getBytes(Charset.defaultCharset())));
                }
                return new WebResourceResponse("text/javascript",
                        ConstantMy.UTF8, FileUtil.readExtIn(MyApplication.getAppContext(), fileName));
            }
        }
        if (url.endsWith("css")) {
            if (index > 0) {
                String fileName = url.substring(index);
                LogUtil.i(TAG, "fileName css " + fileName);
                return new WebResourceResponse("text/css",
                        ConstantMy.UTF8, FileUtil.readExtIn(MyApplication.getAppContext(), fileName));
            }
        }
        if (url.endsWith(".html")) {
            if (index > 0) {
                String fileName = url.substring(index);
                LogUtil.i(TAG, "fileName html " + fileName);
                String html = FileUtil.readExt(MyApplication.getAppContext(), fileName);
                html = html.replace("base.js", "basex.js");
                return new WebResourceResponse("text/html",
                        ConstantMy.UTF8, new ByteArrayInputStream(html.getBytes(Charset.defaultCharset())));
            }
        }
        if (url.endsWith(".woff2")) {
            if (index > 0) {
                String fileName = url.substring(index);
                LogUtil.i(TAG, "fileName woff2 " + fileName);
                return new WebResourceResponse("font/woff2",
                        ConstantMy.UTF8, FileUtil.readExtIn(MyApplication.getAppContext(), fileName));
            }
        }

        return null;
    }

    private String baseJs(String fileName) {
        String baseStr = FileUtil.readExt(MyApplication.getAppContext(), fileName);
        Map<String, Object> data = new HashMap<>();
        data.put("version", AppVersionUtils.getVersionCode());
        data.put("apiBase", AppConfig.API_BASE);
        return TplUtil.tpl(baseStr, data);
    }

    private boolean imageLoad(String url) {
        if (url.contains("tvImg")) {
            return true;
        }
        if (url.contains("cctvpic.com")) {
            return true;
        }
        if (url.contains("default")) {
            return true;
        }
        if (url.contains("open.weixin.qq.com/connect/qrcode")) {
            String code = Util.loginQr(url, "微信");
            LogUtil.i(TAG, "imageLoad: " + code);
            Util.evalOnUi(mWebView, code);
            return true;
        }
        //ssl.ptlogin2.qq.com/ptqrshow
        if (url.contains("ptlogin2.qq.com/ssl/ptqrshow")) {
            String code = Util.loginQr(url, "手机端qq");
            LogUtil.i(TAG, "imageLoad: " + code);
            Util.evalOnUi(mWebView, code);
            return true;
        }
        if (url.startsWith("https://img.alicdn.com/imgextra/") && url.endsWith("xcode.png")) {
            String code = Util.loginQr(url, "youkuQr");
            LogUtil.i(TAG, "imageLoad: " + code);
            Util.evalOnUi(mWebView, code);
            return true;
        }
        return false;
    }

    public static Boolean currentUrlIsHome() {
        if (null == currentUrl) {
            return false;
        }
        return currentUrl.contains("tv-web");
    }

    public static String backUrl() {
        if (null == currentUrl) {
            return null;
        }
        if (currentUrl.contains("tv-web")) {
            if (isRootPage(currentUrl)) {
                return null;
            }
            return null == rootUrl ? AppConfig.pageUrl(AppConfig.VIDEO_PAGE) : rootUrl;
        }
        return lastUrl;
    }
}
