package com.xu42.tv.live.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

@Dao
public interface HistoryDao {

    /** 取出「上次观看的频道」这条记录（site 固定为 "tv"） */
    @Query("SELECT * FROM history where  site=:site order by update_time desc limit 1")
    History queryOneBySite(String site);

    @Insert
    void insertAll(History... histories);

    @Query("update history set url=:url,name=:name,update_time=:updateTime where id=:id")
    int updateChannel(Integer id, String name, String url, Long updateTime);

}
