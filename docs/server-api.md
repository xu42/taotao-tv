# 自建服务端接口文档（api.tv.xu42.com）

本文档描述本项目（油桃TV 精简版）在运行时依赖的自有服务端接口。客户端已把**所有**对外请求收敛到下面的地址，不再访问原作者的任何域名。

- **服务端基地址**：`https://api.tv.xu42.com`
- **内置网页伪源**：`https://tv.xu42.com/tv-web/`（**不落到服务器**，见文末「伪源说明」）
- **客户端常量集中配置**：`android/x5/app/src/main/java/tv/utao/x5/util/AppConfig.java`

> 说明：接口一共有 3 个「动态接口」+ 若干「静态文件」，全部由同一个域名提供。除 `/channel/proxy` 外都是 GET。

---

## 1. 接口总览

| 方法 | 路径 | 用途 | 客户端调用点 |
| --- | --- | --- | --- |
| GET | `/app/config` | 版本检查 + 资源热更新配置 | `api/ConfigApi.java` |
| POST | `/app/crash` | 崩溃日志上报（正文为纯文本） | `service/CrashHandler.java` |
| GET | `/channel/proxy` | 频道地址代理解析（目前用于四川广电等） | `web/tv-web/js/tv/sctv/sctv.js` |
| GET | `/res/tv-web.zip` | 内置网页资源更新包（静态文件，zip） | `service/UpdateService.java` |
| GET | `/app/download` | APK 下载地址（静态文件） | `StartActivity.java` / `BaseWebViewActivity.java` |
| GET | `/x5/*.tbs` | X5 内核下载（可选，也可继续用腾讯官方地址） | `service/UpdateX5Service.java` |

客户端会在启动时请求 `/app/config`，并根据返回内容决定：是否提示升级 APK、是否下载新的网页资源包、是否下载 X5 内核。

---

## 2. GET /app/config

应用启动、以及网页里调用 `querySysInfo` / `updateApk` 能力时都会请求它。

### 2.1 请求

```
GET /app/config?num={32|64}&api={androidSdkInt}&ver={versionCode}
```

| 参数 | 类型 | 说明 |
| --- | --- | --- |
| `num` | string | CPU 位数，`64` 或 `32` |
| `api` | int | 设备 Android SDK 版本号（如 28） |
| `ver` | int | 当前 APK 的 `versionCode` |

> 客户端**不会**再上报 `androidId` 等设备唯一标识，参数只有上面 3 个。

### 2.2 响应

返回 **JSON 对象**（必须以 `{` 开头，否则客户端认为失败；不要返回数组或 HTML）。

```jsonc
{
  "res": {                         // 可选：网页资源热更新
    "version": 62,                 // 资源版本号，必须大于内置 update.json 里的版本才会更新
    "url": "https://api.tv.xu42.com/res/tv-web.zip",
    "skipFirst": true,             // 解压时是否去掉 zip 里的第一层目录
    "update": true                 // false 时客户端完全不检查资源更新
  },
  "apk": {                         // 可选：应用自更新（缺省则不提示升级）
    "version": 5,                  // 最新 versionCode
    "url": "https://api.tv.xu42.com/app/download",
    "desc": "本次更新内容……",
    "force": false                 // true 为强制更新
  },
  "x5Url": {                       // 可选：X5 内核下载地址；缺省则跳过内核下载
    "32": ["https://.../armeabi.tbs"],
    "64": ["https://.../arm64-v8a.tbs"]
  },
  "datas": [                       // 可选：历史遗留字段，当前版本未使用
    { "code": "video", "url": "video.json", "v": "2024/11/19/001" }
  ]
}
```

字段说明与约束：

