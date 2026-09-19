# 更新日志

## 未发布（2026-09-19）- 交互极简：菜单焦点区分 / 去掉收藏与历史 / 退出改提示

三条体验问题一次处理完，方向都是「更简单、看得更明白」：

### ✅ 切台菜单两栏焦点区分
- 原来左右键在「分类栏 / 频道栏」之间移动时，两栏长得一模一样，从画面上看不出焦点在哪一栏
- 新增 `menuColumn`（0 = 左栏分类 / 1 = 右栏频道）+ `applyMenuColumnHighlight()`：
  **焦点所在的栏正常亮度，另一栏压暗到 `MENU_DIM_ALPHA`(0.4f)**，拖动期间零重排
- 表头一起压暗：`activity_live.xml` 左栏表头加 `@+id/categoryHeader`、右栏表头加 `@+id/channelHeader`，
  与各自的 ListView 成组切换，视觉上是一整栏在变亮/变暗
- `showMenu()`、左右键切栏处都会调用，菜单每次打开时状态一定正确
- ⚠️ 实现选了「改 alpha」而不是「改背景/重排」：后者在 ListView 上会触发 `requestLayout`，
  TV 端容易出现切换瞬闪

### ✅ 取消收藏功能 + 取消「记住上次频道」，并整体移除 Room / SQLite
- **启动不再读历史**：`createInit()` 直接 `UpdateService.getDefaultChannel()`，永远是 CCTV-1
- **整体删掉数据库这一层**（收藏与历史都没了，SQLite 已无任何使用者）：
  - 删除 `dao/` 整个包：`AppDatabase`、`Favorite`、`FavoriteDao`、`History`、`HistoryDao`、`HistoryDaoX`
  - 删除 `service/FavoriteService`
  - 删除 `app/schemas/`（Room 导出的 schema）
  - `build.gradle` 去掉 `androidx.room:room-runtime` + `room-compiler`，以及 `room.schemaLocation`
    / `room.incremental` / `room.expandProjection` 三个 `annotationProcessor` 参数
  - `proguard-rules.pro` 去掉 `-keep class com.xu42.tv.live.dao.**` 与 `-dontwarn androidx.room.paging.**`
- 顺带清掉数据模型里的收藏字段：`domain/live/Vod.java` 的 `isFavorite`、`util/JsonTypes` 的 `HZ_LIST`
- ⚠️ `js/tv/cctv/tv.json` 仍在读，频道数据链路不受影响；原生 `JsInterface` 本来只暴露
  `toast` / `message` / `postJson` / `getJson` / `getHtml` 五个方法，**没有**收藏 / 历史的桥接入口，
  网页侧（`web/tv-web`）也确认没有任何 `favorite` / `history` 调用，属于干净的「只删不用改」

### ✅ 移除设置面板，退出改为「提示 + 二次返回」
- 按下返回键后弹出的设置面板（画质 / 收藏 / 退出）**整体删除**：画质切换实际无人可验证、
  收藏与退出都已另有去处，面板只剩下干扰
  - 删除 `layout/dialog_exit.xml`、`layout/item_hz_live.xml`
  - 删除 `drawable/dialog_card_bg.xml`、`drawable/dialog_button_bg.xml`、`color/dialog_button_text.xml`
  - 删除 `values/styles.xml`(`DialogActionButton`)（该文件已空，整体移除）
  - 删除只为画质列表服务的 `impl/BaseBindingAdapter`、`impl/BaseViewHolder`、`impl/IBaseBindingPresenter`、`domain/HzItem`
  - `LiveActivity` 里随之清掉 `exitDialogBinding`、`isExitDialogShowing`、`videoQualityData`、
    `setupHzListInExit()`、`HzLiveBindPresenter`、`updateFavoriteButtonInDialog()`、`initExitDialog/showExitDialog/hideExitDialog`
