package com.xu42.tv.live.service;

import android.content.Context;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.xu42.tv.live.MyApplication;
import com.xu42.tv.live.domain.live.DataWrapper;
import com.xu42.tv.live.domain.live.Live;
import com.xu42.tv.live.domain.live.Vod;
import com.xu42.tv.live.util.AppConfig;
import com.xu42.tv.live.util.FileUtil;
import com.xu42.tv.live.util.JsonTypes;
import com.xu42.tv.live.util.JsonUtil;
import com.xu42.tv.live.util.LogUtil;

/**
 * 频道数据服务。
 *
 * 相比上游数据，这里多做了一件事：**把「同一个台的不同源」合并成一个频道**。
 * 上游 tv.json 是按「站点」分组的（「央视源2」其实是央视在央视频上的镜像），
 * 所以会看到 CCTV-1 出现两次。合并之后：
 *
 *   - 每个频道带一个源列表 {@link Vod#getSources()}，越靠前越优先（央视网排第一）
 *   - {@link Vod#getUrl()} 等于列表里的第一个源，也就是默认源
 *   - 任一源地址都能通过 {@link #getByUrl(String)} 反查到它属于哪个频道
 *
 * 界面上因此只需要展示「频道」，换源交给播放时的左右键与自动降级逻辑。
 *
 * 频道数据只有一个来源：APK 内置的 assets/tv-web/js/cctv/tv.json（由 tv.yml 生成），
 * 不再有任何网络拉取或资源热更新。
 */
public class UpdateService {

    private static final String TAG = "UpdateService";

    // ------------------------------------------------------------------ 源识别

    /**
     * 播放源展示名规则。数组顺序 = 默认源优先级：越靠前越优先，
     * 因此央视网（tv.cctv.com）永远排在央视频 / 芒果TV 之前。
     */
    private static final String[][] SOURCE_RULES = {
            {"tv.cctv.com", "央视网"},
            {"yangshipin.cn", "央视频"},
            {"mgtv.com", "芒果TV"},
            {"youku.com", "优酷"},
            {"iqiyi.com", "爱奇艺"},
            {"le.com", "乐视"},
            {"ixigua.com", "西瓜视频"},
            {"bilibili.com", "哔哩哔哩"},
            {"cctv.com", "央视网"},
    };