- `res`、`apk`、`x5Url`、`datas` **全部可选**。最小可用响应就是 `{"res":{"version":1,"url":"...","update":false}}`；甚至 `{}` 也能让应用正常进入首页（只是没有热更新）。
- `apk` 若存在，`version` / `url` 必须齐全；`version > 当前 versionCode` 且 `force=false` 时用户可选择「稍后」。
- `res` 的判定逻辑（`UpdateService.checkOnlineVersion`）：只有当 `res.update == true` **且** `res.version >` 内置 `update.json` 的 `res.version` 时，才会去下载 `res.url`。
- `x5Url` 的结构是「位数 -> 地址数组」；64 位取 `["64"][0]`，32 位在 Android 5.0+ 取 `["32"][0]`，更低版本取 `["32"][1]`。数组不可为空。

### 2.3 缓存

客户端对 `/app/config` 结果做 **24 小时内存缓存**（同一次进程内）。因此服务端可以做较长时间的 CDN 缓存，或每次返回最新值都可。

---

## 3. POST /app/crash

应用捕获到未处理异常时，把崩溃文本上传到服务端。

### 3.1 请求

```
POST /app/crash
Content-Type: application/json; charset=utf-8

<崩溃日志纯文本>
```

> 注意：客户端用 OkHttp 发送，`Content-Type` 被固定为 `application/json`，但**正文其实是纯文本**（不是 JSON）。服务端按原始 body 读取即可，建议直接落地成文件。

崩溃文本格式（`CrashHandler.dumpExceptionToStr`）：

```
2026-09-18-123456
SYS API: <androidId> <32|64> <sdkInt>
App Version: <versionName>_<versionCode>
OS Version: <release>_<sdkInt>
Vendor: <manufacturer>
Model: <model>
CPU ABI: ["arm64-v8a", ...]

<异常堆栈>
```

### 3.2 响应

任意 **2xx** 即视为成功；成功后客户端会删除本地崩溃文件。非 2xx 会保留文件、下次启动重试。

---

## 4. GET /channel/proxy

用于无法直接播的频道（目前主要是四川广电 `tag=1..9`）：由服务端带正确 `Referer` 去解析真实播放地址，再把地址以**纯文本**返回。

### 4.1 请求

```
GET /channel/proxy?tag={tag}
User-Agent: <客户端 UA>
Referer: https://api.tv.xu42.com     （由客户端 header `tv-ref` 映射而来）
```

| 参数 | 说明 |
| --- | --- |
| `tag` | 频道标识，取自 `js/tv/sctv/live.html?tag=N` 中的 `N`（目前为 1~9） |

### 4.2 响应

- **成功**：HTTP 200，`Content-Type: text/plain`，正文直接就是播放地址（m3u8 / flv 等），例如：

  ```
  https://xxx.sctv.com/live/xxx.m3u8
  ```

  客户端会把响应正文当作播放地址直接交给播放器，并**每 5 分钟重新请求一次**做直播刷新（`reloadLive`）。因此地址需要能长期稳定返回；地址变化时直接返回新地址即可。

- **失败**：返回空正文（客户端会忽略），不要返回 HTML 页面。

### 4.3 服务端实现提示

服务端需要：

1. 按 `tag` 请求对应频道页面（如 `https://www.sctv.com/...`）；
2. 解析出真正的流地址；
3. 请求流地址时带上站点要求的 `Referer` / `User-Agent`；
4. 把最终地址以纯文本返回。

若不需要该频道，可在 `web/tv-web/js/cctv/tv2.yml` 里删除 `tag: sctv` 分组后重新打包，客户端就不会再调用该接口。

---

## 5. 静态文件：资源更新包 /res/tv-web.zip

当 `/app/config` 指示需要热更新时，客户端下载该 zip 并解压到应用私有目录 `filesDir/tv-web/`，覆盖内置资源。

### 5.1 打包要求

