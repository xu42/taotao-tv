# 桃桃TV Android项目

## 项目简介
这是一个 Android TV 应用项目，使用 Java 开发，采用 DataBinding 架构。**只有「直播」一个模块**，应用启动即进入直播页。

## 技术栈
- **语言**: Java
- **架构**: DataBinding
- **WebView**: 系统内核（`android.webkit`），不打包任何第三方浏览器内核
- **平台**: Android TV
- **运行模式**: **完全本地化**，不依赖任何自建服务端（无配置拉取 / 无热更新 / 无崩溃上报 / 无自升级）

## 项目结构

```
app/src/main/
├── java/com/xu42/tv/live/
│   ├── LiveActivity.java           # 直播页面（唯一 Activity，也是启动入口）
│   ├── BaseActivity.java           # Activity基类
│   ├── MyApplication.java          # Application
│   ├── dao/                        # 数据库操作（收藏 / 历史）
│   ├── domain/                     # 数据模型
│   ├── impl/                       # WebViewClient 等实现
│   ├── service/                    # 服务层（频道数据 / 崩溃日志）
│   ├── util/                       # 工具类（AppConfig 为地址唯一出处）
│   └── utils/                      # 工具类
├── res/
│   ├── layout/                     # 布局文件
│   │   ├── activity_live.xml       # 直播页面布局（含二级分类菜单容器）
│   │   ├── item_live_category.xml  # 直播分类行（左侧列表）
│   │   ├── item_live_channel.xml   # 直播频道行（右侧列表）
│   │   └── dialog_exit.xml         # 设置面板布局（居中卡片，返回键/右半屏呼出）
│   ├── values/                     # 资源值
│   └── drawable/                   # 图片资源
└── assets/                         # 静态资源（由 scripts/build-web.js 生成，不入库）
    └── tv-web/                     # Web页面资源（直播页面）
```

## 核心页面说明

### LiveActivity（直播页面 · 唯一页面）
- **启动即播**：读取观看历史，有记录就续播上次的频道与源；没有记录就默认播 CCTV-1
- 电视直播功能，**二级分类菜单**：左侧是分类（央视 / 卫视 / 各省地方台…），右侧是该分类下的频道
- 菜单只占**左半边**，右半边留白（直播画面照常可见）
- 每个频道可以有 **1 个或多个源**，列表里不再暴露源（只显示「N 源」角标），默认播放央视网
- **自动容灾**：默认源播放失败（主帧加载报错 / HTTP ≥400 / 超时 / 页面里探测不到播放器）时，自动切到该频道的下一个源，并 toast 提示「播放失败，自动切到…」
- **手动换源**：播放中按左右键即可在同一频道的多个源之间循环切换
- 上/下键在当前分类内快速切台；按 MENU / SETTINGS / OK 呼出二级分类菜单
- 切台期间显示「加载中」跳动圆点遮罩，避免露出未渲染完的网页
- **设置面板**：按返回键弹出（居中，Netflix 电视端风格），内含画质 / 收藏 / 退出 / 关闭；
  **1.2 秒内连按两次返回键 = 直接退出应用**
- **平板触屏**：点左半屏拉出切台菜单、点右半屏打开设置面板、点菜单右侧留白收起菜单

## 开发规范

### 1. DataBinding使用规范
- 所有Activity必须继承BaseActivity
- 使用DataBindingUtil.setContentView绑定布局
- 布局文件必须使用`<layout>`根标签
- 在`<data>`标签中定义变量和处理器

示例：
```java
protected ActivityMainBinding binding;

@Override
protected void createInit() {
    binding = DataBindingUtil.setContentView(this, R.layout.activity_live);
    binding.setMenuTitleHandler(new MenuTitleHandler());
}
```

### 2. 布局文件规范
```xml
<?xml version="1.0" encoding="utf-8"?>
<layout xmlns:android="http://schemas.android.com/apk/res/android">
    <data>
        <variable
            name="handler"
            type="com.xu42.tv.live.Handler" />
    </data>
    
    <FrameLayout
        android:layout_width="match_parent"
        android:layout_height="match_parent">
        <!-- 内容 -->
    </FrameLayout>
</layout>
```

### 3. 按键处理规范
- 重写`dispatchKeyEvent`方法处理按键事件
- 区分按键按下（ACTION_DOWN）和抬起（ACTION_UP）
- 返回键需要特殊处理，实现双击退出或弹出对话框