- 改为**胶囊提示 + 二次返回**：
  - 新增 `res/layout/activity_live.xml` 里的 `@+id/exitHint`：水平居中、垂直靠下但**不贴底**，
    文案「再按一次「返回」键退出应用」，背景走新增的 `res/drawable/toast_bg.xml`（近黑圆角 + 1dp 描边）
  - `showExitHint()` 淡入 150ms；`hideExitHint()` 淡出 180ms；
    2.4 秒（`EXIT_HINT_MS`）无操作由 `MSG_HIDE_EXIT_HINT` 自动收起
  - `handleBackKey()`：提示正在显示且距上次 ≤ `DOUBLE_BACK_MS`(1200ms) → `exitApp()`，否则记时刻并弹提示
  - 提示 `clickable=false` / `focusable=false`，不抢焦点
  - 长按返回键的重复事件继续用 `getRepeatCount() == 0` 过滤
  - **平板点右半屏 = 按一次返回键**（`dispatchTouchEvent` 走同一个 `handleBackKey`），行为统一
  - 提示底部间距在 `bind()` 里按 `heightPixels * 0.16f` **动态计算**，
    平板（约 600dp 高）与电视（1080p）观感一致，不写死 dp
- ⚠️ 打开切台菜单会先 `hideExitHint()`，避免两块 UI 叠在一起

### 体积
- ✅ Release 通用包 782 KB → **660 KB**（Room + recyclerview 依赖 + 设置面板资源全部移除）

## 未发布（2026-09-19）- 汲取原作者 APK 的三个体验修复

参考 `docs/竞品分析-油桃TV与土拨鼠大屏浏览器.md`，落地其 P0 三项（已在米 Pad 4 Plus 真机验证）：

- ✅ **沉浸式粘性全屏**：`BaseActivity.applyImmersiveMode()`（`setSystemUiVisibility(5894)`），
  在 `onCreate` / `onResume` / `onWindowFocusChanged(true)` 三处调用，
  状态栏与手势导航条在平板/盒子上彻底隐藏；异常时降级为 `FLAG_FULLSCREEN`
- ✅ **进后台真正停播**：`WebView.onPause()` / `stopLoading()` 停不住已经在跑的 HLS，
  现在改为「静音页内 video → 250ms 后卸载到 `about:blank` → 回前台按后台时长（≥2s 延迟 600ms）重载」。
  卸载前作废 `loadGeneration`、清 watchdog 与视频探测，避免把主动卸载误判成播放失败
  - 顺带修掉 `onPause()` 里 `setJavaScriptEnabled(false)` 的无效实现
  - 两处必要守卫：`onProgressChanged` 忽略 `about:*`（否则 `about:blank` 会写坏续播历史）、
    `onSourceFailed` 在后台停播期间直接返回（否则会在后台偷偷换源）
- ✅ **切台菜单分层半透明**：`menuPanel` 改透明基底，左栏分类 `#CC070A10`（80%）、
  右栏频道 `#B8070A10`（72%），直播画面透出来且层次分明；
  菜单文字补 `shadowColor=#CC000000` 文字阴影，纯白画面下依然清晰

### 新增
- ✅ `BaseActivity` 新增可覆盖钩子 `onEnterBackground()` / `onLeaveBackground(long)`
- ✅ `docs/img/menu-half-translucent.png`、`docs/img/exit-panel-centered.png`（真机截图）

## 未发布（2026-09-19）- 平板 / 电视交互优化

### 平板
- ✅ **切台菜单只占屏幕左半边**：`menuContainer` 拆成等宽的「菜单面板 + 右侧留白」两块；
  菜单内部两栏从固定 `320dp` 改成权重（左 34% / 右 66%），整体等比缩放
- ✅ **右侧留白可点收起菜单**（留白是全透明的，直播画面照常可见）
- ✅ **触屏分左右半屏**：点左半屏拉出切台菜单；点右半屏打开设置面板

