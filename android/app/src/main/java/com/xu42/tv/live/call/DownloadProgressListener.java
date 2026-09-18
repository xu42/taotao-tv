package com.xu42.tv.live.call;

import java.io.File;

public interface DownloadProgressListener {
	// 下载中使用这个方法
    void onDownloadProgress(long sumReaded, long content, boolean done);
	// 下载结果，如下载中断等
    void onDownloadResult(File target, boolean done);
	// 下载失败使用这个方法，如无法联网等
    void onFailResponse();
}
