package com.xu42.tv.live.domain;

import java.util.List;

public class ConfigDTO {

    private String api;
    private List<VersionData> datas;
    private ApkInfo apk;
    private Res res;

    public String getApi() {
        return api;
    }

    public void setApi(String api) {
        this.api = api;
    }

    public Res getRes() {
        return res;
    }

    public void setRes(Res res) {
        this.res = res;
    }

    public ApkInfo getApk() {
        return apk;
    }

    public void setApk(ApkInfo apk) {
        this.apk = apk;
    }



    public List<VersionData> getDatas() {
        return datas;
    }

    public void setDatas(List<VersionData> datas) {
        this.datas = datas;
    }
}
