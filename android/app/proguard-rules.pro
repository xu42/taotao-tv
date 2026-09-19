# 桃桃TV 混淆规则
#
# release 包开启了 R8（minifyEnabled + shrinkResources），这里只保留「运行时靠名字
# 找到的东西」：JS 桥接方法与 Gson 反序列化的数据类。

# 保留异常、泛型签名与注解：Gson 的 TypeToken 依赖它们
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

# ------------------------------------------------------------------ Gson
# ⚠️ 血的教训：R8 会把多个匿名 TypeToken 子类「合并成一个类」，合并后泛型签名无处承载，
# Signature 属性会被直接丢弃 —— 实测 release 包 dex 里 Ldalvik/annotation/Signature;
# 数量为 0，即便上面已经写了 -keepattributes Signature 也救不回来。
# 之后 Gson 的 getClass().getGenericSuperclass() 只能拿到裸 Class，
# 抛 "Missing type parameter."，表现为「debug 包正常、release 包一启动就闪退」。
#
# 本项目已改用 TypeToken.getParameterized()（见 util/JsonTypes），从根上不依赖签名。
# 下面两条只作防回归：万一以后又写出 `new TypeToken<X>() {}`，也不会被合并。
-keep class com.google.gson.reflect.TypeToken { *; }
-keep,allowobfuscation,allowshrinking class * extends com.google.gson.reflect.TypeToken

# ------------------------------------------------------------------ 第三方
# okhttp / okio 的可选依赖缺失时的告警
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
