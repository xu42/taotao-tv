package com.xu42.tv.live.util;

import android.content.Context;
import android.content.res.AssetManager;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

/**
 * 内置网页资源的读取工具。
 *
 * 应用已完全本地化：所有网页资源（tv-web/**）只从 APK 的 assets 读取，
 * 不再有「下载资源包解压到私有目录」这套逻辑，因此这里没有任何写文件操作。
 */
public class FileUtil {
    private static final String TAG = "FileUtil";

    /** 以文本形式读取 assets 里的文件（路径示例：tv-web/js/cctv/tv.json） */
    public static String readExt(Context context, String extFileName) {
        return getStringFromInputStream(readExtIn(context, extFileName));
    }

    /** 以流的形式读取 assets 里的文件，供 WebView 拦截请求时返回响应体 */
    public static InputStream readExtIn(Context context, String extFileName) {
        if (null == context || null == extFileName) {
            return null;
        }
        AssetManager assetManager = context.getAssets();
        try {
            return assetManager.open(extFileName);
        } catch (IOException e) {
            LogUtil.e(TAG, "assets 中找不到 " + extFileName + " : " + e.getMessage());
        }
        return null;
    }

    public static String getStringFromInputStream(InputStream a_is) {
        if (null == a_is) {
            return "";
        }
        BufferedReader br = null;
        StringBuilder sb = new StringBuilder();
        String line;
        try {
            br = new BufferedReader(new InputStreamReader(a_is));
            while ((line = br.readLine()) != null) {
                sb.append(line).append("\n");
            }
        } catch (IOException e) {
            // 读取失败按空内容处理，调用方各自兜底
        } finally {
            if (br != null) {
                try {
                    br.close();
                } catch (IOException e) {
                }
            }
        }
        return sb.toString();
    }
}
