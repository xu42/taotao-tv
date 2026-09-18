package com.xu42.tv.live.service;

import android.content.Context;

import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.xu42.tv.live.MyApplication;
import com.xu42.tv.live.api.ConfigApi;
import com.xu42.tv.live.call.DownloadCallback;
import com.xu42.tv.live.dao.Favorite;
import com.xu42.tv.live.domain.ConfigDTO;
import com.xu42.tv.live.domain.Res;
import com.xu42.tv.live.domain.live.DataWrapper;
import com.xu42.tv.live.domain.live.Live;
import com.xu42.tv.live.domain.live.Vod;
import com.xu42.tv.live.util.AppConfig;
import com.xu42.tv.live.util.FileUtil;
import com.xu42.tv.live.util.HttpUtil;
import com.xu42.tv.live.util.JsonUtil;
import com.xu42.tv.live.util.LogUtil;
import com.xu42.tv.live.util.Util;

public class UpdateService {

    private static final  String TAG="UpdateService";
    public static String baseFolder;
    public static   void updateRes(Context context)  {
        try {
            updateResWithError(context);
        }catch (Exception e){
            //e.printStackTrace();
            //LogUtil.e(TAG, Objects.requireNonNull(e.getMessage()));
        }
    }
    public static   void updateResWithError(Context context) throws IOException {
        //copyAssets 到应用目录
        String tvWebZip="tv-web";
        boolean isDev = Util.isDev();
        LogUtil.i(TAG,"isDev "+isDev);
        baseFolder= context.getFilesDir().getPath();
        String toZipFilePath=baseFolder+"/"+tvWebZip+".zip";
        new Thread(()->{ checkOnlineVersion(toZipFilePath);}).start();
       // File toZipFile =new File(toZipFilePath);
    /*    String html= FileUtil.readExt(MyApplication.getAppContext(),"tv-web/index.html").trim();
        if(!toZipFile.exists()||html.isEmpty()){
            if(html.isEmpty()){
                LogUtil.i(TAG,"html isEmpty");
            }
            //不存在 copy assert 下文件
            FileUtil.copyFileFromAssert(context, tvWebZip+".zip",toZipFilePath);
            FileUtil.unzipFile(toZipFilePath,baseFolder+"/tv-web",true);
        }else{
            //校验线上版本 不一致 更新

        }*/
       // new Thread(()->{ checkOnlineVersion(toZipFilePath);}).start();
      /*  if(isDev){
            //开发 每次都copy
            LogUtil.i(TAG,"isDev");
            FileUtil.copyFileFromAssert(context,
                    tvWebZip+".zip",toZipFilePath);
            FileUtil.unzipFile(toZipFilePath,baseFolder+"/tv-web",true);
        }*/
    }
    protected static Map<String,Vod> indexVodMap = new HashMap<>();
    protected static Map<Integer,Integer> tagMaxMap = new HashMap<>();
    protected static Map<String,String> urlKeyMap= new HashMap<>();
    protected static List<Live> newLives= new ArrayList<>();
    public static  void initTvData(){
        String json= FileUtil.readExt(MyApplication.getAppContext(),"tv-web/js/cctv/tv.json");
        if(json.trim().isEmpty()){
            return;
        }
        DataWrapper<List<Live>> data = JsonUtil.fromJson(json,new TypeToken<DataWrapper<List<Live>>>(){}.getType());
        List<Live> lives = data.getData();
        indexVodMap = new HashMap<>();
        tagMaxMap=new HashMap<>();
        urlKeyMap=new HashMap<>();
        int i=0,j;
        for (Live life : lives) {
            j=0;
            for (Vod vod : life.getVods()) {
                // 频道地址里的应用的伪源统一收敛到当前配置，避免依赖固定的旧域名
                vod.setUrl(AppConfig.normalizeUrl(vod.getUrl()));
                vod.setTagIndex(i);
                vod.setDetailIndex(j);
                String key= i+"_"+j;
                vod.setKey(key);
                indexVodMap.put(key,vod);
                urlKeyMap.put(vod.getUrl(),key);
                j++;
            }
            tagMaxMap.put(i,j-1);
            i++;
        }
        newLives=lives;
    }
    
