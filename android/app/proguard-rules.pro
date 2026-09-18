# 桃桃TV 混淆规则
#
# release 包开启了 R8（minifyEnabled + shrinkResources），这里只保留「运行时靠名字
# 找到的东西」：JS 桥接方法、Gson 反序列化的数据类、Room 实体等。

# 保留异常、泛型签名与注解：Gson 的 TypeToken / Room 的注解都依赖它们
-keepattributes Exceptions,Signature,InnerClasses,EnclosingMethod
-keepattributes *Annotation*,AnnotationDefault

-optimizationpasses 5
-dontwarn dalvik.**

# ------------------------------------------------------------------ JS 桥接
# 网页侧通过 _api / _apiX 反射调用这些方法，名字不能被改
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# ------------------------------------------------------------------ 数据模型
# 这些类靠字段名做 json <-> 对象互转，字段名不能被混淆
-keep class com.xu42.tv.live.domain.** { *; }
-keep class com.xu42.tv.live.dao.** { *; }
-keepclassmembers class com.xu42.tv.live.dao.** { <fields>; }

# ------------------------------------------------------------------ 第三方
# okhttp / okio 的可选依赖缺失时的告警
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-dontwarn androidx.room.paging.**
