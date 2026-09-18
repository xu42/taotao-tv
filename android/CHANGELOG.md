# 更新日志

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