示例：
```java
@Override
public boolean dispatchKeyEvent(KeyEvent event) {
    if (event.getAction() == KeyEvent.ACTION_UP) {
        return super.dispatchKeyEvent(event);
    }
    int keyCode = event.getKeyCode();
    if (keyCode == KeyEvent.KEYCODE_BACK) {
        showExitDialog();
        return true;
    }
    return super.dispatchKeyEvent(event);
}
```

### 4. 页面跳转规范
本应用只有一个 Activity（`LiveActivity`），页面内的所有页面切换都在 WebView 内部完成，不再有 Activity 之间的跳转。

### 5. SharedPreferences使用规范
使用`ValueUtil`工具类进行数据存储：
```java
// 保存数据
ValueUtil.putString(context, "key", "value");

// 读取数据
String value = ValueUtil.getString(context, "key", "defaultValue");
```

### 6. 日志规范
使用`LogUtil`进行日志输出：
```java
LogUtil.i(TAG, "信息日志");
LogUtil.e(TAG, "错误日志");
```

### 7. Toast提示规范
使用`ToastUtils`显示提示：
```java
ToastUtils.show(context, "提示信息", Toast.LENGTH_SHORT);
```

## 功能特性

### 1. 设置面板（`dialog_exit.xml`）
- 按返回键弹出，**垂直水平居中**的 480dp 近黑圆角卡片（风格参考 Netflix 电视端）
- 顶部显示当前频道 + 分类 + 正在用的源；有画质数据时多一行画质按钮
- 按钮：收藏当前频道 / 退出应用 / 关闭，焦点按钮**反白**（白底黑字，见 `@style/DialogActionButton`）
- **1.2 秒内连按两次返回键 = 直接退出应用**；超过 1.2 秒再按只收起面板
- 长按返回键产生的重复事件会被过滤（`getRepeatCount() != 0`），不会误退出

### 2. 直播功能与触屏
- 启动即播：优先续播上次频道，无记录则播 CCTV-1
- 二级分类菜单：左分类 / 右频道，遥控器上下切分类、左右进频道；**只占左半边，右侧留白**
- 一个频道多个源，默认央视网，失败自动切换，也可手动左右键换源
- 支持遥控器上下键在当前分类内快速切台
- **触屏**：点左半屏拉出菜单、点右半屏打开设置面板、点菜单右侧留白收起菜单
- 记录观看历史（含上次使用的源）与收藏

## 编译和运行

### 环境要求
- Android Studio Arctic Fox或更高版本
- JDK 17
- Android SDK Platform 34 / Build-Tools 34
- Gradle 8.x（随 wrapper 提供）

### 编译步骤
1. 克隆项目到本地
2. 生成内置网页资源：`node scripts/build-web.js`（assets 不入库，必须先生成）
3. 在 `android/local.properties` 写入 `sdk.dir=<你的 Android SDK 路径>`
4. 使用Android Studio打开 `android/` 目录
5. 等待Gradle同步完成
6. 连接Android TV设备或启动模拟器
7. 点击Run按钮运行

### 打包APK
```bash
cd android

# Debug版本
./gradlew assembleDebug

# Release版本（无签名密钥时自动回退 debug 签名，产物同样可安装）
./gradlew assembleRelease
```

产物路径：`android/app/build/outputs/apk/release/`
- `taotao-tv-<versionName>.apk`（固定单个通用包，不做 ABI 拆分）

本工程是纯 Java + 内置网页，**没有任何 `.so`**，拆分 ABI 不会带来任何体积收益，所以固定只出一个通用包。

### 签名（正式发版必做）
没有密钥时 `assembleRelease` 会回退到 debug 签名，包能装但**每次构建签名都不同**，用户无法覆盖升级。
生成正式密钥库（一次生成、终身使用）：

```bash
./scripts/gen-keystore.sh          # 交互式，生成 android/keystore/release.jks
                                   # 并写好 local.properties，最后打印 GitHub Secrets 需要的 base64
```

完整说明（含 GitHub Secrets 配置、备份要求、常见问题）见 **[docs/签名与发布.md](../docs/签名与发布.md)**。

### 版本号（按编译时间自动生成）
不再写死，默认取编译那一刻：
- `versionName` = `yyyyMMdd.HHmm`（精确到分钟，APK 文件名也会带上）
- `versionCode` = `yyMMddHH`（单调递增，不会溢出 int）

需要复现某次构建或手工指定时可以覆盖：
```bash
./gradlew assembleRelease -PbuildTime=2026-09-18T16:30
./gradlew assembleRelease -PversionName=1.0.0 -PversionCode=100000
```

