package com.xu42.tv.live.util;

/**
 * 应用级常量集中配置。
 *
 * 说明：
 * - 应用完全本地化运行：没有自有服务端，不做配置拉取、资源热更新、崩溃上报。
 * - WEB_ORIGIN 是应用内置页面的「伪源」。它不会真的走网络：
 *   WebViewClientImpl 会拦截任何路径里带 tv-web/ 的请求，改为从 APK 内置资源
 *   读取，因此断网也能打开直播页面。
 */
public class AppConfig {

    /** 内置网页伪源（务必以 / 结尾） */
    public static final String WEB_ORIGIN = "https://tv.xu42.com/tv-web/";

    /**
     * 把历史数据里的绝对地址统一收敛到本应用的伪源。
     * 例如 https://旧域名/tv-web/live.html?url=xxx -> https://tv.xu42.com/tv-web/live.html?url=xxx
     */
    public static String normalizeUrl(String url) {
        if (null == url || !url.startsWith("http")) {
            return url;
        }
        int idx = url.indexOf("tv-web/");
        if (idx <= 0) {
            return url;
        }
        return WEB_ORIGIN + url.substring(idx + "tv-web/".length());
    }
}