### 电视
- ✅ **设置面板改为垂直水平居中**，近黑圆角卡片 + 焦点按钮反白（白底黑字），
  风格参考 Netflix 电视端；原来是贴在屏幕右侧 1/3 的长条
- ✅ **连按两次返回键直接退出应用**（两次间隔 ≤ 1.2 秒）；超过 1.2 秒再按只收起面板。
  已过滤长按产生的重复事件，长按返回键不会误退出
- ✅ 面板顶部显示当前频道 + 分类 + 正在使用的源；画质行无数据时整块隐藏
- ✅ 移除面板左侧旧的「遥控器操作提示」区域

### 清理
- ✅ 删除 `drawable/menu_button_background.xml`（改用新的 `dialog_button_bg`）
- ✅ 新增 `values/styles.xml`(`DialogActionButton`)、`drawable/dialog_card_bg.xml`、
  `drawable/dialog_button_bg.xml`、`color/dialog_button_text.xml`
- ✅ 面板按钮 `btnCancel` 更名 `btnClose`（文案「关闭」）

## v1.2.0 (2026-09-18) - 纯本地单模块版

### 新增 / 改动
- ✅ **移除影视（点播）模块**：删除 `MainActivity` / `BaseWebViewActivity`、
  影视相关 `domain`（DetailMenu / JdItem / RateItem / XjItem…）、`layout`（activity_main / item_jd…）、
  内置网页 (`video.html`、`js/video/`、各聚合源适配代码) 与 `bestv` 之外的全部点播分支
- ✅ **应用首页即直播页**：启动直接进入 `LiveActivity`，**续播上次观看的频道**，
  无历史记录时默认播 **CCTV-1**；删除 `StartActivity` / `HomeActivity` 及其布局
- ✅ **彻底移除远端依赖（纯本地）**：
  - 删除 `api/ConfigApi`、`call/*` 回调、`domain/ConfigDTO|Res|ApkInfo|ExtConfig|VersionData` 等服务端契约
  - 删除 DNS（`dns/HttpDns*`）、崩溃上报、网页资源热更新、APK 自升级、频道代理 `/channel/proxy`
  - 权限收敛为 `INTERNET` + `ACCESS_NETWORK_STATE`（去掉 `RECEIVE_BOOT_COMPLETED`、`REQUEST_INSTALL_PACKAGES`、`ACCESS_WIFI_STATE`）
  - 删除 `BootReceiver`、`FileProvider`(`xml/file_path.xml`)
  - `docs/server-api.md` 已删除
- ✅ **恢复单一 APK 产出**：移除 ABI 拆包逻辑（`splits.abi`、`-PabiSplit`），固定只出
  `taotao-tv-<versionName>.apk`
- ✅ **新增本地 `hls.min.js`**：内嵌网页不再依赖 CDN，彻底离线可用
- ✅ 删除依赖服务端代理的 8 个四川频道（四川卫视仍可通过央视频源收看）
- ✅ Release 包进一步下降到约 **910 KB**

### 修复问题
- ✅ 修复拆包时所有分包重名互相覆盖、写出损坏 APK 的问题

## v1.1.0 (2026-09-18) - 精简瘦身版

### 新增 / 改动
- ✅ **直播切台改为二级分类菜单**：左侧是分类（央视 / 卫视 / 各省地方台），右侧是该分类下的频道；
  列表里不再展示「源」，只留一个「N 源」角标，操作更简单
- ✅ **频道多源模型**：同名频道跨分组归并成一个频道并挂载多个源，默认央视网；
  播放失败（主帧报错 / HTTP≥400 / 超时 / 探测不到播放器）时**自动切到下一个源**并提示；
  播放中按**左右键**可手动换源
- ✅ **移除百视通（bestv）源**及其网页分支（源本身已失效）
- ✅ **版本号不再写死**：按编译那一刻生成，`versionName = yyyyMMdd.HHmm`、`versionCode = yyMMddHH`，
  APK 文件名也带上时间戳