    /**
     * 「同一个台」的判定：去掉空格 / 连字符等符号后完全相同才算同一个台。
     * 例：CCTV-13 新闻 与 CCTV13 新闻 → cctv13新闻
     */
    public static String channelKey(String name) {
        if (null == name) {
            return "";
        }
        StringBuilder sb = new StringBuilder(name.length());
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (Character.isLetterOrDigit(c)) {
                sb.append(Character.toLowerCase(c));
            }
        }
        return sb.toString();
    }

    /**
     * 允许「跨分组」补充源的特征。
     *
     * 上游把央视与各地卫视分散在多个分组里（卫视 / 河南 / 浙江 …），这些台全局唯一，
     * 合并不会出错；而「新闻综合」这类名字各省都有，一旦跨组合并就会把不相干的台并到
     * 一起，所以只放开央视与卫视。
     */
    private static final String[] SAFE_CROSS_GROUP_KEYS = {"cctv", "卫视"};

    private static boolean safeCrossGroup(String key) {
        if (null == key || key.isEmpty()) {
            return false;
        }
        for (String part : SAFE_CROSS_GROUP_KEYS) {
            if (key.contains(part)) {
                return true;
            }
        }
        return false;
    }

    private static String hostOf(String url) {
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

    /** 源站展示名；不认识的域名去掉 www. 后直接展示 */
    public static String sourceLabel(String url) {
        String host = hostOf(url);
        if (host.isEmpty()) {
            return "默认源";
        }
        for (String[] rule : SOURCE_RULES) {
            if (host.contains(rule[0])) {
                return rule[1];
            }
        }
        return host.startsWith("www.") ? host.substring(4) : host;
    }

    /** 源优先级：数字越小越优先（央视网 = 0） */
    private static int sourceRank(String url) {
        String host = hostOf(url);
        for (int i = 0; i < SOURCE_RULES.length; i++) {
            if (host.contains(SOURCE_RULES[i][0])) {
                return i;
            }
        }
        return SOURCE_RULES.length;
    }

    /** 收藏会在地址后追加 usave=1，匹配频道源时先把它去掉 */
    public static String cleanUrl(String url) {
        if (null == url || url.indexOf("usave=1") < 0) {
            return url;
        }
        return url.replaceAll("[?&]usave=1", "");
    }

    private static String favUrl(String url) {
        if (null == url) {
            return null;
        }
        return url.contains("?") ? url + "&usave=1" : url + "?usave=1";
    }

    // ------------------------------------------------------------------ 数据

    protected static Map<String, Vod> indexVodMap = new HashMap<>();
    protected static Map<String, String> urlKeyMap = new HashMap<>();
    protected static List<Live> newLives = new ArrayList<>();

    /** 「央视源2」这类镜像分组的识别规则 */
    private static final Pattern COMPANION_GROUP = Pattern.compile("^(.+?)源[0-9０-９]*$");

    public static void initTvData() {
        String json = FileUtil.readExt(MyApplication.getAppContext(), "tv-web/js/cctv/tv.json");
        if (json.trim().isEmpty()) {
            return;
        }
        DataWrapper<List<Live>> data = JsonUtil.fromJson(json, JsonTypes.LIVE_DATA);
        List<Live> lives = data.getData();
        if (null == lives) {
            return;
        }

        // 1) 频道地址统一收敛到当前伪源
        for (Live live : lives) {
            if (null == live.getVods()) {
                live.setVods(new ArrayList<Vod>());
                continue;
            }
            for (Vod vod : live.getVods()) {
                vod.setUrl(AppConfig.normalizeUrl(vod.getUrl()));
            }
        }

        // 2) 合并镜像分组：央视源2 -> 央视
        lives = mergeCompanionGroups(lives);

        // 3) 全局同名索引，用于把同一个台在别处的源补进来
        Map<String, List<Vod>> globalByName = new HashMap<>();
        for (Live live : lives) {
            for (Vod vod : live.getVods()) {
                String key = channelKey(vod.getName());
                if (key.isEmpty()) {
                    continue;
                }
                List<Vod> bucket = globalByName.get(key);
                if (null == bucket) {
                    bucket = new ArrayList<>();
                    globalByName.put(key, bucket);
                }
                bucket.add(vod);
            }
        }

        // 4) 逐分组构建频道（同名的多个源合成一个频道）
        indexVodMap = new HashMap<>();
        urlKeyMap = new HashMap<>();
        List<Live> merged = new ArrayList<>();
        for (Live live : lives) {
            Map<String, Vod> ordered = new LinkedHashMap<>();
            for (Vod vod : live.getVods()) {
                String key = channelKey(vod.getName());
                if (key.isEmpty()) {
                    continue;
                }
                Vod channel = ordered.get(key);
                if (null == channel) {
                    channel = new Vod();
                    channel.setName(vod.getName());
                    channel.setSources(new ArrayList<Vod>());
                    ordered.put(key, channel);
                }
                addSource(channel, vod.getUrl());
                if (safeCrossGroup(key)) {
                    List<Vod> others = globalByName.get(key);
                    if (null != others) {
                        for (Vod other : others) {
                            addSource(channel, other.getUrl());
                        }
                    }
                }
            }
            if (ordered.isEmpty()) {
                continue;
            }
            List<Vod> channels = new ArrayList<>(ordered.values());
            for (Vod channel : channels) {
                sortSources(channel);
            }
            Live group = new Live();
            group.setName(live.getName());
            group.setTag(live.getTag());
            group.setIndex(live.getIndex());
            group.setVods(channels);
            merged.add(group);
        }
        newLives = merged;

        // 5) 建索引：任一源地址都能反查到频道
        int i = 0;
        for (Live live : newLives) {
            int j = 0;
            for (Vod channel : live.getVods()) {
                channel.setTagIndex(i);
                channel.setDetailIndex(j);
                String key = i + "_" + j;
                channel.setKey(key);
                indexVodMap.put(key, channel);
                List<Vod> sources = channel.getSources();
                if (null == sources || sources.isEmpty()) {
                    urlKeyMap.put(channel.getUrl(), key);
                } else {
                    for (Vod source : sources) {
                        String url = source.getUrl();
                        urlKeyMap.put(url, key);
                        urlKeyMap.put(favUrl(url), key);
                    }
                }
                j++;
            }
            i++;
        }
        LogUtil.i(TAG, "initTvData groups=" + newLives.size() + " channels=" + indexVodMap.size());
    }

    /** 把「央视源2」这类镜像分组并进主分组，并保持主分组原来的位置 */
    private static List<Live> mergeCompanionGroups(List<Live> lives) {
        Map<String, Live> byName = new LinkedHashMap<>();
        List<Live> result = new ArrayList<>();
        for (Live live : lives) {
            String name = null == live.getName() ? "" : live.getName().trim();
            Matcher matcher = COMPANION_GROUP.matcher(name);
            String primary = matcher.matches() ? matcher.group(1) : null;
            Live target = (null != primary) ? byName.get(primary) : null;
            if (null != target) {
                target.getVods().addAll(live.getVods());
                LogUtil.i(TAG, "合并镜像分组 " + name + " -> " + primary);
                continue;
            }
            live.setName(name);
            byName.put(name, live);
            result.add(live);
        }
        return result;
    }

    private static void addSource(Vod channel, String url) {
        if (null == url || url.trim().isEmpty()) {
            return;
        }
        List<Vod> sources = channel.getSources();
        for (Vod source : sources) {
            if (url.equals(source.getUrl())) {
                return;
            }
        }
        Vod source = new Vod();
        source.setUrl(url);
        source.setName(sourceLabel(url));
        sources.add(source);
    }

    /** 源排序：央视网最优先，其余按规则表顺序；默认源写回 url 字段 */
    private static void sortSources(Vod channel) {
        List<Vod> sources = channel.getSources();
        final Map<String, Integer> rankMap = new HashMap<>();
        for (Vod source : sources) {
            rankMap.put(source.getUrl(), sourceRank(source.getUrl()));
        }
        Collections.sort(sources, new Comparator<Vod>() {
            @Override
            public int compare(Vod a, Vod b) {
                return rankMap.get(a.getUrl()) - rankMap.get(b.getUrl());
            }
        });
        if (!sources.isEmpty()) {
            channel.setUrl(sources.get(0).getUrl());
        }
    }

    /** 某个源地址在频道源列表里的下标；匹配不到返回 0 */
    public static int sourceIndexOf(Vod channel, String url) {
        if (null == channel || null == channel.getSources() || null == url) {
            return 0;
        }
        String want = cleanUrl(url);
        List<Vod> sources = channel.getSources();
        for (int i = 0; i < sources.size(); i++) {
            if (want.equals(cleanUrl(sources.get(i).getUrl()))) {
                return i;
            }
        }
        return 0;
    }

    // ------------------------------------------------------------------ 对外

    /** 全部直播分类（央视 / 卫视 / 各省…），顺序即菜单里的顺序 */
    public static List<Live> getByLives() {
        return newLives;
    }

    /**
     * 默认频道：CCTV-1 综合。
     * 优先取「央视」分组里的央视网源，找不到再到全量数据里按名字找，
     * 最后才退回第一条数据，保证任何数据情况下都有可播的频道。
     */
    public static Vod getDefaultChannel() {
        Vod fallback = null;
        for (Live live : newLives) {
            for (Vod vod : live.getVods()) {
                if (isCctv1(vod.getName())) {
                    if ("cctv".equals(live.getTag())) {
                        return vod;
                    }
                    if (null == fallback) {
                        fallback = vod;
                    }
                }
            }
        }
        if (null != fallback) {
            return fallback;
        }
        return getByKey("0_0");
    }

    private static boolean isCctv1(String name) {
        if (null == name) {
            return false;
        }
        String n = name.replace(" ", "").replace("-", "").toUpperCase();
        return n.startsWith("CCTV1");
    }

    public static Vod getByKey(String key) {
        return indexVodMap.get(key);
    }

    /** 按任意源地址反查频道（收藏时带 usave=1 的地址同样能查到） */
    public static Vod getByUrl(String url) {
        if (null == url) {
            return null;
        }
        String key = urlKeyMap.get(url);
        if (null == key) {
            key = urlKeyMap.get(cleanUrl(url));
        }
        if (null == key) {
            return null;
        }
        return indexVodMap.get(key);
    }
}
