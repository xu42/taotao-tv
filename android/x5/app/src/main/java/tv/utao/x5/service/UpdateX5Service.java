package tv.utao.x5.service;

import android.content.Context;
import android.os.Build;
import android.os.Environment;

import java.io.File;
import java.util.List;
import java.util.Map;

import tv.utao.x5.call.DownloadProgressListener;
import tv.utao.x5.domain.ConfigDTO;
import tv.utao.x5.util.FileUtil;
import tv.utao.x5.util.HttpUtil;
import tv.utao.x5.util.LogUtil;
import tv.utao.x5.util.Util;

public class UpdateX5Service {
    private static final  String TAG="UpdateX5Service";

    public static   String  updateX5(Context context,ConfigDTO configDTO,DownloadProgressListener downloadProgressListener)  {
        //https://tbs.imtt.qq.com/others/release/x5/tbs_core_046238_20230210164344_nolog_fs_obfs_armeabi_release.tbs
        String url = getDownloadUrl(configDTO);
        if(null==url){
            return "error";
        }
        LogUtil.i(TAG,"updateX5 url "+url);
        String targetDir = FileUtil.getTBSFileDir(context).getPath()+"/";
        String fileName = fileNameByUrl(url);
        File targetFile = new File(targetDir+fileName);
        HttpUtil.downloadByProgress(url, targetFile, downloadProgressListener);
        // 下载中是null
        return  null;

    }
    private static String fileNameByUrl(String url){
       int index=   url.lastIndexOf("/");
       return url.substring(index+1);
    }
    private static String getDownloadUrl(ConfigDTO configDTO){
        Map<String, List<String>> configMap =configDTO.getX5Url();
        if(null==configMap){
            return null;
        }
        boolean is64= Util.is64();
        if(is64){
            List<String> urls64 = configMap.get("64");
            if(null==urls64||urls64.isEmpty()){
                return null;
            }
            return urls64.get(0);
        }
        List<String>  urls =  configMap.get("32");
        if(null==urls||urls.isEmpty()){
            return null;
        }
        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            return  urls.get(0);
        }
        return  urls.size()>1?urls.get(1):urls.get(0);
    }
    private  static File filePublicPath(String fileName){
         File  publicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
         return new File(publicDir,fileName);
    }
}
