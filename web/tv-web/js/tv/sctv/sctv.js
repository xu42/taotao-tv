//let url = window.location.href;
//let index= url.indexOf("tag=");
let tag =  _tvFunc.getQueryParams()["tag"];
//服务端代理接口，见 docs/server-api.md
const _proxyBase = (typeof _tvApiBase!=="undefined" && _tvApiBase) ? _tvApiBase : "https://api.tv.xu42.com";
let playUrl=null;
let initPlayer=function (){
    _apiX.getJson(_proxyBase+"/channel/proxy?tag="+tag,   { "User-Agent": _apiX.userAgent(false), "tv-ref": _proxyBase },function(data){
        console.log(data);
        if(data&&data.trim()!==""){
            playUrl=data;
            const config = {
                "id": "mse",
                "url": data,
                "hlsOpts": {
                    xhrSetup: function(xhr, url) {
                       // xhr.setRequestHeader('tv-ref', 'https://www.sctv.com/');
                    }
                },
                "playsinline": true,
                "plugins": [],
                "isLive": true,
                "autoplay": true,
                volume: 1,
                "width": "100%",
                "height": "100%"
            }
            //config.plugins.push(HlsPlayer);
//config.plugins.push(FlvPlayer)
            player = new HlsJsPlayer(config);
        }
    });
   /* fetch("http://api.vonchange.com/utao/sctv?tag="+tag)
        .then((response) => response.text())
        .then((data) => {console.log(data);
            if(data&&data.trim()!==""){
                playUrl=data;
                const config = {
                    "id": "mse",
                    "url": data,
                    "hlsOpts": {
                        xhrSetup: function(xhr, url) {
                            xhr.setRequestHeader('Referer', 'https://www.sctv.com/');
                        }
                    },
                    "playsinline": true,
                    "plugins": [],
                    "isLive": true,
                    "autoplay": true,
                    volume: 1,
                    "width": "100%",
                    "height": "100%"
                }
                //config.plugins.push(HlsPlayer);
//config.plugins.push(FlvPlayer)
                player = new HlsJsPlayer(config);
            }
        });*/
}
let reloadLive=function (){
    _apiX.getJson(_proxyBase+"/channel/proxy?tag="+tag,   { "User-Agent": _apiX.userAgent(false), "tv-ref": _proxyBase },function(data){
        console.log(data);
            if(data&&data.trim()!==""){
                if(data!==playUrl){
                    playUrl=data;
                    player.src=data;
                }
            }
        });
}


initPlayer();
setInterval(function(){console.log("reloadLive");reloadLive();},1000*60*5);
