package com.xu42.tv.live.util;

import com.google.gson.reflect.TypeToken;
import com.xu42.tv.live.domain.HzItem;
import com.xu42.tv.live.domain.live.DataWrapper;
import com.xu42.tv.live.domain.live.Live;

import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;

/**
 * 集中定义 Gson 反序列化用的泛型 {@link Type} 常量。
 *
 * <p><b>为什么不用 {@code new TypeToken<X>() {}} 匿名子类？</b>
 * 那种写法在运行时靠 {@code getClass().getGenericSuperclass()} 反射读取类的泛型签名，
 * 而 R8（release 包开启 minify）会：
 * <ol>
 *   <li>把多个匿名 TypeToken 子类<b>合并成一个类</b>（本工程 5 个 → 1 个）；</li>
 *   <li>合并后泛型签名无处承载，直接把 {@code Signature} 属性丢掉
 *       （实测 release 包 dex 里 {@code Ldalvik/annotation/Signature;} 数量为 0，
 *       即使已经写了 {@code -keepattributes Signature}）。</li>
 * </ol>
 * 于是 {@code getGenericSuperclass()} 只能拿到裸的 {@code Class}，
 * Gson 抛 {@code RuntimeException: Missing type parameter.}，
 * 表现为<b>debug 包正常、release 包一启动就闪退</b>。
 *
 * <p>{@link TypeToken#getParameterized} 是纯静态构造，运行时不需要任何签名信息，
 * 因此对混淆完全免疫。
 */
public final class JsonTypes {

    private JsonTypes() {
    }

    /** 直播频道数据：{@code DataWrapper<List<Live>>}（assets 内置的 tv.json） */
    public static final Type LIVE_DATA = TypeToken.getParameterized(
            DataWrapper.class,
            TypeToken.getParameterized(List.class, Live.class).getType()
    ).getType();

    /** 画质列表：{@code List<HzItem>} */
    public static final Type HZ_LIST =
            TypeToken.getParameterized(List.class, HzItem.class).getType();

    /** 请求头：{@code Map<String, String>} */
    public static final Type STRING_MAP =
            TypeToken.getParameterized(Map.class, String.class, String.class).getType();
}