- 内容 = `web/tv-web` 目录的完整产物（即 `scripts/build-web.js` 拷贝进 `assets/tv-web` 的那一份）。
- **根目录结构必须与内置资源一致**，解压后要能直接看到 `video.html`、`index.html`、`js/`、`css/`、`img/` 等。
- `res.skipFirst = true` 表示「打包时把文件放在一个顶层目录里」，解压时会自动去掉第一层。例如 zip 里是：

  ```
  tv-web/video.html
  tv-web/js/...
  ```

  就必须设置 `skipFirst: true`；若 zip 里直接是 `video.html`、`js/...`，则设置 `skipFirst: false`。

- 仓库已提供打包脚本，可产出符合要求的 zip：

  ```bash
  node scripts/build-web.js --zip      # 生成 android/x5/app/src/main/assets/tv-web.zip
  ```

### 5.2 版本推进

发布新资源时：

1. 重新打包 zip 并上传覆盖 `/res/tv-web.zip`；
2. 递增 `/app/config` 里的 `res.version`（必须大于客户端内置 `web/tv-web/update.json` 的 `res.version`）；
3. 可选：同步更新仓库里的 `web/tv-web/update.json` / `update.yml`，让内置版本号与新包一致，避免新装用户重复下载。

---

## 6. 静态文件：APK /app/download

`/app/config` 的 `apk.url` 指向该地址，客户端下载后用系统安装器安装。建议：

- 直接 302 跳转到最新 APK，或直接返回 APK 文件（`Content-Type: application/vnd.android.package-archive`）。
- 文件名建议带版本号，例如 `taotao-tv-v1.2.0.apk`。
- 配合 GitHub Actions：tag 触发的构建会把 APK 上传到对应 Release（见 `.github/workflows/`）。也可以让服务端把 `/app/download` 重定向到 Release 资源地址。

---

## 7. X5 内核 /x5/*.tbs（可选）

`x5Url` 指向 `.tbs` 内核包。若不想自行托管，可以继续使用腾讯官方地址（仓库内置 `update.json` 里已填好官方链接），或干脆在 `/app/config` 里不返回 `x5Url`，客户端会跳过内核下载、直接使用系统 WebView。

---

## 8. 部署参考

### 8.1 目录结构建议

```
/var/www/tv-api/
├── app/
│   ├── config          # 动态接口（由你的后端生成）
│   ├── crash           # 动态接口（接收并落地日志）
│   └── download        # 静态：最新 APK
├── channel/
│   └── proxy           # 动态接口（频道代理解析）
├── res/
│   └── tv-web.zip      # 静态：网页资源包
└── x5/
    ├── armeabi.tbs
    └── arm64-v8a.tbs
```

### 8.2 参考 Nginx 配置

```nginx
server {
    listen 443 ssl http2;
    server_name api.tv.xu42.com;

    ssl_certificate     /etc/letsencrypt/live/api.tv.xu42.com/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/api.tv.xu42.com/privkey.pem;

    # 配置接口：交给后端应用
    location = /app/config {
        proxy_pass http://127.0.0.1:8080;
        proxy_set_header Host $host;
        add_header Cache-Control "public, max-age=300";
    }

    # 崩溃上报：纯文本正文，直接透传
    location = /app/crash {
        client_max_body_size 1m;
        proxy_pass http://127.0.0.1:8080;
    }

    # 频道代理：text/plain 返回播放地址
    location = /channel/proxy {
        proxy_pass http://127.0.0.1:8080;
        proxy_read_timeout 30s;
    }

    # 资源包：静态文件，方便时用 CDN
    location /res/ {
        root /var/www/tv-api;
        add_header Cache-Control "public, max-age=600";
    }

    # APK
    location = /app/download {
        root /var/www/tv-api;
        try_files /app/latest.apk =404;
        default_type application/vnd.android.package-archive;
    }
}

# HTTP 跳 HTTPS
server {
    listen 80;
    server_name api.tv.xu42.com;
    return 301 https://$host$request_uri;
}
```

> 必须在 `api.tv.xu42.com` 上启用 **HTTPS**：客户端只接受同域名的 https，且 Nginx 需要放行 `Referer` 头（`/channel/proxy` 依赖它）。