- ✅ **体积优化**：`minifyEnabled` + `shrinkResources` + `resConfigs "zh","en"` + 剔除构建元数据，
  Release 包从 3.6MB 降到 **963KB**
- ✅ **可选 ABI 拆包**：`./gradlew assembleRelease -PabiSplit` 同时产出
  arm64-v8a / armeabi-v7a / x86 / x86_64 四个分包 + 一个通用包；默认只出通用包
  _（v1.2.0 已回退为单一通用包）_
- ✅ **移除 X5（TBS）内核**，改用系统 WebView；包名 `tv.utao.x5` → `com.xu42.tv.live`
- ✅ 清理无用目录与文件：`util/`、`img/`、`web/tv-web` 下废弃页面与脚本

### 修复问题
- ✅ 修复拆包时所有分包重名互相覆盖、写出损坏 APK 的问题
- ✅ 修复自动容灾被上一个源迟到的错误回调误触发的问题（按 URL 判定是否为陈旧错误）
- ✅ 修复菜单同步选中项时误重置当前分类下标的问题
- ✅ 修复切频道后左侧分类高亮不刷新的问题

## v1.0.1 (2025-10-03) - TV遥控器优化

### 修复问题
- ✅ 修复退出对话框精确占据屏幕右侧1/3宽度
- ✅ 修复点击启动首页选项后自动关闭对话框
- ✅ **修复TV遥控器焦点导航**：退出对话框显示时默认选中"退出"按钮
- ✅ **修复焦点切换**：支持遥控器上下键在按钮间切换焦点
- ✅ **修复LiveActivity按键冲突**：退出对话框显示时拦截上下左右键，避免误触发快速切台
- ✅ 优化按键处理优先级：退出对话框 > 频道菜单 > 快速切台

### 技术改进
1. **布局优化**：使用 `LinearLayout` + `layout_weight` 实现精确的1/3屏幕宽度
2. **焦点管理**：添加 `nextFocusUp/Down/Left/Right` 属性，实现完整的焦点导航循环
3. **按键分发逻辑**：参考频道菜单实现，优先处理退出对话框的按键事件

## v1.0.0 (2025-10-03)

### 新增功能

#### 1. 退出对话框功能
- ✅ 在 MainActivity 和 LiveActivity 中添加了退出对话框
- ✅ 对话框位于屏幕右侧，占据约1/3屏幕宽度
- ✅ 按返回键时弹出对话框，默认聚焦在"退出应用"按钮
- ✅ 再次按返回键或点击"退出应用"按钮即可退出
- ✅ 点击"取消"按钮或对话框背景可关闭对话框

#### 2. 启动首页切换功能
- ✅ 在退出对话框中新增"启动首页设置"功能
- ✅ 提供"视频点播"和"电视直播"两个选项
- ✅ 用户可以切换默认启动页面
- ✅ 设置会保存到 SharedPreferences，下次启动生效
- ✅ 实时显示当前启动首页设置

#### 3. 智能启动逻辑
- ✅ StartActivity 根据用户设置自动跳转到相应页面
- ✅ 默认启动到"视频点播"页面（MainActivity）
- ✅ 如果用户设置为"电视直播"，则启动到 LiveActivity

### 文件修改清单

#### 新增文件
1. **README.md** - 项目文档
   - 项目简介和技术栈
   - 项目结构说明
   - 开发规范和最佳实践
   - 编译和运行指南

2. **CHANGELOG.md** - 更新日志
   - 详细记录所有功能更新

3. **app/src/main/res/layout/dialog_exit.xml** - 退出对话框布局
   - 使用 DataBinding
   - 响应式布局设计
   - 支持TV遥控器焦点导航

#### 修改文件

1. **app/src/main/java/com/xu42/tv/live/MainActivity.java**
   - 导入 DialogExitBinding 和相关类
   - 添加 exitDialogBinding 和 isExitDialogShowing 属性
   - 修改 keyBack() 方法，改为显示退出对话框
   - 新增 showExitDialog() 方法
   - 新增 hideExitDialog() 方法
   - 新增 initExitDialog() 方法
   - 新增 updateStartPageHint() 方法

