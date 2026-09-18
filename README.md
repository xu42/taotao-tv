# 桃桃TV · 精简版

专为电视、投影等大屏设备设计的第三方浏览器应用。精简版在保留「看电视直播 + 浏览全网视频」核心能力的前提下，**去掉一切与播放无关的冗余逻辑**，只做两件事。

> 设计宗旨：**简化、美化、易于操作使用**。

---

## 界面

首页只有两个入口，遥控器左右键切换：

| 入口 | 说明 |
| --- | --- |
| **直播** | 央视、卫视与地方台，遥控器上下左右快速切台 |
| **影视** | 聚合多家片源，分类浏览、点选即播 |

---

## 与上游版本的主要差异

1. **首页重做** — 由原来的多功能首页收敛为「直播 / 影视」两个大卡片；未选中浅色、选中深色描边，带轻微缩放动效，遥控器操作不再跳闪。
2. **彻底移除第三方浏览器内核** — 不再下载、不再打包任何内核文件，统一使用设备自带的系统 WebView。因此启动更快、安装包更小、也不需要额外的内核权限与后台服务。
3. **移除影视模块中的抖音入口** — 该入口原本依赖第三方内核能力，已整体删除。
4. **直播切台加载体验** — 切换频道时先显示跳动圆点遮罩，等真实画面就绪后再淡出，不再露出未渲染完成的网页。
5. **空分类导航修复** — 影视模块中某个片源没有资源时，遥控器向下键可以正确跳到下一个有内容的分区，不再卡住。
6. **应用内不再包含推广与版权声明内容**；仓库 `LICENSE`（Apache 2.0）保持原样。

---

## 目录结构

```
.
├── android/                  # Android TV 客户端（Java + DataBinding）
│   ├── app/
│   │   ├── src/main/java/com/xu42/tv/live/
│   │   └── src/main/assets/tv-web/   # 由脚本生成，不入库
│   ├── build_release.sh
│   └── README.md             # 客户端详细说明
├── web/tv-web/               # 内置网页源码（直播与影视的页面逻辑）
├── scripts/build-web.js      # 生成 assets 资源（也支持 --zip 打包热更新包）
├── docs/server-api.md        # 自建服务端接口文档
└── util/                     # 辅助脚本
```

---

## 编译

### 环境要求

- JDK 17
- Android SDK Platform 34 / Build Tools 34
- Node.js（构建内置网页资源用）

### 步骤

```bash
# 1. 生成内置网页资源（assets 不入库，必须先执行）
node scripts/build-web.js

# 2. 指定 Android SDK 路径
echo "sdk.dir=$HOME/Library/Android/sdk" > android/local.properties

# 3. 编译
cd android
./gradlew assembleRelease
```

产物：`android/app/build/outputs/apk/release/taotao-tv-<versionName>.apk`

> 未配置签名密钥时会自动回退到 debug 签名，产物依然可以直接安装，便于本地与 CI 使用。
> 配置正式签名只需在 `android/local.properties` 写入 `RELEASE_STORE_FILE` / `RELEASE_STORE_PASSWORD` / `RELEASE_KEY_ALIAS` / `RELEASE_KEY_PASSWORD`。

推送 tag（`v*`）时 GitHub Actions 会自动编译并把 APK 发布到对应 Release，详见 `.github/workflows/build-apk.yml`。

---

## 服务端

客户端的所有对外请求都收敛到自建服务端 `https://api.tv.xu42.com`：

- 应用升级与网页资源热更新：`/app/config`
- 崩溃上报：`/app/crash`
- 频道地址代理解析：`/channel/proxy`

接口字段、打包要求与部署示例见 [`docs/server-api.md`](docs/server-api.md)。

网页侧不硬编码域名——原生层在页面加载时把 `AppConfig.API_BASE` 注入到网页的 `_tvApiBase` 变量。

---

## 开发提示

- 所有地址常量的**唯一出处**是 `android/app/src/main/java/com/xu42/tv/live/util/AppConfig.java`。
- 内置网页使用「伪源」`https://tv.xu42.com/tv-web/`，由 `WebViewClientImpl.shouldInterceptRequest` 从本地读取，**不需要真实部署、也不会产生网络请求**。修改它需同步改 `AppConfig.WEB_ORIGIN` 与 `web/tv-web/js/end.js` 的 `_tvWebOrigin`。
- 改动 `web/tv-web` 后务必重新执行 `node scripts/build-web.js`，否则 App 里看到的还是旧页面。

---

## 声明

1. 本项目适配的均为正规正版资源发行平台，请勿将其用于适配不合规平台。
2. 软件本身不收费、不含任何插入广告；视频平台自身的广告与会员策略与本项目无关。
3. 请遵守当地法律法规与各平台服务条款使用。

## 许可证

本项目基于 Apache License 2.0 开源，详见 [`LICENSE`](LICENSE)。
