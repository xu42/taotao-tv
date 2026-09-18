#!/usr/bin/env bash
#
# 生成 Android Release 签名密钥库（.jks），顺手写好 android/local.properties，
# 并打印 GitHub Secrets 需要的 base64。
#
# 用法：
#   ./android/scripts/gen-keystore.sh              # 交互式生成 android/keystore/release.jks
#   ./android/scripts/gen-keystore.sh my.jks       # 换一个文件名
#   ./android/scripts/gen-keystore.sh --b64        # 不生成，只把已有密钥库的 Secrets 值打出来
#
# 这个脚本只在你本机跑，密码不会离开终端，也不会进 git
# （android/.gitignore 已忽略 /keystore/、*.jks、*.keystore、local.properties）。
#
# 注意：本脚本里所有变量引用一律写成 ${VAR} 形式。
# 裸写 $VAR 时，紧跟在中文全角标点前会被 bash 当成变量名的一部分
# （例如 "$LOCAL_PROPS（已 ...）" 会去找名为「LOCAL_PROPS（」的变量），
# 在 set -u 下直接报 unbound variable 退出。
#
set -euo pipefail

trap 'echo "✗ 第 ${LINENO} 行出错，脚本已中止。" >&2' ERR

# ---------- 定位路径 ----------
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ANDROID_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"          # android/
KEYSTORE_DIR="${ANDROID_DIR}/keystore"
LOCAL_PROPS="${ANDROID_DIR}/local.properties"

# RELEASE_STORE_FILE 是相对 android/app/ 解析的（见 app/build.gradle 里的 file()），
# 这里反过来把 local.properties 里的相对路径还原成绝对路径。
store_path_from_props() {
  [ -f "${LOCAL_PROPS}" ] || return 1
  local rel
  rel="$(grep -E '^[[:space:]]*RELEASE_STORE_FILE=' "${LOCAL_PROPS}" | tail -1 | cut -d= -f2- || true)"
  [ -n "${rel}" ] || return 1
  ( cd "${ANDROID_DIR}/app" && cd "$(dirname "${rel}")" 2>/dev/null && printf '%s/%s\n' "$(pwd)" "$(basename "${rel}")" )
}

b64_of() {
  # macOS 的 base64 不换行且用 -i 读文件；Linux 默认 76 折行。
  # tr -d '\n' 两边都兜住。
  base64 -i "$1" 2>/dev/null | tr -d '\n' || base64 -w0 "$1"
}

print_secrets() {
  local ks="$1" alias_name="$2"
  printf '\n—————————————————— GitHub Secrets ——————————————————\n'
  printf '仓库 → Settings → Secrets and variables → Actions → New repository secret\n'
  printf '网页路径：https://github.com/xu42/taotao-tv/settings/secrets/actions\n\n'
  printf '  RELEASE_KEYSTORE_BASE64   = 下面这一整行（很长，一次复制完）\n'
  printf '  RELEASE_STORE_PASSWORD    = 你生成时输入的密钥库密码\n'
  printf '  RELEASE_KEY_ALIAS         = %s\n' "${alias_name}"
  printf '  RELEASE_KEY_PASSWORD      = 你生成时输入的密钥密码\n\n'
  printf -- '---------- RELEASE_KEYSTORE_BASE64（从这里到横线之前）----------\n'
  b64_of "${ks}"
  printf '\n----------------------------------------------------------------\n'
  printf '\n懒得手动复制的话，直接进剪贴板：\n'
  printf '  base64 -i %s | tr -d "\\n" | pbcopy\n' "${ks}"
}

# ---------- --b64 模式：不生成，只打印已有密钥库的 Secrets 值 ----------
if [ "${1:-}" = "--b64" ] || [ "${1:-}" = "-b" ]; then
  KS="$(store_path_from_props || true)"
  if [ -z "${KS}" ] || [ ! -f "${KS}" ]; then
    KS="${KEYSTORE_DIR}/release.jks"
  fi
  if [ ! -f "${KS}" ]; then
    echo "✗ 没找到密钥库。先跑一次不带参数的 $(basename "$0") 生成一个。" >&2
    exit 1
  fi
  ALIAS_NAME="$(grep -E '^[[:space:]]*RELEASE_KEY_ALIAS=' "${LOCAL_PROPS}" 2>/dev/null | tail -1 | cut -d= -f2- || true)"
  printf '使用密钥库：%s\n' "${KS}"
  print_secrets "${KS}" "${ALIAS_NAME:-<见 local.properties>}"
  exit 0
fi

# ---------- 生成模式 ----------
KEYSTORE_NAME="${1:-release.jks}"
KEYSTORE_PATH="${KEYSTORE_DIR}/${KEYSTORE_NAME}"

if ! command -v keytool >/dev/null 2>&1; then
  echo "✗ 找不到 keytool。它随 JDK 一起安装，请先装 JDK 17+（brew install --cask temurin@17）。" >&2
  exit 1
fi