### 8.3 最小后端伪代码（Node/Express 示例）

```js
const express = require('express');
const app = express();

// 只上报匿名兼容性信息，无需鉴权
app.get('/app/config', (req, res) => {
  const { num, api, ver } = req.query;
  res.json({
    res: {
      version: 63,                                 // 比内置 update.json 大
      url: 'https://api.tv.xu42.com/res/tv-web.zip',
      skipFirst: true,
      update: true,
    },
    apk: {
      version: 2,
      url: 'https://api.tv.xu42.com/app/download',
      desc: '修复若干问题',
      force: false,
    },
    // 不需要 X5 内核更新时可以不返回 x5Url
  });
});

app.post('/app/crash', express.text({ type: '*/*', limit: '1mb' }), (req, res) => {
  console.log('[CRASH]', req.body);      // 建议按天写文件
  res.sendStatus(200);
});

app.get('/channel/proxy', async (req, res) => {
  const url = await resolveChannel(req.query.tag, req.headers.referer);
  res.type('text/plain').send(url || '');
});

app.listen(8080, '127.0.0.1');
```

### 8.4 自测（curl）

```bash
# 配置接口：必须是 JSON 对象，且以 { 开头
curl -s 'https://api.tv.xu42.com/app/config?num=64&api=28&ver=1' | head -c 400; echo

# 崩溃上报
curl -s -X POST 'https://api.tv.xu42.com/app/crash' \
     -H 'Content-Type: application/json' --data 'test crash log' -o /dev/null -w '%{http_code}\n'

# 频道代理：应返回 text/plain 的播放地址
curl -s -i 'https://api.tv.xu42.com/channel/proxy?tag=1' -H 'Referer: https://api.tv.xu42.com' | head

# 资源包：应能下载且解压出 video.html
curl -s -o /tmp/tv-web.zip 'https://api.tv.xu42.com/res/tv-web.zip' && unzip -l /tmp/tv-web.zip | head
```

---

## 9. 客户端对接点一览

| 位置 | 作用 |
| --- | --- |
| `util/AppConfig.java` | 所有地址的唯一出处（`API_BASE` / `CONFIG_URL` / `CRASH_URL` / `CHANNEL_PROXY_URL`） |
| `api/ConfigApi.java` | 请求 `/app/config`，24h 缓存 |
| `service/UpdateService.java` | 读取配置、下载并解压 `/res/tv-web.zip` |
| `service/UpdateX5Service.java` | 读取 `x5Url` 下载内核 |
| `service/CrashHandler.java` | POST `/app/crash` |
| `StartActivity.java` / `BaseWebViewActivity.java` | 读取 `apk` 字段，处理应用升级 |
| `web/tv-web/js/tv/sctv/sctv.js` | 调用 `/channel/proxy`（基址来自注入的 `_tvApiBase`） |

> 网页侧的服务端地址由原生层在页面加载时注入：`WebViewClientImpl` 把 `AppConfig.API_BASE` 写进 `basex.js` 的 `_tvApiBase` 变量，网页脚本（如 `sctv.js`）直接读取该变量即可，无需在网页里硬编码域名。

---

## 10. 附录：伪源说明

`https://tv.xu42.com/tv-web/` 只是应用内部使用的「伪源」：

- `WebViewClientImpl.shouldInterceptRequest` 会拦截所有路径里包含 `tv-web/` 的请求，改为从 APK 内置资源（或热更新后的私有目录）读取；
- 因此**这个域名不需要真实部署、也不会产生网络请求**，断网也能打开首页 / 影视 / 直播页面；
- 它的作用是让网页拥有一个稳定的同源地址，便于 `localStorage`、相对路径、脚本注入等正常工作。

如果你希望把它改成别的字符串，只需修改 `AppConfig.WEB_ORIGIN`（务必以 `/` 结尾）以及 `web/tv-web/js/end.js` 里的 `_tvWebOrigin`，两者保持一致即可。
