package com.xu42.tv.live;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.webkit.JavascriptInterface;
import android.webkit.PermissionRequest;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.databinding.DataBindingUtil;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import com.xu42.tv.live.api.ConfigApi;
import com.xu42.tv.live.call.DownloadCallback;
import com.xu42.tv.live.call.StringCallback;
import com.xu42.tv.live.dao.HistoryDaoX;
import com.xu42.tv.live.databinding.ActivityMainBinding;
import com.xu42.tv.live.databinding.ItemHzBinding;
import com.xu42.tv.live.databinding.ItemJdBinding;
import com.xu42.tv.live.databinding.ItemRateBinding;
import com.xu42.tv.live.databinding.ItemXjBinding;
import com.xu42.tv.live.domain.ApkInfo;
import com.xu42.tv.live.domain.ConfigDTO;
import com.xu42.tv.live.domain.DetailMenu;
import com.xu42.tv.live.domain.HzItem;
import com.xu42.tv.live.domain.JdItem;
import com.xu42.tv.live.domain.RateItem;
import com.xu42.tv.live.domain.SysInfo;
import com.xu42.tv.live.domain.XjItem;
import com.xu42.tv.live.impl.BaseBindingAdapter;
import com.xu42.tv.live.impl.BaseViewHolder;
import com.xu42.tv.live.impl.IBaseBindingPresenter;
import com.xu42.tv.live.service.UpdateService;
import com.xu42.tv.live.util.AppConfig;
import com.xu42.tv.live.util.AppVersionUtils;
import com.xu42.tv.live.util.DataCleanManager;
import com.xu42.tv.live.util.FileUtil;
import com.xu42.tv.live.util.HttpUtil;
import com.xu42.tv.live.util.JsonUtil;
import com.xu42.tv.live.util.LogUtil;
import com.xu42.tv.live.util.Util;
import com.xu42.tv.live.util.ValueUtil;
import com.xu42.tv.live.util.WebService;
import com.xu42.tv.live.utils.ToastUtils;


/**
 * Demo 基础 WebViewActivity，所有WebView能力Demo继承该 Activity 开发
 */
public class BaseWebViewActivity extends BaseActivity {
    protected String TAG = "BaseWebViewActivity";

    /** 影视聚合页（内置页面，由 WebViewClientImpl 拦截后从 APK 资源读取） */
    private static final String mHomeUrl = AppConfig.pageUrl(AppConfig.VIDEO_PAGE);

    protected  ActivityMainBinding binding;

    @Override
    protected void createInit() {
        // 2. 然后设置内容视图
        bind();
        UpdateService.updateRes(this);
        initWebView();
        //mWebView.requestFocus();
        mWebView.loadUrl(mHomeUrl);
    }


