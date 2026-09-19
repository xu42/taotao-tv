package com.xu42.tv.live.domain.live;

import java.util.List;

/**
 * 一个频道（或一路播放源）。
 *
 * 频道与源共用这个类：
 *   - 频道：{@link #url} 是默认源地址，{@link #sources} 是全部源（越靠前越优先）
 *   - 源：  {@link #name} 是源站名（央视网 / 央视频…），{@link #sources} 为 null
 */
public class Vod {
   private String name;
   private String url;

   private Integer tagIndex;
   private Integer detailIndex;
   private String key;

   /** 该频道的全部播放源，第 0 个是默认源；单源频道长度为 1 */
   private List<Vod> sources;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public Integer getTagIndex() {
        return tagIndex;
    }

    public void setTagIndex(Integer tagIndex) {
        this.tagIndex = tagIndex;
    }

    public Integer getDetailIndex() {
        return detailIndex;
    }

    public void setDetailIndex(Integer detailIndex) {
        this.detailIndex = detailIndex;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    /** 全部播放源；可能为 null（未做多源合并的场景） */
    public List<Vod> getSources() {
        return sources;
    }

    public void setSources(List<Vod> sources) {
        this.sources = sources;
    }

    /** 源数量，最少按 1 计 */
    public int sourceCount() {
        return (null == sources || sources.isEmpty()) ? 1 : sources.size();
    }
}
