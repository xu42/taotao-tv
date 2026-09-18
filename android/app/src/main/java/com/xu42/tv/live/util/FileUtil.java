package com.xu42.tv.live.util;

import android.content.Context;
import android.content.res.AssetManager;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import com.xu42.tv.live.service.UpdateService;

public class FileUtil {
    private static final String TAG="FileUtil";

    public  static void   copyFile(File orgFile,File toFile){
        FileOutputStream fos;
        InputStream is;
        try {
            is = new FileInputStream(orgFile);
            fos = new FileOutputStream(toFile);
            byte[] buffer = new byte[1024];
            int byteCount;
            while ((byteCount = is.read(buffer)) != -1) {// 循环从输入流读取
                // buffer字节
                fos.write(buffer, 0, byteCount);// 将读取的输入流写入到输出流
            }
            fos.flush();// 刷新缓冲区
            is.close();
            fos.close();
        }catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
    public  static void   copyFileFromAssert(Context context,String orgFile,String toFile){
        FileOutputStream fos;
        InputStream is;
        try {
            is = context.getAssets().open(orgFile);
            fos = new FileOutputStream(new File(toFile));
            byte[] buffer = new byte[1024];
            int byteCount;
            while ((byteCount = is.read(buffer)) != -1) {// 循环从输入流读取
                // buffer字节
                fos.write(buffer, 0, byteCount);// 将读取的输入流写入到输出流
            }
            fos.flush();// 刷新缓冲区
            is.close();
            fos.close();
        }catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
    public static String readAssert(Context context, String strAssertFileName) {
        AssetManager assetManager = context.getAssets();
        String strResponse = "";
        try {
            InputStream ims = assetManager.open(strAssertFileName);
            strResponse = getStringFromInputStream(ims);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return strResponse;
    }
    public static InputStream readAssertIn(Context context, String strAssertFileName) {
        AssetManager assetManager = context.getAssets();
        try {
           return assetManager.open(strAssertFileName);
        } catch (IOException e) {
            LogUtil.e(TAG, e.getMessage());
        }
        return null;
    }
   public static InputStream readExtIn(Context context,String extFileName) {
       String baseFolder= UpdateService.baseFolder+"/";
       String fullPath=baseFolder+extFileName;
       LogUtil.i(TAG,fullPath);
       if(!new File(fullPath).exists()){
           LogUtil.i(TAG,"assert:: "+fullPath);
           return readAssertIn(context,extFileName);
       }
       try {
           return new FileInputStream(fullPath);
       } catch (FileNotFoundException e) {
           LogUtil.e(TAG, e.getMessage());
           //throw new RuntimeException(e);
       }
       return null;
   }
    public static String readExt(Context context,String extFileName) {
        return getStringFromInputStream(readExtIn(context,extFileName));
    }
    public static void  del(String filePath) {
       File file = new File(filePath);
       if(file.exists()){
          boolean delete= file.delete();
          LogUtil.i(TAG,filePath+" del "+delete);
       }
    }

    public static String getStringFromInputStream(InputStream a_is) {
       if(null==a_is){
           return "";
       }
        BufferedReader br = null;
        StringBuilder sb = new StringBuilder();
        String line;
        try {
            br = new BufferedReader(new InputStreamReader(a_is));
            while ((line = br.readLine()) != null) {
                sb.append(line).append("\n");
            }
        } catch (IOException e) {
        } finally {
            if (br != null) {
                try {
                    br.close();
                } catch (IOException e) {
                }
            }
        }
        return sb.toString();
    }
    public static void unzipFile(String zipPath, String outputDirectory,boolean skipFirst)throws IOException {
        /**
         * 解压assets的zip压缩文件到指定目录
         * @param context上下文对象
         * @param assetName压缩文件名
         * @param outputDirectory输出目录
         * @param isReWrite是否覆盖
         * @throws IOException
         */

        LogUtil.i(TAG,"开始解压的文件： "  + zipPath + "\n" + "解压的目标路径：" + outputDirectory );
        // 创建解压目标目录
        File file = new File(outputDirectory);
        // 如果目标目录不存在，则创建
        if (!file.exists()) {
            file.mkdirs();
        }
        // 打开压缩文件
        InputStream inputStream = new FileInputStream(zipPath); ;
        ZipInputStream zipInputStream = new ZipInputStream(inputStream);
        // 读取一个进入点
        ZipEntry zipEntry = zipInputStream.getNextEntry();
        if(null==zipEntry){
            return;
        }
        String firstName=null;
        if(skipFirst){
            int index = zipEntry.getName().indexOf("/");
            if(index>0){
                firstName=zipEntry.getName().substring(0,index+1);
            }else{
                firstName=zipEntry.getName();
                zipEntry=zipInputStream.getNextEntry();
            }
        }
        // 使用1Mbuffer
        byte[] buffer = new byte[1024 * 1024];
        // 解压时字节计数
        int count = 0;
        // 如果进入点为空说明已经遍历完所有压缩包中文件和目录
        while (zipEntry != null) {
            //LogUtil.i(TAG,"解压文件 入口 1： " +zipEntry );
            String fileName = zipEntry.getName();
            if(skipFirst){
                fileName=fileName.substring(firstName.length());
            }
            if (!zipEntry.isDirectory()) {  //如果是一个文件
                // 如果是文件
                //LogUtil.i(TAG,"解压文件 原来 文件的位置： " + fileName);
                //fileName = fileName.substring(fileName.lastIndexOf("/") + 1);  //截取文件的名字 去掉原文件夹名字
                LogUtil.i(TAG,"解压文件 的名字： " + fileName);
                file = new File(outputDirectory + File.separator + fileName);  //放到新的解压的文件路径

                file.createNewFile();
                FileOutputStream fileOutputStream = new FileOutputStream(file);
                while ((count = zipInputStream.read(buffer)) > 0) {
                    fileOutputStream.write(buffer, 0, count);
                }
                fileOutputStream.close();
            }else{
                File folder= new File(outputDirectory+File.separator+fileName);
                if (!folder.exists()) {
                    folder.mkdirs();
                }
            }

            // 定位到下一个文件入口
            zipEntry = zipInputStream.getNextEntry();
           // LogUtil.i(TAG,"解压文件 入口 2： " + zipEntry );
        }
        zipInputStream.close();
        LogUtil.i(TAG,"解压完成");

    }

}
