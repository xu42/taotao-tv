/**
 * 影视源适配器
 *
 * 设计目标：把「各视频站点自己的分类名/分页接口」收敛成统一的分类维度，
 * 让影视聚合页可以按「电影 / 电视剧 / 综艺 / ...」把多个免费源的片库混排展示。
 *
 * 新增一个源只需要在 VG_SOURCES 里追加一项：
 *   key      源标识（也用于记录播放历史 site 字段）
 *   name     展示名
 *   cats     支持的统一分类 -> 该源接口里的分类参数
 *   list(cat, page, done)  取数，done(vods) 回调
 * 影片对象统一为：{id, name, pic, url, remark, site}
 */

/* 统一分类（顺序即首页 tab 顺序） */
const VG_CATS = ["电影", "电视剧", "综艺", "动漫", "少儿", "纪录片", "教育", "文化", "音乐"];

const VG_SOURCES = [
    {
        key: "cctv",
        name: "央视片库",
        cats: { "电视剧": "电视剧", "动漫": "动画片", "纪录片": "纪录片", "综艺": "特别节目" },
        list: function (cat, page, done) {
            const fc = this.cats[cat];
            const url = "https://api.cntv.cn/list/getVideoAlbumList?serviceId=tvcctv&n=24" +
                _tvFunc.paramStr({ p: page, fc: fc });
            _apiX.getJson(url,
                { "User-Agent": _apiX.userAgent(false), "tv-ref": "https://tv.cctv.com/" },
                function (text) {
                    let vods = [];
                    try {
                        const data = JSON.parse(text);
                        (data.data && data.data.list || []).forEach(function (item) {
                            vods.push({
                                id: item.id,
                                name: item.title,
                                pic: _tvFunc.image(item.image),
                                url: item.url,
                                remark: item.sc || "",
                                site: "cctv"
                            });
                        });
                    } catch (e) {
                        console.error("cctv parse error", e);
                    }
                    done(vods);
                },
                function () { done(null); });
        }
    },
    {
        key: "mgtv",
        name: "芒果TV",
        // 芒果频道号：1 综艺 / 2 电视剧 / 3 电影 / 10 少儿 / 51 纪录片 / 115 教育
        cats: { "电影": 3, "电视剧": 2, "综艺": 1, "少儿": 10, "纪录片": 51, "教育": 115 },
        list: function (cat, page, done) {
            const channelId = this.cats[cat];
            const url = "https://pianku.api.mgtv.com/rider/list/pcweb/v3?platform=pcweb&channelId=" +
                channelId + "&pn=" + page + "&sort=c2";
            _apiX.getJson(url,
                { "User-Agent": _apiX.userAgent(false), "tv-ref": "https://www.mgtv.com/" },
                function (text) {
                    let vods = [];
                    try {
                        const data = JSON.parse(text);
                        (data.data && data.data.hitDocs || []).forEach(function (item) {
                            vods.push({
                                id: item.clipId,
                                name: item.title,
                                pic: _tvFunc.image(item.img),
                                url: "https://www.mgtv.com/b/" + item.clipId,
                                remark: VG_SOURCES.remark(item.updateInfo),
                                site: "mgtv"
                            });
                        });
                    } catch (e) {
                        console.error("mgtv parse error", e);
                    }
                    done(vods);
                },
                function () { done(null); });
        }
    },
    {
        key: "bestv",
        name: "百视通",
        cats: { "电影": "电影", "电视剧": "电视剧", "少儿": "少儿" },
        list: function (cat, page, done) {
            // 百视通只放出免费内容：badge=1 表示限免
            const url = "https://www.bestv.com.cn/api/videos/q?page=" + page +
                "&size=36&badge=1&tags[]=" + encodeURIComponent(this.cats[cat]);
            _apiX.getJson(url,
                { "User-Agent": _apiX.userAgent(false), "tv-ref": "https://www.bestv.com.cn/" },
                function (text) {
                    let vods = [];
                    try {
                        const data = JSON.parse(text);
                        (data.Data && data.Data.Items || []).forEach(function (item) {
                            if (item.badge !== 0) {
                                return;
                            }
                            vods.push({
                                id: item.Vid,
                                name: item.Title,
                                pic: _tvFunc.image(item.Vimage),
                                url: "https://www.bestv.com.cn/web/play/" + item.Vid,
                                remark: item.Pubdate || "",
                                site: "bestv"
                            });
                        });
                    } catch (e) {
                        console.error("bestv parse error", e);
                    }
                    done(vods);
                },
                function () { done(null); });
        }
    },
    {
        key: "youku",
        name: "优酷",
        cats: {
            "电影": "电影", "电视剧": "电视剧", "综艺": "综艺", "动漫": "动漫",
            "少儿": "少儿", "纪录片": "纪录片", "教育": "教育", "文化": "文化", "音乐": "音乐"
        },
        list: function (cat, page, done) {
            const filter = encodeURIComponent('{"type":"' + this.cats[cat] + '"}');
            const session = encodeURIComponent('{"scene":"search_component_paging","id":227939}');
            const url = "https://www.youku.com/category/data?session=" + session +
                "&params=" + filter + "&pageNo=" + page;
            _apiX.getJson(url,
                { "User-Agent": _apiX.userAgent(false), "tv-ref": "https://www.youku.com/" },
                function (text) {
                    let vods = [];
                    try {
                        const data = JSON.parse(text);
                        (data.data && data.data.filterData && data.data.filterData.listData || []).forEach(function (item) {
                            let link = item.videoLink || "";
                            if (link.indexOf("//") === 0) {
                                link = "https:" + link;
                            }
                            vods.push({
                                id: VG_SOURCES.vodIdFromYouku(link),
                                name: item.title,
                                pic: _tvFunc.image(item.img + "?x-oss-process=image/resize,w_315/interlace,1/quality,Q_80"),
                                url: link,
                                remark: VG_SOURCES.remark(item.summary),
                                site: "youku"
                            });
                        });
                    } catch (e) {
                        console.error("youku parse error", e);
                    }
                    done(vods);
                },
                function () { done(null); });
        }
    }
];

/* 分页每页条数（各源接口基本一致，前端只用于判断何时继续加载） */
VG_SOURCES.PAGE_SIZE = 24;

/** 清洗「更新至X集 / 全X集」这类说明文案 */
VG_SOURCES.remark = function (raw) {
    if (!raw) {
        return "";
    }
    return (" " + raw).replace("更新至", "今").replace(/全/g, "").trim();
};

/** 优酷播放链接里 ?s= 之后是剧集 id */
VG_SOURCES.vodIdFromYouku = function (link) {
    const arr = (link || "").split("?s=");
    return arr.length > 1 ? arr[1] : null;
};

/** 返回支持指定分类的源列表 */
VG_SOURCES.byCat = function (cat) {
    return VG_SOURCES.filter(function (s) {
        return Object.prototype.hasOwnProperty.call(s.cats, cat);
    });
};