### 体积优化
Release 构建已开启，无需额外参数：
- `minifyEnabled true` + `shrinkResources true`：R8 混淆 + 资源裁剪（dex 未压缩从 5.3MB 降到 670KB）
- `resConfigs "zh","en"`：只保留中英文资源
- `packaging.resources.excludes`：剔除 `META-INF/*`、`kotlin/**`、`DebugProbesKt.bin` 等构建元数据
- `dependenciesInfo includeInApk false`：不写入依赖信息

当前 Release 通用包约 **782 KB**（优化前 3.6 MB）。

### ⚠️ 开了 R8 就必须实跑 release 包

**debug 包通过 ≠ release 包通过。** R8 会做类合并、属性剥离、名字改写，能造出只在 release 出现的崩溃。

最容易踩的一个：**永远不要写 `new TypeToken<X>() {}` 匿名子类**去做 Gson 反序列化。
R8 会把多个匿名 TypeToken 子类合并成一个，合并后 `Signature` 属性无处承载被丢弃
（实测 release dex 里 `Ldalvik/annotation/Signature;` 数量为 0，**写了 `-keepattributes Signature` 也无效**），
Gson 随即抛 `Missing type parameter.`，表现为「Android Studio run 正常、打包后一启动就闪退」。

本工程已统一改用 `util/JsonTypes`（内部是 `TypeToken.getParameterized()`，不依赖签名）。
新增泛型反序列化请往那里加常量，详见 `app/proguard-rules.pro` 的 Gson 段落注释。

改动混淆规则或依赖后，建议真机过一遍：

```bash
./build_release.sh                       # 自动编译 + 安装 + 启动
# 或手动：
adb install -r app/build/outputs/apk/release/*.apk
adb shell am start -n com.xu42.tv.live/.LiveActivity
adb logcat -d | grep -A 20 "FATAL EXCEPTION"
```

## 注意事项

1. **WebView 内核**: 直接使用设备自带的系统 WebView，不再下载任何第三方内核；因此不产生额外启动耗时与安装包体积
2. **横屏强制**: Activity 设置为横屏模式（`SCREEN_ORIENTATION_LANDSCAPE`）
3. **SingleTask模式**: Activity 使用 `singleTask` 启动模式，避免重复创建
4. **WebView内存**: 注意WebView的内存管理，及时释放资源
5. **焦点处理**: TV应用需要特别注意焦点处理，确保遥控器可以正常导航
6. **assets 不入库**: `android/app/src/main/assets/tv-web` 由 `scripts/build-web.js` 生成，仓库里不提交
7. **零远端依赖**: 除播放地址本身外，应用不发起任何自建服务端请求。新增代码**不得**引入 `api.tv.xu42.com` 一类的远端调用，数据一律内置在 `assets/` 中。

## 更新日志

### v1.2.0 (2026-09-18)
- ✅ **移除影视（点播）模块**：删除 `MainActivity` / `BaseWebViewActivity` / 影视相关 domain、layout、网页与适配源，应用首页即直播页
- ✅ **启动即播**：打开应用直接续播上次频道（无记录则 CCTV-1），删除 `StartActivity` / `HomeActivity`
- ✅ **彻底移除远端依赖**：删除 `ConfigApi`、DNS、崩溃上报、资源热更新、APK 自升级、频道代理等全部服务端相关代码与权限
- ✅ 新增本地 `hls.min.js`，内嵌网页不再依赖 CDN
- ✅ 恢复**单一通用 APK** 产出，移除 ABI 拆包逻辑
- ✅ 包体进一步下降，Release 包约 910 KB

### v1.1.0 (2026-09-18)
- ✅ 直播切台改为**二级分类菜单**（左分类 / 右频道），列表不再展示源
- ✅ 频道**多源**模型：默认央视网，播放失败自动切源，播放中左右键手动换源
- ✅ 移除百视通（bestv）源及其网页分支
- ✅ 版本号改为按编译时间自动生成（`versionName=yyyyMMdd.HHmm` / `versionCode=yyMMddHH`）
- ✅ 体积优化：R8 + shrinkResources + 资源裁剪，Release 包 3.6MB → 963KB
- ✅ 新增可选 ABI 拆包开关 `-PabiSplit`（分包 + 通用包）<span title="v1.2.0 已移除">（v1.2.0 已回退为单一通用包）</span>
- ✅ 清理无用目录与文件（`util/`、`img/`、`web/tv-web` 下废弃页面与脚本）

### v1.0.0 (2025-10-03)
- ✅ 新增退出对话框功能
- ✅ 新增启动首页切换功能
- ✅ 优化返回键处理逻辑
- ✅ 完善项目文档

## 许可证
请遵守相关法律法规使用本项目。

## 联系方式
如有问题，请提交Issue或联系开发团队。

