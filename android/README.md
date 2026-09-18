# 桃桃TV Android项目

## 项目简介
这是一个 Android TV 应用项目，使用 Java 开发，采用 DataBinding 架构。主要提供「直播」与「影视」两个入口。

## 技术栈
- **语言**: Java
- **架构**: DataBinding
- **WebView**: 系统内核（`android.webkit`），不打包任何第三方浏览器内核
- **平台**: Android TV

## 项目结构

```
app/src/main/
├── java/com/xu42/tv/live/
│   ├── StartActivity.java          # 启动页（检查配置/更新）
│   ├── HomeActivity.java           # 首页（直播 / 影视 两个入口）
│   ├── MainActivity.java           # 影视页面（Web 聚合点播）
│   ├── LiveActivity.java           # 直播页面
│   ├── BaseActivity.java           # Activity基类
│   ├── BaseWebViewActivity.java    # WebView Activity基类
│   ├── api/                        # API接口
│   ├── dao/                        # 数据库操作
│   ├── domain/                     # 数据模型
│   ├── impl/                       # 接口实现
│   ├── service/                    # 服务层
│   ├── util/                       # 工具类（AppConfig 为地址唯一出处）
│   └── utils/                      # 工具类
├── res/
│   ├── layout/                     # 布局文件
│   │   ├── activity_start.xml      # 启动页布局
│   │   ├── activity_home.xml       # 首页布局
│   │   ├── activity_main.xml       # 影视页面布局
│   │   ├── activity_live.xml       # 直播页面布局
│   │   └── dialog_exit.xml         # 退出对话框布局
│   ├── values/                     # 资源值
│   └── drawable/                   # 图片资源
└── assets/                         # 静态资源（由 scripts/build-web.js 生成，不入库）
    └── tv-web/                     # Web页面资源
```

## 核心页面说明

### 1. StartActivity（启动页）
- 应用入口，负责初始化
- 请求 `/app/config`，处理应用升级提示与网页资源热更新
- 完成后进入首页（直播 / 影视）

### 2. HomeActivity（首页）
- 只有「直播」「影视」两个入口，遥控器左右切换
- 未选中为浅色卡片、选中为深色卡片 + 描边，带轻微缩放动效
- 返回键弹出退出对话框

### 3. MainActivity（影视页面）
- 基于 WebView 加载视频聚合页面
- 支持遥控器按键控制
- 支持菜单操作（选集、画质、倍速等）
- 支持返回退出对话框

### 4. LiveActivity（直播页面）
- 电视直播功能
- 支持快速切台（上下左右键）
- 支持频道列表选择
- 切台期间显示「加载中」跳动圆点遮罩，避免露出未渲染完的网页
- 支持返回退出对话框

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
    binding = DataBindingUtil.setContentView(this, R.layout.activity_main);
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
```java
// 跳转到MainActivity
Intent intent = new Intent(this, MainActivity.class);
startActivity(intent);
finish();

// 跳转到LiveActivity
Intent intent = new Intent(this, LiveActivity.class);
startActivity(intent);
finish();
```

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

### 1. 退出对话框
- 按返回键弹出退出对话框（右侧1/3屏幕）
- 默认选中"退出"选项
- 再次按返回键或点击确认退出
- 支持"启动首页"切换功能

### 2. 首页入口
- 首页固定「直播 / 影视」两个入口，遥控器左右切换
- 焦点卡片选中态为深色 + 描边，未选中为浅色
- 选中记忆最近一次使用的入口

### 3. 直播功能
- 支持遥控器上下左右快速切台
- 支持频道列表选择
- 记录观看历史

### 4. 视频点播功能
- 支持选集、画质、倍速调节
- 支持播放进度记录
- 支持搜索功能

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

产物路径：`android/app/build/outputs/apk/release/taotao-tv-<versionName>.apk`

## 注意事项

1. **WebView 内核**: 直接使用设备自带的系统 WebView，不再下载任何第三方内核；因此不产生额外启动耗时与安装包体积
2. **横屏强制**: 所有Activity都设置为横屏模式（`SCREEN_ORIENTATION_LANDSCAPE`）
3. **SingleTask模式**: 主要Activity使用`singleTask`启动模式，避免重复创建
4. **WebView内存**: 注意WebView的内存管理，及时释放资源
5. **焦点处理**: TV应用需要特别注意焦点处理，确保遥控器可以正常导航
6. **assets 不入库**: `android/app/src/main/assets/tv-web` 由 `scripts/build-web.js` 生成，仓库里不提交

## 更新日志

### v1.0.0 (2025-10-03)
- ✅ 新增退出对话框功能
- ✅ 新增启动首页切换功能
- ✅ 优化返回键处理逻辑
- ✅ 完善项目文档

## 许可证
请遵守相关法律法规使用本项目。

## 联系方式
如有问题，请提交Issue或联系开发团队。

