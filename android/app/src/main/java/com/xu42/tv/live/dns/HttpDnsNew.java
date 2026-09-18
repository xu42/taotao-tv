package com.xu42.tv.live.dns;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import okhttp3.Dns;
import com.xu42.tv.live.util.HttpRequest;
import com.xu42.tv.live.util.JsonUtil;
import com.xu42.tv.live.util.LogUtil;

public class HttpDnsNew implements Dns {
    private static final String TAG="HttpDnsNew";
    @Override
    public List<InetAddress> lookup(String hostname) throws UnknownHostException {
        String body = HttpRequest.get("http://dns.alidns.com/resolve?name="+hostname).body();
        LogUtil.i(TAG,body);
        Map<String,Object> map= JsonUtil.fromJson(body, Map.class);
        List<Map<String,Object>> list= (List<Map<String, Object>>) map.get("Answer");
        if(null==list||list.isEmpty()){
            return Dns.SYSTEM.lookup(hostname);
        }
        List<InetAddress> result = new ArrayList<>();
        for (Map<String, Object> item : list) {
            Object dataObj = item.get("data");
            if(null!=dataObj){
                LogUtil.i(TAG,dataObj.toString());
                result.add(InetAddress.getByName(dataObj.toString()));
            }
        }
        if(!result.isEmpty()){
            return result;
        }
        return Dns.SYSTEM.lookup(hostname);
    }
}