    private void bind(){
        binding = DataBindingUtil.setContentView(this, R.layout.activity_main);
        binding.setMenuTitleHandler(new MenuTitleHandler());
        ViewGroup container = binding.webviewWrapper;
        container.addView(mWebView, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        //mWebView=binding.webView;
        focusChange();
    }


    protected void initWebChromeClient() {
        mWebView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                LogUtil.i("WebChromeClient", "onProgressChanged, newProgress:" + newProgress + ", view:" + view);
            }

            @Override
            public void onShowCustomView(View view, CustomViewCallback callback) {
                LogUtil.i("WebChromeClient", "onShowCustomView");
                binding.fullscreen.addView(view);
                binding.fullscreen.setVisibility(View.VISIBLE);
            }

            @Override
            public void onPermissionRequest(PermissionRequest request) {
                LogUtil.i("WebChromeClient", "onPermissionRequest " + request.getOrigin());
                LogUtil.i("WebChromeClient", request.getOrigin() + " " + Arrays.toString(request.getResources()));
                request.deny();
            }

            @Override
            public void onHideCustomView() {
                LogUtil.i("WebChromeClient", "onHideCustomView");
                binding.fullscreen.removeAllViews();
                binding.fullscreen.setVisibility(View.GONE);
            }
        });
    }

    @Override
    protected Object getJsInterface() {
        return new JsInterface();
    }


    //菜单
    private Button defaultFocusBtn(View oldFocus, View newFocus){
        if(!(newFocus instanceof Button)){
            return null;
        }
     /*   if(null!=oldFocus){  oldFocus.setScaleX(1.0f); oldFocus.setScaleY(1.0f);}
        newFocus.setScaleX(1.1f);
        newFocus.setScaleY(1.1f);*/
        return (Button) newFocus;

    }
    private String  oldBtnTag(View oldFocus){
        if(!(oldFocus instanceof Button)){
            return null;
        }
        Object tagObj=oldFocus.getTag();
        if(null==tagObj){return null;}
        return  tagObj.toString();
    }

    private void focusChange() {
        View view = binding.tvMenu;
        view.getViewTreeObserver().addOnGlobalFocusChangeListener(new ViewTreeObserver.OnGlobalFocusChangeListener() {
            @Override
            public void onGlobalFocusChanged(View oldFocus, View newFocus) {
                Log.d(TAG, "onGlobalFocusChanged: oldFocus=" + oldFocus);
                Log.d(TAG, "onGlobalFocusChanged: newFocus=" + newFocus);
                //lastFocus=oldFocus;nowFocus=newFocus;
                Button focusBtn=defaultFocusBtn(oldFocus,newFocus);
                if(null==focusBtn){return;}
                Object tagObj=focusBtn.getTag();
                if(null==tagObj){return;}
                String tag= tagObj.toString();
                Log.d(TAG, "onGlobalFocusChanged: newFocus=" + tag);
                if(tag.startsWith("menu_")){
                    tag=tag.substring(5);
                    binding.getMenu().setTab(tag);
                }
                String oldTag=null;
                switch (tag){
                    case "hzItem":
                        focusBtn.setNextFocusUpId(R.id.hzBtn);
                        oldTag=oldBtnTag(oldFocus);
                        if(null!=oldTag){
                            if(oldTag.equals("menu_hz")){
                                RecyclerView.ViewHolder viewHolder = binding.hzsView.findViewHolderForLayoutPosition(0);
                                if(null!=viewHolder){
                                    viewHolder.itemView.requestFocus();
                                }
                            }
                        }
                        break;
                    case "rateItem":
                        focusBtn.setNextFocusUpId(R.id.rateBtn);
                        oldTag=oldBtnTag(oldFocus);
                        if(null!=oldTag){
                            if(oldTag.equals("menu_rate")){
                                RecyclerView.ViewHolder viewHolder = binding.ratesView.findViewHolderForLayoutPosition(0);
                                if(null!=viewHolder){
                                    viewHolder.itemView.requestFocus();
                                }
                            }
                        }
                        break;
                    case "jdItem":
                        LinearLayout layoutJd = (LinearLayout) focusBtn.getParent();
                        RecyclerView.LayoutParams paramsJd = (RecyclerView.LayoutParams) layoutJd.getLayoutParams();
                        int itemPositionJd = paramsJd.getViewLayoutPosition();
                        if(itemPositionJd<6){
                            focusBtn.setNextFocusUpId(R.id.jdBtn);
                        }
                        oldTag=oldBtnTag(oldFocus);
                        if(null!=oldTag){
                            if(oldTag.equals("menu_jd")){
                                RecyclerView.ViewHolder viewHolder = binding.jdsView.findViewHolderForLayoutPosition(0);
                                if(null!=viewHolder){
                                    viewHolder.itemView.requestFocus();
                                }
                            }
                        }
                        break;
                    case "xjItem":
                        LinearLayout layout = (LinearLayout) focusBtn.getParent();
                        RecyclerView.LayoutParams params = (RecyclerView.LayoutParams) layout.getLayoutParams();
                        int itemPosition = params.getViewLayoutPosition();
                        if(itemPosition<6){
                            focusBtn.setNextFocusUpId(R.id.xjBtn);
                        }
                        Log.d(TAG, "xjItem: index=" + itemPosition);
                        oldTag=oldBtnTag(oldFocus);
                        if(null!=oldTag){
                            //old上一个是选集btn 下一个是item 自动选择
                            if(oldTag.equals("menu_xj")){
                                int id =  binding.xjsView.getLayoutManager().getItemCount();
                                LogUtil.i(TAG,"count "+ id+" "+ binding.xjsView.getChildCount()+" "+binding.xjsView.getAdapter().getItemCount());
                                int viewCount= binding.xjsView.getChildCount();
                                int num=binding.getMenu().getNow().getXj().getIndex();
                                if(num>viewCount){
                                    num=viewCount-1;
                                }
                                RecyclerView.ViewHolder viewHolder = binding.xjsView.findViewHolderForLayoutPosition(num);
                                if(null!=viewHolder){
                                    viewHolder.itemView.requestFocus();
                                }
                            }
                        }
                        break;
                    default:
                        LogUtil.i(TAG,"setTab"+tag);
                        break;
                }
            }
        });
    }

    private void xjBlind(List<XjItem> xjItems){
        BaseBindingAdapter xjAdapter = new BaseBindingAdapter<XjItem, ItemXjBinding>(xjItems,R.layout.item_xj) {
            @Override
            public void doBindViewHolder(BaseViewHolder<ItemXjBinding> holder, XjItem item) {
                holder.getBinding().setVariable(BR.item, item);
                holder.getBinding().setVariable(BR.itemPresenter, ItemPresenter);
            }
        };
        xjAdapter.setItemPresenter(new XjBindPresenter());
        binding.xjsView
                .setLayoutManager(new GridLayoutManager(this, 6));
        binding.xjsView
                .setAdapter(xjAdapter);
    }
    private void jdBlind(List<JdItem> jdItems){
        BaseBindingAdapter jdAdapter = new BaseBindingAdapter<JdItem, ItemJdBinding>(jdItems,R.layout.item_jd) {
            @Override
            public void doBindViewHolder(BaseViewHolder<ItemJdBinding> holder, JdItem item) {
                holder.getBinding().setVariable(BR.item, item);
                holder.getBinding().setVariable(BR.itemPresenter, ItemPresenter);
            }
        };
        jdAdapter.setItemPresenter(new JdBindPresenter());
        binding.jdsView
                .setLayoutManager(new GridLayoutManager(this, 6));
        binding.jdsView
                .setAdapter(jdAdapter);
    }
    private void hzBind(List<HzItem> hzItems){
        BaseBindingAdapter hzAdapter = new BaseBindingAdapter<HzItem, ItemHzBinding>(hzItems,R.layout.item_hz) {
            @Override
            public void doBindViewHolder(BaseViewHolder<ItemHzBinding> holder, HzItem item) {
                holder.getBinding().setVariable(BR.item, item);
                holder.getBinding().setVariable(BR.itemPresenter, ItemPresenter);
            }
        };
        hzAdapter.setItemPresenter(new HzBindPresenter());
        binding.hzsView
                .setLayoutManager(new LinearLayoutManager(this,LinearLayoutManager.HORIZONTAL,false));
        binding.hzsView
                .setAdapter(hzAdapter);
    }

    private void rateBind(List<RateItem> rateItems){
        BaseBindingAdapter rateAdapter = new BaseBindingAdapter<RateItem, ItemRateBinding>(rateItems,R.layout.item_rate) {
            @Override
            public void doBindViewHolder(BaseViewHolder<ItemRateBinding> holder, RateItem item) {
                holder.getBinding().setVariable(BR.item, item);
                holder.getBinding().setVariable(BR.itemPresenter, ItemPresenter);
            }
        };
        rateAdapter.setItemPresenter(new RateBindPresenter());
        binding.ratesView
                .setLayoutManager(new LinearLayoutManager(this,LinearLayoutManager.HORIZONTAL,false));
        binding.ratesView
                .setAdapter(rateAdapter);

    }

    public  class XjBindPresenter implements IBaseBindingPresenter {

        public void onClick(XjItem item) {
            LogUtil.i(TAG,item.getTitle());
            //TestActivity.binding.getMenu().getNow().setXj(item);
            hideMenu();
            postMessage("click","xj-"+item.getId());

        }
    }
    public  class JdBindPresenter implements IBaseBindingPresenter {

        public void onClick(JdItem item) {
            LogUtil.i(TAG,item.getName());
            hideMenu();
            postMessage("click","jd-"+item.getId());

        }
    }

    public    class HzBindPresenter implements IBaseBindingPresenter {

        public void onClick(HzItem item) {
            LogUtil.i(TAG,item.getName());
            hideMenu();
            postMessage("click","hz-"+item.getId());

        }
    }
    public  class RateBindPresenter implements IBaseBindingPresenter {

        public void onClick(RateItem item) {
            LogUtil.i(TAG,item.getName());
            hideMenu();
            postMessage("click","rate-"+item.getId());

        }
    }

    public  class MenuTitleHandler {

        public void nextBtn() {
            hideMenu();
            postMessage("click","tv-next");
        }
        public void reloadBtn() {
            hideMenu();
            mWebView.reload();
        }
        public void btnClick(View view){
            LogUtil.i(TAG,"btnClick "+view);
            //binding.tvMenu.setFocusable(true);
             view.requestFocus();
        }
    }

    //menu mange
    protected     boolean isMenuShow(){
        int visible=  binding.tvMenu.getVisibility();
        if(visible== View.VISIBLE){
            return true;
        }
        return false;
    }
    protected void showMenu(String data){
        //binding.webView.setFocusable(false);
        //binding.tvMenu.setFocusable(true);
        LogUtil.i(TAG,"data:: "+data);
        if(null==data||!data.startsWith("{")){
            return;
        }
        DetailMenu detailMenu = JsonUtil.fromJson(data, DetailMenu.class);
        binding.setMenu(detailMenu);
        xjBlind(detailMenu.getXjs());
        hzBind(detailMenu.getHzs());
        jdBlind(detailMenu.getJds());
        rateBind(detailMenu.getRates());
        //binding.nextBtn.setBackgroundResource(R.drawable.btnsel);
        binding.tvMenu.setVisibility(View.VISIBLE);
        //binding.tvMenu.requestFocus();
        binding.xjBtn.requestFocus();
        //binding.tvMenu.setFocusable(false);
    }
    protected void hideMenu(){
        binding.tvMenu.setVisibility(View.GONE);
       // binding.webView.setFocusable(true);
        //mWebView.requestFocus();
       // binding.tvMenu.clearFocus();
        //binding.webView.setFocusable(false);
        //binding.fullscreen.setFocusable(true);
      //  binding.fullscreen.requestFocus();
    }
    public    void postMessage(String service, String data) {
        if(service.equals("click")){
            String click=Util.click(data);
            LogUtil.i(TAG,"clickCode: "+click);
            mWebView.evaluateJavascript(click,null);
        }
    }

    private void toLive(){
        Intent intent = new Intent(this, LiveActivity.class);
        startActivity(intent);
        finish();
    }
    protected void killAppProcess()
    {
        //注意：不能先杀掉主进程，否则逻辑代码无法继续执行，需先杀掉相关进程最后杀掉主进程
        ActivityManager mActivityManager = (ActivityManager)this.getSystemService(Context.ACTIVITY_SERVICE);
        List<ActivityManager.RunningAppProcessInfo> mList = mActivityManager.getRunningAppProcesses();
        for (ActivityManager.RunningAppProcessInfo runningAppProcessInfo : mList)
        {
            if (runningAppProcessInfo.pid != android.os.Process.myPid())
            {
                android.os.Process.killProcess(runningAppProcessInfo.pid);
            }
        }
        android.os.Process.killProcess(android.os.Process.myPid());
        System.exit(0);
    }
    private  static  WebService webService=null;
    private void newWebService(){
        if(null==webService){
            webService=new WebService(10240);
        }
    }
    protected boolean openOkMenu(){
        return "1".equals(ValueUtil.getString(getApplicationContext(),"openOkMenu","0"));
    }
    //js
    public class JsInterface{

        // Android 调用 Js 方法1 中的返回值
        @JavascriptInterface
        public void toast(String message){
            LogUtil.i(TAG,"message "+message);
            ToastUtils.show(MyApplication.getContext(),message, Toast.LENGTH_SHORT);
        }
        @JavascriptInterface
        public void message(String service,String data){
            LogUtil.i(TAG,"service "+service+" data "+data);
            if("activity".equals(service)){
                if(data.equals("live")){
                    toLive();
                }
                return;
            }
            if("history.save".equals(service)){
                //final AppDatabase db = AppDatabase.getInstance(this);
                HistoryDaoX.save(thisContext, data, new StringCallback() {
                    @Override
                    public void data(String data) {
                        runOnUiThread(() -> {
                            if (mWebView != null) {
                                mWebView.loadUrl(data);
                            } else {
                                Log.e(TAG, "WebView is null when trying to load URL");
                            }
                        });
                    }
                });
                return;
            }
            if("history.update".equals(service)){
                HistoryDaoX.update(thisContext,data);
                return;
            }
            if("menu".equals(service)){
                runOnUiThread(()->{
                    boolean isMenuShow=isMenuShow();
                    if(isMenuShow){
                        hideMenu();
                        return;
                    }
                    showMenu(data);
                });
                return;
            }
            if("openOkMenu".equals(service)){
                ValueUtil.putString(getApplicationContext(),"openOkMenu",data);
                if(data.equals("1")){
                    ToastUtils.show(thisContext, "开启OK键是菜单成功",Toast.LENGTH_SHORT);
                }else{
                    ToastUtils.show(thisContext, "关闭OK键是菜单成功",Toast.LENGTH_SHORT);
                }

                return;
            }
            if("closeApp".equals(service)){
                killAppProcess();
                return;
            }
            if("js".equals(service)){
                Util.evalOnUi(mWebView,data);
                return;
            }
            if("key".equals(service)){
                keyCodeAllByCode(data);
                return;
            }
            if("keyNum".equals(service)){
                keyEventAll(Integer.parseInt(data));
                return;
            }
            if("clearCache".equals(service)){
                DataCleanManager.cleanInternalCache(thisContext);
                DataCleanManager.cleanExternalCache(thisContext);
                ToastUtils.show(thisContext, "清理缓存成功 网站可能会要求重新扫码登录",Toast.LENGTH_SHORT);
                return;
            }
            if("updateApk".equals(service)){
                int versionCode=  AppVersionUtils.getVersionCode();
                ConfigDTO configDTO =  ConfigApi.getConfig();
                if(null==configDTO){
                    return;
                }
                ApkInfo apkInfo = configDTO.getApk();
                if(null==apkInfo||null==apkInfo.getVersion()||null==apkInfo.getUrl()){
                    return;
                }
                if(apkInfo.getVersion()<=versionCode){
                    return;
                }
                File targetFile = new File(thisContext.getFilesDir().getPath(),"update.apk");
                HttpUtil.download(apkInfo.getUrl(),
                        thisContext.getFilesDir().getPath(), "update.apk", new DownloadCallback() {
                            @Override
                            public void downloaded() {
                                Util.installApk(thisContext,targetFile);
                            }
                });
            }
        }
        @JavascriptInterface
        public String queryByService(String service,String extPraram){
            LogUtil.i(TAG,"queryByService "+service+" extPraram "+extPraram);
            if("queryHistory".equals(service)){
                return JsonUtil.toJson(HistoryDaoX.queryHistory(thisContext));
            }
            if("queryIp".equals(service)){
                //开启服务 有且只有一次
                newWebService();
                return Util.getLocalIPAddress(thisContext);
            }
            if("querySysInfo".equals(service)){
               ConfigDTO configDTO= ConfigApi.getConfig();
                String oldJson= FileUtil.readExt(MyApplication.getAppContext(),"tv-web/update.json");
                ConfigDTO oldConfig = null;
                if(!oldJson.trim().isEmpty()){
                     oldConfig = JsonUtil.fromJson(oldJson,ConfigDTO.class);
                }
                int versionCode=  AppVersionUtils.getVersionCode();
                ApkInfo apkInfo = (null==configDTO)?null:configDTO.getApk();
                int updateCode = (null==apkInfo||null==apkInfo.getVersion())?0:apkInfo.getVersion();
                SysInfo sysInfo = new SysInfo();
                if(updateCode>versionCode){
                    sysInfo.setHaveNew(true);
                }
                boolean is64= Util.is64();
                sysInfo.setSys64(is64);
                sysInfo.setVersionCode(Build.VERSION.SDK_INT);
                sysInfo.setX86(Util.isX86());
                sysInfo.setDeviceId(MyApplication.androidId);
                sysInfo.setOpenOkMenu(openOkMenu());
                sysInfo.setCacheSize(DataCleanManager.getCacheSize(thisContext));
                //Build.VERSION.SDK_INT
                sysInfo.setVersionName(AppVersionUtils.getVersionName());
                if(null!=oldConfig&&null!=oldConfig.getRes()){
                    sysInfo.setResVersion(""+oldConfig.getRes().getVersion());
                }

                return JsonUtil.toJson(sysInfo);
            }
            return null;
        }
        @JavascriptInterface
        public String postJson(String url,String header, String requestBody){

            Map<String, String> headerMap= JsonUtil.fromJson(header,
                    new TypeToken<Map<String, String>>() {}.getType());
            if(!url.startsWith("http")){
                return FileUtil.readExt(MyApplication.getAppContext(),"tv-web/"+url);
            }
            LogUtil.i(TAG,headerMap.toString()+"url "+url+" "+requestBody);
            return HttpUtil.postJson(url,headerMap,requestBody);
        }
        @JavascriptInterface
        public String getJson(String url,String header){
            Map<String, String> headerMap= JsonUtil.fromJson(header,
                    new TypeToken<Map<String, String>>() {}.getType());
            if(!url.startsWith("http")){
                return FileUtil.readExt(MyApplication.getAppContext(),"tv-web/"+url);
            }
            LogUtil.i(TAG,headerMap.toString()+"url "+url);
            return HttpUtil.getJson(url,headerMap);
        }
        @JavascriptInterface
        public String getHtml(String url,String header){
            Map<String, String> headerMap= JsonUtil.fromJson(header,
                    new TypeToken<Map<String, String>>() {}.getType());
            LogUtil.i(TAG,headerMap.toString()+" getHtml "+url);
            return HttpUtil.getJson(url,headerMap);
        }

    }



}
