package com.xu42.tv.live.service;

import android.content.Context;

import java.util.Date;
import java.util.List;

import com.xu42.tv.live.dao.AppDatabase;
import com.xu42.tv.live.dao.Favorite;
import com.xu42.tv.live.domain.live.Vod;

public class FavoriteService {
    private static FavoriteService instance;
    private AppDatabase db;

    private FavoriteService(Context context) {
        db = AppDatabase.getInstance(context);
    }

    public static synchronized FavoriteService getInstance(Context context) {
        if (instance == null) {
            instance = new FavoriteService(context);
        }
        return instance;
    }

    /**
     * 添加收藏
     * @param vod 要收藏的频道
     */
    public void addFavorite(Vod vod) {
        Favorite favorite = new Favorite();
        //favorite.setVodKey(vod.getKey());
        favorite.setVodName("❤"+vod.getName());
        favorite.setVodUrl(favUrl(vod.getUrl()));
        favorite.setCreateTime(new Date().getTime());
        db.favoriteDao().insertFavorite(favorite);
    }
    private String favUrl(String url){
        if(url.contains("?")){
            return  url +"&usave=1";
        }
        return url+"?usave=1";
    }
    private String checkFavUrl(String url){
        if(url.contains("usave=1")){
            return url;
        }
        return favUrl(url);
    }

    /**
     * 取消收藏
     * @param vodUrl 频道key
     */
    public void removeFavorite(String vodUrl) {
        vodUrl=checkFavUrl(vodUrl);
        db.favoriteDao().deleteFavoriteByVodUrl(vodUrl);
    }

    /**
     * 检查是否已收藏
     * @param vodUrl 频道URL
     * @return true if favorited, false otherwise
     */
    public boolean isFavorite(String vodUrl) {
        vodUrl=checkFavUrl(vodUrl);
        return db.favoriteDao().isFavorite(vodUrl) > 0;
    }

    /**
     * 获取所有收藏
     * @return 收藏列表
     */
    public List<Favorite> getAllFavorites() {
        return db.favoriteDao().getAllFavorites();
    }
}