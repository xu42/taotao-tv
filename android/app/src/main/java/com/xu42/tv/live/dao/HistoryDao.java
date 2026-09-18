package com.xu42.tv.live.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import java.util.List;

@Dao
public interface HistoryDao {
    @Query("SELECT * FROM history where  site=:site order by update_time desc limit 60")
    List<History> queryHistoryBySite(String site);

    @Query("SELECT * FROM history where  site=:site order by update_time desc limit 1")
    History queryOneBySite(String site);

    @Query("SELECT * FROM history  order by update_time desc limit 120")
    List<History> queryHistory();

    @Query("delete from history where id not in (SELECT id FROM history  order by update_time desc limit 100) ")
    int clearData();
    @Insert
    void insertAll(History... histories);
    @Query("SELECT * FROM history where vod_id =:vodId and site=:site")
    List<History> queryByVodId(String vodId,String site);

    @Query("update history set remark =:remark,url=:url,update_time=:updateTime where vod_id =:vodId and site=:site")
    //@Update
    int updateUrl(String  vodId,String site,String remark,String url,Long updateTime);
    @Query("update history set url=:url,name=:name,update_time=:updateTime where id=:id")
    int updateChannel(Integer id,String name,String url,Long updateTime);

}