if [ -f "${KEYSTORE_PATH}" ]; then
  echo "✗ ${KEYSTORE_PATH} 已存在，为避免覆盖（覆盖 = 永久失去升级能力）脚本拒绝继续。" >&2
  echo "  只想打印它的 Secrets 值：./android/scripts/gen-keystore.sh --b64" >&2
  echo "  想新建另一个就用别的文件名：./android/scripts/gen-keystore.sh release-2.jks" >&2
  exit 1
fi

echo "=============================================="
echo " 桃桃TV Release 签名密钥库生成"
echo " 输出路径：${KEYSTORE_PATH}"
echo "=============================================="
echo

# ---------- 别名 ----------
read -r -p "密钥别名 (alias) [直接回车用 tao-tao-tv]： " KEY_ALIAS
KEY_ALIAS="${KEY_ALIAS:-tao-tao-tv}"

# ---------- 密码 ----------
while :; do
  read -r -s -p "密钥库密码 (至少 6 位，输入时不显示)： " STORE_PASS; echo
  if [ "${#STORE_PASS}" -lt 6 ]; then
    echo "  太短了，至少 6 位，重来。"; continue
  fi
  read -r -s -p "再输一遍确认： " STORE_PASS2; echo
  if [ "${STORE_PASS}" != "${STORE_PASS2}" ]; then
    echo "  两次不一致，重来。"; continue
  fi
  break
done

read -r -p "密钥密码 (key password) [直接回车 = 与密钥库密码相同]： " KEY_PASS
if [ -z "${KEY_PASS}" ]; then
  KEY_PASS="${STORE_PASS}"
fi

# ---------- 证书信息 ----------
echo
echo "下面几项是证书里的署名信息，随便填、以后也改不了（不影响功能）。"
read -r -p "  名字 / 昵称 (CN) [taotao-tv]： " CN;    CN="${CN:-taotao-tv}"
read -r -p "  组织 (O)         [xu42]： "      ORG; ORG="${ORG:-xu42}"
read -r -p "  国家代码 (C)     [CN]： "         CTRY; CTRY="${CTRY:-CN}"

mkdir -p "${KEYSTORE_DIR}"

echo
echo ">> 正在生成密钥库（RSA 2048，有效期 10000 天 ≈ 27 年）..."
keytool -genkeypair \
  -keystore "${KEYSTORE_PATH}" \
  -alias "${KEY_ALIAS}" \
  -keyalg RSA \
  -keysize 2048 \
  -validity 10000 \
  -storepass "${STORE_PASS}" \
  -keypass "${KEY_PASS}" \
  -dname "CN=${CN}, OU=dev, O=${ORG}, C=${CTRY}"

echo
echo ">> 写入 ${LOCAL_PROPS} ..."
# local.properties 里可能已有 sdk.dir，先删掉旧的 RELEASE_* 行再追加，避免重复
if [ -f "${LOCAL_PROPS}" ]; then
  TMP="$(mktemp)"
  grep -v -E '^[[:space:]]*RELEASE_(STORE_FILE|STORE_PASSWORD|KEY_ALIAS|KEY_PASSWORD)=' "${LOCAL_PROPS}" > "${TMP}" || true
  mv "${TMP}" "${LOCAL_PROPS}"
fi
{
  # 注意 ../ —— build.gradle 里的 file() 相对 android/app/ 解析，
  # 与 CI 写入的路径保持一致（真实文件在 android/keystore/ 下）
  echo "RELEASE_STORE_FILE=../keystore/${KEYSTORE_NAME}"
  echo "RELEASE_STORE_PASSWORD=${STORE_PASS}"
  echo "RELEASE_KEY_ALIAS=${KEY_ALIAS}"
  echo "RELEASE_KEY_PASSWORD=${KEY_PASS}"
} >> "${LOCAL_PROPS}"

cat <<EOF

============================================ 完成 ============================================
密钥库  ：${KEYSTORE_PATH}
别名    ：${KEY_ALIAS}
本机配置：${LOCAL_PROPS}  (已 gitignore，不会进仓库)

现在可以本地验证正式签名：
    cd android && ./gradlew clean assembleRelease
    "\$(ls \$HOME/Library/Android/sdk/build-tools/*/apksigner | tail -1)" verify --print-certs \\
      "\$(ls -t app/build/outputs/apk/release/*.apk | head -1)"
    预期看到 CN=${CN}，而不是 CN=Android Debug
EOF

print_secrets "${KEYSTORE_PATH}" "${KEY_ALIAS}"

cat <<EOF

⚠️  两件事千万别忘：
  1. 把 ${KEYSTORE_PATH} 和上面这些密码**另行备份**（密码管理器 / 离线 U 盘）。
     这个文件丢了、或密码忘了，就再也签不出能覆盖升级的同款 App，只能让所有用户卸载重装。
  2. 密钥库文件本身**不要**提交到 git，也别塞进 issue / 聊天记录里。
============================================================================================
EOF
