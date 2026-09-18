package com.xu42.tv.live.dao;

import android.content.Context;

import java.util.Date;

import com.xu42.tv.live.domain.live.Vod;
import com.xu42.tv.live.service.UpdateService;
import com.xu42.tv.live.util.LogUtil;

/**
 * 直播观看记录的读写。
 *
 * <p>全应用只有一个页面（直播），历史记录只保存「上次观看的频道及其播放源」，
 * 用 site="tv" 作为唯一标记。
 */
public class HistoryDaoX {
    private static String TAG = "HistoryDaoX";

    /** 上次观看的频道；没有记录（或记录已失效）时返回默认频道 CCTV-1 */
    public static Vod currentChannel(Context context) {
        HistoryDao historyDao = AppDatabase.getInstance(context).historyDao();
        History history = historyDao.queryOneBySite("tv");
        if (null == history) {
            return UpdateService.getDefaultChannel();
        }
        Vod vod = UpdateService.getByUrl(history.url);
        if (null == vod) {
            return UpdateService.getDefaultChannel();
        }
        return vod;
    }

    /** 上次播放的地址，用来还原当时用的是这个频道的哪一路源 */
    public static String currentChannelUrl(Context context) {
        HistoryDao historyDao = AppDatabase.getInstance(context).historyDao();
        History history = historyDao.queryOneBySite("tv");
        return null == history ? null : history.url;
    }

    /** 记录 / 更新「当前频道」，下次启动时据此续播 */
    public static void updateChannel(Context context, String url) {
        HistoryDao historyDao = AppDatabase.getInstance(context).historyDao();
        History history = historyDao.queryOneBySite("tv");
        Vod vod = UpdateService.getByUrl(url);
        if (null == vod) {
            return;
        }
        if (null == history) {
            History historyNew = new History();
            historyNew.url = vod.getUrl();
            historyNew.name = vod.getName();
            historyNew.site = "tv";
            historyNew.vodId = "0";
            historyNew.createTime = new Date().getTime();
            historyNew.updateTime = new Date().getTime();
            historyDao.insertAll(historyNew);
            return;
        }
        LogUtil.i(TAG, vod.getName() + vod.getUrl());
        historyDao.updateChannel(history.id, vod.getName(), vod.getUrl(), new Date().getTime());
    }
}