2. **app/src/main/java/com/xu42/tv/live/LiveActivity.java**
   - 导入 DialogExitBinding 和 ValueUtil
   - 添加 exitDialogBinding 和 isExitDialogShowing 属性
   - 新增 handleBackPress() 方法替代原来的直接跳转逻辑
   - 新增 showExitDialog() 方法
   - 新增 hideExitDialog() 方法
   - 新增 initExitDialog() 方法
   - 新增 updateStartPageHint() 方法

3. **app/src/main/java/com/xu42/tv/live/StartActivity.java**
   - 修改 to() 方法，添加启动页面选择逻辑
   - 根据 SharedPreferences 中的设置跳转到不同页面

4. **app/src/main/res/layout/activity_main.xml**
   - 在根布局中添加退出对话框的 include 标签

5. **app/src/main/res/layout/activity_live.xml**
   - 在根布局中添加退出对话框的 include 标签

### 技术实现细节

#### 1. DataBinding 使用
所有对话框相关的视图绑定都使用了 DataBinding：
```java
DialogExitBinding exitDialogBinding = DataBindingUtil.bind(dialogView);
```

#### 2. SharedPreferences 数据持久化
使用 ValueUtil 工具类进行数据存储：
```java
// 保存设置
ValueUtil.putString(this, "startPage", "main");  // 或 "live"

// 读取设置
String startPage = ValueUtil.getString(this, "startPage", "main");
```

#### 3. 焦点管理
- 对话框显示时自动聚焦到"退出应用"按钮
- 所有按钮都设置了 `focusable="true"` 和 `clickable="true"`
- 使用 TV 适配的背景 drawable：`@drawable/menu_button_background`

#### 4. 用户体验优化
- 对话框背景半透明，保持内容可见性
- 提供即时反馈（Toast 提示）
- 支持多种交互方式：遥控器、触摸
- 清晰的视觉层次和信息架构

### 使用说明

#### 如何退出应用
1. 在主页面或直播页面按返回键
2. 弹出退出对话框
3. 选择"退出应用"或再次按返回键即可退出
4. 选择"取消"可返回应用

#### 如何切换启动首页
1. 在主页面或直播页面按返回键
2. 在弹出的对话框中找到"启动首页设置"
3. 选择"视频点播"或"电视直播"按钮
4. 设置会立即保存并在下次启动时生效
5. 对话框会显示当前的启动首页设置

### 测试建议

#### 功能测试
- [ ] 测试 MainActivity 返回键弹出对话框
- [ ] 测试 LiveActivity 返回键弹出对话框
- [ ] 测试对话框显示后再次按返回键退出
- [ ] 测试"退出应用"按钮功能
- [ ] 测试"取消"按钮功能
- [ ] 测试点击背景关闭对话框
- [ ] 测试"视频点播"设置功能
- [ ] 测试"电视直播"设置功能
- [ ] 测试启动页面根据设置正确跳转

#### UI/UX 测试
- [ ] 测试对话框布局在不同分辨率下的显示
- [ ] 测试焦点导航是否正常
- [ ] 测试按钮的焦点高亮效果
- [ ] 测试 Toast 提示是否正常显示

#### 兼容性测试
- [ ] 测试在不同 Android TV 设备上的表现
- [ ] 测试遥控器按键响应
- [ ] 测试触摸操作（如果设备支持）

### 已知问题
无

### 待优化项
- 可考虑添加对话框显示/隐藏动画效果
- 可考虑添加更多启动选项（如记住上次页面）
- 可考虑添加快捷键说明

### 开发团队
- 开发人员：AI Assistant
- 测试人员：待定
- 发布日期：2025-10-03

---

## 技术支持

如遇到问题，请查看 README.md 或提交 Issue。

