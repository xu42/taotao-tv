/**
 * 影视聚合页
 *
 * 一级导航是分类（电影/电视剧/综艺/…），选中分类后把各个免费源（央视片库、芒果TV、
 * 百视通、优酷）的同分类片库聚合成若干「横向片架」，点卡片直接进入播放。
 *
 * 遥控器：
 *   左右  在分类之间 / 某一源片架内移动
 *   上下  分类 -> 第一个片架 -> 下一个片架
 *   OK    选中
 */
(function () {

    /** 分类 -> 已加载片架的会话内缓存，避免来回切分类时重复请求造成卡顿 */
    const catCache = {};

    const _vhtml = {
        init() {
            this.initHtml();
        },
        initHtml() {
            document.body.innerHTML = `
            <div class="vg-body" id="vg-body" @vue:mounted="initData()">
                <div class="vg-top">
                    <div class="vg-title">
                        <span class="vg-brand">影视</span>
                        <span class="vg-sub">左右切分类 · 上下选影片 · OK 键播放</span>
                    </div>
                    <div class="vg-cats tv-header">
                        <div v-for="(c,ci) in cats" :id="'vgcat-'+ci" tabindex="0"
                             class="tv-btn vg-cat" :class="{'vg-cat-on': c===activeCat}"
                             @focus="onCatFocus(c,ci)" @click="switchCat(c,ci)"
                             :move-down="firstCardIdFrom(0)">{{c}}</div>
                    </div>
                </div>
                <div class="vg-main">
                    <div v-for="(g,gi) in groups" class="vg-group" :key="g.key">
                        <div class="vg-group-title">
                            <span class="vg-group-name">{{g.name}}</span>
                            <span class="vg-group-tip">{{g.loading ? '加载中…' : (g.vods.length ? g.vods.length+' 部' : '暂无内容')}}</span>
                        </div>
                        <div class="vg-row" :id="'vgrow-'+gi">
                            <div v-for="(v,ci) in g.vods" :id="'g'+gi+'-'+ci" tabindex="0"
                                 class="vg-card" @focus="onCardFocus(gi,ci)" @click="goto(v)"
                                 :move-up="moveUpFor(gi)"
                                 :move-down="firstCardIdFrom(gi+1)">
                                <div class="vg-cover">
                                    <img v-if="v.pic" :src="v.pic" :alt="v.name" v-on:error="v.pic=null"/>
                                    <div v-else class="vg-cover-none">{{v.name}}</div>
                                </div>
                                <div class="vg-card-title">{{v.name}}</div>
                                <div class="vg-card-remark" v-if="v.remark">{{v.remark}}</div>
                            </div>
                            <div v-if="!g.vods.length && !g.loading" class="vg-row-empty">该片源暂无此分类内容</div>
                        </div>
                    </div>
                </div>
                <div class="vg-bottom">片源均来自各平台官方免费内容</div>
            </div>`;

            PetiteVue.createApp({
                cats: VG_CATS,
                activeCat: VG_CATS[0],
                catIndex: 0,
                groups: [],
                initData() {
                    this.switchCat(VG_CATS[0], 0);
                },
                onCatFocus(c, ci) {
                    // 焦点落到分类上时顺便把该分类的内容准备好，切换更快
                    if (c !== this.activeCat) {
                        this.switchCat(c, ci);
                    }
                },
                switchCat(c, ci) {
                    this.activeCat = c;
                    this.catIndex = ci;
                    if (catCache[c]) {
                        // 该分类已经加载过，直接复用，切换瞬间完成
                        this.groups = catCache[c];
                        this.refocusCat();
                        return;
                    }
                    const sources = VG_SOURCES.byCat(c);
                    this.groups = sources.map(function (s) {
                        return { key: s.key, name: s.name, cat: c, page: 0, loading: false, done: false, vods: [] };
                    });
                    catCache[c] = this.groups;
                    const _this = this;
                    this.groups.forEach(function (g, gi) {
                        _this.loadGroup(gi);
                    });
                    this.refocusCat();
                },
                /** 该片架是否已经有内容（遥控器导航用来跳过空片架） */
                groupHasVods(gi) {
                    const g = this.groups[gi];
                    return !!(g && g.vods && g.vods.length > 0);
                },
                /**
                 * 从 fromGi 起找到第一个「有内容」的片架，返回其首张卡片的选择器。
                 *
                 * 某个源在该分类下没有资源时（例如百视通），它的片架里不会渲染任何
                 * #g{gi}-0 卡片；若 move-down 仍指向该元素，按下键就会毫无反应。
                 * 这里把空片架直接跳过。
                 */
                firstCardIdFrom(fromGi) {
                    for (let i = Math.max(0, fromGi); i < this.groups.length; i++) {
                        if (this.groupHasVods(i)) {
                            return '#g' + i + '-0';
                        }
                    }
                    return null;
                },
                /** 按下键的反向：找上一个有内容的片架，都没有则回到分类栏 */
                moveUpFor(gi) {
                    for (let i = gi - 1; i >= 0; i--) {
                        if (this.groupHasVods(i)) {
                            return '#g' + i + '-0';
                        }
                    }
                    return '#vgcat-' + this.catIndex;
                },
                /** 把焦点补回当前分类 tab（重新渲染后元素上的焦点样式会丢） */
                refocusCat() {
                    const _this = this;
                    this.$nextTick(function () {
                        const el = document.getElementById('vgcat-' + _this.catIndex);
                        if (!el) {
                            return;
                        }
                        document.querySelectorAll('.tv-focus').forEach(function (n) {
                            if (n !== el) { n.classList.remove('tv-focus'); }
                        });
                        el.classList.add('tv-focus');
                    });
                },
                loadGroup(gi) {
                    const g = this.groups[gi];
                    if (!g || g.loading || g.done) {
                        return;
                    }
                    const source = VG_SOURCES.filter(function (s) { return s.key === g.key; })[0];
                    if (!source) {
                        g.done = true;
                        return;
                    }
                    g.loading = true;
                    const _this = this;
                    const nextPage = g.page + 1;
                    source.list(g.cat, nextPage, function (vods) {
                        g.loading = false;
                        if (!vods || !vods.length) {
                            g.done = true;
                            return;
                        }
                        g.page = nextPage;
                        vods.forEach(function (v) { g.vods.push(v); });
                        if (vods.length < VG_SOURCES.PAGE_SIZE) {
                            g.done = true;
                        }
                    });
                },
                onCardFocus(gi, ci) {
                    const g = this.groups[gi];
                    if (g && ci + 6 >= g.vods.length) {
                        this.loadGroup(gi);
                    }
                },
                goto(item) {
                    if (!item || !item.url) {
                        return;
                    }
                    _layer.wait("正在打开《" + item.name + "》请稍候…");
                    // 交给安卓端记录历史并进入播放
                    _apiX.msg("history.save", {
                        vodId: item.id,
                        name: item.name,
                        pic: item.pic,
                        url: _tvFunc.url(item.url),
                        remark: item.remark,
                        site: item.site
                    });
                }
            }).mount('#vg-body');
        }
    };

    window._tvHtmlInit = function () {
        _vhtml.init();
        TvFocus.init(null);
    };

    $$(function () {
        _tvHtmlInit();
    });
})();
