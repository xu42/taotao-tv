#!/usr/bin/env node
/**
 * 桃桃TV 前端资源打包脚本
 *
 * 作用：
 *   1) 执行 web/tv-web/node.js，把 yml 源数据转换成 json（tv.json / update.json / data/*.json）
 *   2) 把 web/tv-web 下的静态资源拷贝到 android/app/src/main/assets/tv-web
 *      —— 这一步是安卓端能加载到页面和直播频道数据的前提
 *
 * 用法：
 *   node scripts/build-web.js             # 只做 yml->json 和拷贝
 *   node scripts/build-web.js --zip       # 额外生成 assets/tv-web.zip
 *   node scripts/build-web.js --no-yaml   # 跳过 yml->json（CI 快速构建时可用）
 *
 * 说明：
 *   - 只依赖 web/tv-web 下的 node_modules（js-yaml / archiver）
 *   - 不使用任何写死的本机绝对路径，clone 下来即可直接跑
 */

'use strict';

const fs = require('fs');
const path = require('path');
const { execFileSync } = require('child_process');
const { createRequire } = require('module');

const ROOT = path.resolve(__dirname, '..');
const WEB_DIR = path.join(ROOT, 'web', 'tv-web');
const ASSETS_TV_WEB = path.join(
  ROOT,
  'android',
  'app',
  'src',
  'main',
  'assets',
  'tv-web'
);

// 不需要打进 APK 的目录 / 文件（构建脚本、依赖、编辑器产物等）
const EXCLUDE_DIRS = new Set([
  'node_modules',
  '.git',
  '.idea',
  'doc',
  'docs',
  'test',
  'back',
  'web-ext-artifacts',
]);
const EXCLUDE_FILES = new Set([
  'node.js',
  'ya.js',
  'ya-old.js',
  'huo.js',
  'hun.js',
  'upload.sh',
  'ext-run.sh',
  '.gitignore',
  'package.json',
  'package-lock.json',
]);

const args = process.argv.slice(2);
const wantZip = args.includes('--zip');
const skipYaml = args.includes('--no-yaml');

function log(msg) {
  process.stdout.write(`[build-web] ${msg}\n`);
}

function rmrf(target) {
  if (fs.existsSync(target)) {
    fs.rmSync(target, { recursive: true, force: true });
  }
}

/** 递归拷贝静态资源，按排除表过滤 */
function copyWebAssets(srcDir, destDir) {
  fs.mkdirSync(destDir, { recursive: true });
  let fileCount = 0;

  for (const entry of fs.readdirSync(srcDir, { withFileTypes: true })) {
    const name = entry.name;
    const src = path.join(srcDir, name);
    const dest = path.join(destDir, name);

    if (entry.isDirectory()) {
      if (EXCLUDE_DIRS.has(name)) continue;
      fileCount += copyWebAssets(src, dest);
      continue;
    }
    if (EXCLUDE_FILES.has(name)) continue;
    if (name.endsWith('.md')) continue;

    fs.copyFileSync(src, dest);
    fileCount += 1;
  }
  return fileCount;
}

/** yml -> json（复用前端仓库自带脚本，保证和上游一致） */
function runYamlToJson() {
  const script = path.join(WEB_DIR, 'node.js');
  if (!fs.existsSync(script)) {
    log('未找到 web/tv-web/node.js，跳过 yml->json');
    return;
  }
  if (!fs.existsSync(path.join(WEB_DIR, 'node_modules'))) {
    log('web/tv-web/node_modules 不存在，跳过 yml->json（请先执行 npm install）');
    return;
  }
  execFileSync(process.execPath, [script], { cwd: WEB_DIR, stdio: 'inherit' });
}

/** 可选：生成 tv-web.zip（保持和上游资源包结构一致，顶层为 dist 目录） */
function makeZip() {
  let archiver;
  try {
    archiver = createRequire(path.join(WEB_DIR, 'package.json'))('archiver');
  } catch (e) {
    log('未安装 archiver，跳过 zip 生成');
    return;
  }
  const zipPath = path.join(path.dirname(ASSETS_TV_WEB), 'tv-web.zip');
  const output = fs.createWriteStream(zipPath);
  const archive = archiver('zip', { zlib: { level: 9 } });
  archive.pipe(output);
  archive.directory(ASSETS_TV_WEB, 'tv-web');
  archive.finalize();
  output.on('close', () => log(`已生成 ${path.relative(ROOT, zipPath)}`));
}

function main() {
  if (!fs.existsSync(WEB_DIR)) {
    log(`找不到前端目录：${WEB_DIR}`);
    process.exit(1);
  }

  if (!skipYaml) {
    log('执行 yml -> json …');
    runYamlToJson();
  }

  log('拷贝静态资源到 assets …');
  rmrf(ASSETS_TV_WEB);
  const count = copyWebAssets(WEB_DIR, ASSETS_TV_WEB);
  log(`已拷贝 ${count} 个文件 -> ${path.relative(ROOT, ASSETS_TV_WEB)}`);

  // 产物自检：首页与直播频道数据是硬依赖，缺失说明打包不完整
  const mustHave = [
    'video.html',
    'js/video/home.js',
    'js/cctv/tv.json',
    'js/common.js',
    'js/myfocus.js',
    'css/my.css',
  ];
  const missing = mustHave.filter((f) => !fs.existsSync(path.join(ASSETS_TV_WEB, f)));
  if (missing.length) {
    log(`缺少必要文件：${missing.join(', ')}`);
    process.exit(1);
  }

  if (wantZip) {
    makeZip();
  }
  log('完成 ✅');
}

main();
