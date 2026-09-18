package tv.utao.x5.util;

/**
 * 应用级常量集中配置。
 *
 * 说明：
 * - WEB_ORIGIN 是应用内置页面的「伪源」。它不会真的走网络：
 *   WebViewClientImpl 会拦截任何路径里带 tv-web/ 的请求，改为从 APK 内置资源
 *   （或资源更新后落地的私有目录）读取，因此断网也能打开首页/影视/直播页面。
 * - API_BASE 是自己部署的服务端，接口契约见 docs/server-api.md。
 */
public class AppConfig {

    /** 内置网页伪源（务必以 / 结尾） */
    public static final String WEB_ORIGIN = "https://tv.xu42.com/tv-web/";

    /** 自有服务端地址 */
    public static final String API_BASE = "https://api.tv.xu42.com";

    /** 应用配置 / 版本检查接口 */
    public static final String CONFIG_URL = API_BASE + "/app/config";

    /** 崩溃日志上报接口 */
    public static final String CRASH_URL = API_BASE + "/app/crash";

    /** 四川等需要服务端代理解析的频道接口 */
    public static final String CHANNEL_PROXY_URL = API_BASE + "/channel/proxy";

    /** 影视页（首页「影视」入口落地页） */
    public static final String VIDEO_PAGE = "video.html";

    public static String pageUrl(String page) {
        return WEB_ORIGIN + page;
    }

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