    /**
     * 获取所有直播数据，包括收藏数据
     * @param context 上下文
     * @return 包含收藏栏目的直播数据列表
     */
    public static List<Live> getByLivesWithFavorites(Context context){
        // 获取收藏数据
        List<Favorite> favorites = FavoriteService.getInstance(context).getAllFavorites();
        List<Live> lives = new ArrayList<>();
        if(!favorites.isEmpty()) {
            // 创建收藏栏目
            int tagIndex=0;
            Live favoriteLive = new Live();
            favoriteLive.setName("收藏");
            favoriteLive.setTag("favorite");
            favoriteLive.setIndex(tagIndex);
            // 将收藏的频道添加到收藏栏目
            List<Vod> favoriteVods = new ArrayList<>();
            int index = 0;
            for(Favorite favorite : favorites) {
                Vod vod = new Vod();
                // 显示为“1.频道名”格式，去掉前缀心形符号
                String baseName = favorite.getVodName();
               /* if (baseName != null) {
                    baseName = baseName.replaceFirst("^❤\\s*", "");
                }*/
                String displayName = String.format("%d.%s", index + 1, baseName);
                vod.setName(displayName);
                // 为收藏的URL添加usave=1参数
                String url = favorite.getVodUrl();
                vod.setUrl(url);
                vod.setTagIndex(tagIndex);
                vod.setDetailIndex(index);
                String key= tagIndex+"_"+index;
                vod.setKey(key);
                indexVodMap.put(key,vod);
                urlKeyMap.put(vod.getUrl(),key);
                index++;
                favoriteVods.add(vod);
            }
            favoriteLive.setVods(favoriteVods);
            lives.add(favoriteLive);
            lives.addAll(newLives);
            // 让“我的收藏”参与导航映射（index 已经是收藏明细数量）
            tagMaxMap.put(tagIndex, index - 1);
            return lives;
        }
        return newLives;
    }
    
    public static List<Live> getByLives(){
        return newLives;
    }

    /**
     * 默认频道：CCTV-1 综合。
     * 优先取「央视」分组里的央视网源，找不到再到全量数据里按名字找，
     * 最后才退回第一条数据，保证任何数据情况下都有可播的频道。
     */
    public static Vod getDefaultChannel(){
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

    private static boolean isCctv1(String name){
        if(null==name){
            return false;
        }
        String n = name.replace(" ", "").replace("-", "").toUpperCase();
        return n.startsWith("CCTV1");
    }
    public static Vod getByKey(String key){
        return indexVodMap.get(key);
    }
    /*public static String getKeyByUrl(String url){
        return  urlKeyMap.get(url);
    }*/
    public static Vod getByUrl(String url){
        String key= urlKeyMap.get(url);
        if(null==key){
            return null;
        }
        return indexVodMap.get(key);
    }


    public static  String liveNext(Integer tagIndexNow,Integer detailIndexNow,String nextType){
        if(nextType.equals("up")){
            if(detailIndexNow==0){
                return tagIndexNow+"_"+tagMaxMap.get(tagIndexNow);
            }
            return tagIndexNow+"_"+(detailIndexNow-1);
        }
        if(nextType.equals("down")){
            if(Objects.equals(detailIndexNow, tagMaxMap.get(tagIndexNow))){
                return tagIndexNow+"_0";
            }
            return tagIndexNow+"_"+(detailIndexNow+1);
        }
        if(nextType.equals("left")){
            if(tagIndexNow==0){
                return (tagMaxMap.size()-1)+"_0";
            }
            return (tagIndexNow-1)+"_0";
        }
        if(nextType.equals("right")){
            if(tagIndexNow.equals(tagMaxMap.size()-1)){
                return "0_0";
            }
            return (tagIndexNow+1)+"_0";
        }
        return "0_0";
    }
    private static void checkOnlineVersion(String toZipFilePath){
         ConfigDTO newConfig = ConfigApi.getConfig();
         if(null==newConfig){
             return;
         }
         if(null==newConfig.getRes()||!newConfig.getRes().getUpdate()){
             LogUtil.i(TAG,"checkOnlineVersion updateRes false");
             return;
         }
        Res resNew = newConfig.getRes();
         String oldJson= FileUtil.readExt(MyApplication.getAppContext(),"tv-web/update.json");
        LogUtil.i(TAG," checkOnlineVersion old "+oldJson);
        if(oldJson.trim().isEmpty()){
            return;
        }
         ConfigDTO oldConfig = JsonUtil.fromJson(oldJson,ConfigDTO.class);
         Res resOld = oldConfig.getRes();
         if(null!=resOld&&resNew.getVersion()>resOld.getVersion()){//res版本更新

            // FileUtil.del(toZipFilePath);
             //下载zip 到
             String downloadUrl= resNew.getUrl();
             //String downloadUrl ="http://qn.vonchange.com/utao/test/tv-web.zip";
             LogUtil.i(TAG,"版本更新到 "+resNew.getVersion()+" toZipFilePath"+toZipFilePath+" download:"+downloadUrl);
             HttpUtil.download(downloadUrl, baseFolder, "tv-web.zip", new DownloadCallback() {
                 @Override
                 public void downloaded() {
                     try {
                         LogUtil.i(TAG,"downloaded");
                         FileUtil.unzipFile(toZipFilePath,baseFolder+"/tv-web",resNew.getSkipFirst());
                     } catch (IOException e) {
                         LogUtil.e(TAG, "downloaded: "+e.getMessage());
                     }
                 }
             });
         }

    }

}
