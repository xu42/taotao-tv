if(typeof _tvload == "undefined"){
    _tvload=false;
}
(function(){
    if(_tvload){
        return;
    }
    _tvload=true;
    /*  if(window.location.href.startsWith("https://v.qq.com/x/cover/")){
          return ;
      }*/
    function loadDetailByUrl(url){
        if(url.startsWith("https://www.yangshipin.cn/tv/home")){
            return "cctv";
        }
        if(url.startsWith("https://tv.cctv.com/live")){
            return "tv/cctv";
        }
        if(url.startsWith("https://tv.cctv.com/")){
            return "cctvideo";
        }
        if(url.startsWith("https://www.ixigua.com/")){
            return "xigua";
        }
        if(url.startsWith("https://v.qq.com/x/cover/")){
            return "qq";
        }
      /*  if(url.startsWith("https://v.youku.com/v_show/")){
            return "youku";
        }*/
        if(url.startsWith("https://v.youku.com/")){
            return "youku";
        }
        if(url.startsWith("https://www.iqiyi.com/")){
            return "iqiyi";
        }
        if(url.startsWith("https://www.douyin.com/?recommend=1")){
            return "dytj";
        }
        if(url.startsWith("https://www.miguvideo.com/p/live")){
            return "ty/mg";
        }
        if(url.startsWith("https://passport.migu.cn/login")){
            return "ty/mg/login";
        }
        if(url.startsWith("https://www.mgtv.com/b/")){
            return "mgtv";
        }
        if(url.startsWith("https://www.le.com/")){
            return "letv";
        }
        if(url.startsWith("https://www.bilibili.com/bangumi/play/")){
            return "bili";
        }
        if(url.startsWith("https://movie.douban.com/subject/")){
            return "douban";
        }
        //各大tv
        if(url.startsWith("https://live.jstv.com")){
            return "tv/jstv"
        }
        if(url.startsWith("https://live.kankanews.com/")){
            return "tv/sh"
        }
        if(url.startsWith("https://www.btime.com")){
            return "tv/bjtv"
        }
 /*       if(url.startsWith("https://www.jxntv.cn/")){
            return "tv/jxntv"
        }*/
        if(url.startsWith("https://www.jlntv.cn/")){
            return "tv/jltv"
        }
    /*    if(url.startsWith("https://www.gdtv.cn/tvChannelDetail")){
            return "tv/gdtv"
        }*/
        if(url.startsWith("https://www.lcxw.cn/")){
            _tvLoadRes.js("https://cdn.bootcdn.net/ajax/libs/hls.js/1.5.13/hls.js");
            return "tv/lctv"
        }
        if(url.startsWith("https://www.fengshows.com/")){
            return "tv/fengshows"
        }
     /*   if(url.startsWith("https://www.rzw.com.cn")){
            return "tv/rztv"
        }*/
        if(url.startsWith("https://www.nmtv.cn")){
            return "tv/nmtv"
        }
        if(url.startsWith("https://www.mgtv.com/live")){
            return "tv/hntv"
        }
        return "tv/common"
    }
    _tvLoadRes.css(_browser.getURL("css/my.css?v=x"));
    _tvLoadRes.js(_browser.getURL("js/zepto.min.js?v=x"));
    _tvLoadRes.js(_browser.getURL("js/common.js?v=x"));
    _tvLoadRes.js(_browser.getURL("js/myfocus.js?v=x"));
    _tvLoadRes.js(_browser.getURL("js/vuex.min.js?v=x"));
    let detailPath=loadDetailByUrl(window.location.href);
    console.log("detailPath:: "+detailPath);
    if(detailPath.startsWith("tv/")){
        _tvLoadRes.js(_browser.getURL(`js/tv/common.js?v=x`));
    }else{
        _tvLoadRes.js(_browser.getURL("js/detailBase.js?v=x"));
    }
    _tvLoadRes.js(_browser.getURL("js/detailBase.js?v=x"));
    _tvLoadRes.js(_browser.getURL(`js/${detailPath}/detail.js?v=x`));//"js/youku/detail.js?v=x"

})();
