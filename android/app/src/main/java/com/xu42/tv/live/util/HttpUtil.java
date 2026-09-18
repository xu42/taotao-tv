package com.xu42.tv.live.util;

import java.io.IOException;
import java.io.InputStream;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * 直播页面用的轻量 HTTP 客户端。
 *
 * 用途只有一件事：网页里通过 JS 桥（_api.getJson / postJson）发起的跨域请求
 * 由原生代发，从而绕过浏览器同源限制（广东、凤凰秀等源站需要自定义请求头）。
 *
 * 应用自身不请求任何自有服务端，因此这里没有下载 / 上报之类的接口。
 */
public class HttpUtil {
    private static final String TAG = "HttpUtil";

    private static final int DEFAULT_TIMEOUT = 5;
    private static OkHttpClient defaultClient = null;

    static {
        defaultClient();
    }

    public static void defaultClient() {
        OkHttpClient.Builder builder = new OkHttpClient.Builder()
                .readTimeout(DEFAULT_TIMEOUT, TimeUnit.SECONDS)
                .writeTimeout(DEFAULT_TIMEOUT, TimeUnit.SECONDS)
                .connectTimeout(DEFAULT_TIMEOUT, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .sslSocketFactory(new SSLSocketFactoryCompat(SSLSocketFactoryCompat.trustAllCert),
                        SSLSocketFactoryCompat.trustAllCert);
        builder = ignoreSSL(builder);
        defaultClient = builder.build();
    }

    public static OkHttpClient.Builder ignoreSSL(OkHttpClient.Builder builder) {
        builder.sslSocketFactory(createSSLSocketFactory())
                .hostnameVerifier((s, sslSession) -> true);
        return builder;
    }

    private static SSLSocketFactory createSSLSocketFactory() {
        SSLSocketFactory sSLSocketFactory = null;
        try {
            SSLContext sc = SSLContext.getInstance("TLS");
            sc.init(null, new TrustManager[]{new TrustAllManager()}, new SecureRandom());
            sSLSocketFactory = sc.getSocketFactory();
        } catch (Exception e) {
            LogUtil.e(TAG, e.getMessage());
        }
        return sSLSocketFactory;
    }

    private static class TrustAllManager implements X509TrustManager {

        @Override
        public void checkClientTrusted(X509Certificate[] x509Certificates, String s) {
        }

        @Override
        public void checkServerTrusted(X509Certificate[] x509Certificates, String s) {
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[0];
        }
    }

    public static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    public static String postJson(String url, Map<String, String> headerMap, String requestBody) {
        RequestBody body = RequestBody.create(JSON, requestBody);
        Request.Builder builder = new Request.Builder().url(url);
        addHeaders(builder, headerMap);
        Request request = builder.post(body).build();
        return responseDo(request);
    }

    public static InputStream get(String url, Map<String, String> headerMap) {
        Request.Builder builder = new Request.Builder().url(url);
        addHeaders(builder, headerMap);
        Request request = builder.get().build();
        Response response = null;
        try {
            response = defaultClient.newCall(request).execute();
        } catch (IOException e) {
            LogUtil.e(TAG, e.getMessage());
        }
        if (null == response || null == response.body()) {
            return null;
        }
        return response.body().byteStream();
    }

    public static String getJson(String url, Map<String, String> headerMap) {
        Request.Builder builder = new Request.Builder().url(url);
        addHeaders(builder, headerMap);
        return responseDo(builder.get().build());
    }

    /** 网页约定：header 里的 tv-ref 表示要转成 Referer 发出去 */
    private static void addHeaders(Request.Builder builder, Map<String, String> headerMap) {
        if (null == headerMap || headerMap.isEmpty()) {
            return;
        }
        for (Map.Entry<String, String> entry : headerMap.entrySet()) {
            if (null == entry.getKey() || null == entry.getValue()) {
                continue;
            }
            if ("tv-ref".equals(entry.getKey())) {
                builder.addHeader("Referer", entry.getValue());
                continue;
            }
            builder.addHeader(entry.getKey(), entry.getValue());
        }
    }

    private static String responseDo(Request request) {
        Response response = null;
        try {
            response = defaultClient.newCall(request).execute();
        } catch (IOException e) {
            LogUtil.e(TAG, "500 error: " + e.getMessage());
            return "500";
        }
        if (!response.isSuccessful()) {
            LogUtil.e(TAG, "400 code: " + response.code());
            return "400";
        }
        try {
            return null == response.body() ? "500" : response.body().string();
        } catch (IOException e) {
            LogUtil.e(TAG, " 500 error response: " + e.getMessage());
            return "500";
        }
    }
}
