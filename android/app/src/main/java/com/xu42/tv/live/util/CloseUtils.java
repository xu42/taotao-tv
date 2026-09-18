package com.xu42.tv.live.util;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.channels.FileChannel;

public class CloseUtils {
 
    public static void closeIO(InputStream inputStream, OutputStream outputStream){
        try {
            inputStream.close();
            outputStream.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    public static void closeIO(InputStream inputStream){
        try {
            inputStream.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    public static void closeIO(OutputStream outputStream){
        try {
            outputStream.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    //BufferedReader
    public static void closeIO(BufferedReader bufferedReader){
        try {
            bufferedReader.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    //FileChannel
    public static void closeIO(FileChannel fileChannel){
        try {
            fileChannel.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    //BufferedWriter
    public static void closeIO(BufferedWriter bufferedWriter){
        try {
            bufferedWriter.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    public static void closeIO(InputStreamReader inputStreamReader){
        try {
            inputStreamReader.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}