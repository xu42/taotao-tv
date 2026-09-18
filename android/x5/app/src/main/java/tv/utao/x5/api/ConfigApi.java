package tv.utao.x5.api;

import android.os.Build;

import java.text.MessageFormat;
import java.util.HashMap;

import tv.utao.x5.call.ConfigCallback;
import tv.utao.x5.domain.ConfigDTO;
import tv.utao.x5.util.AppConfig;
import tv.utao.x5.util.AppVersionUtils;
import tv.utao.x5.util.HttpUtil;
import tv.utao.x5.util.JsonUtil;
import tv.utao.x5.util.LogUtil;
import tv.utao.x5.util.Util;

/**
 * 拉取服务端配置（版本检查 + 资源更新地址 + 内核下载地址）。
 *
 * 只请求自己部署的服务端，接口契约见 docs/server-api.md。
 * 请求参数里不再包含任何设备唯一标识。
 */
public class ConfigApi {

    private static final String TAG = "ConfigApi";

    public static ConfigDTO configDTO = null;
    private static Long lastTime = System.currentTimeMillis();

    public static void syncGetConfig(ConfigCallback configCallback) {
        new Thread(() -> {
            ConfigDTO configDTO1 = getConfig();
            configCallback.getConfig(configDTO1);
        }).start();
    }

    public static ConfigDTO getConfig() {
        if (null != configDTO && System.currentTimeMillis() - lastTime < 1000 * 60 * 60 * 24) {
            return configDTO;
        }
        lastTime = System.currentTimeMillis();
        String json;
        try {
            String num = Util.is64() ? "64" : "32";
            int api = Build.VERSION.SDK_INT;
            // 只上报与兼容性相关的匿名信息，便于服务端决定推荐的内核/资源版本
            String paramStr = MessageFormat.format("?num={0}&api={1}&ver={2}",
                    num, api, AppVersionUtils.getVersionCode());
            String reqUrl = AppConfig.CONFIG_URL + paramStr;
            LogUtil.i(TAG, "reqUrl " + reqUrl);
            json = HttpUtil.getJson(reqUrl, new HashMap<>());
        } catch (Exception e) {
            return null;
        }
        if (HttpUtil.isErrorResponse(json)) {
            LogUtil.i(TAG, "config response error");
            return null;
        }
        ConfigDTO result = JsonUtil.fromJson(json, ConfigDTO.class);
        configDTO = result;
        return result;
    }
}
