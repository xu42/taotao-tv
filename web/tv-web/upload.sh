#!/bin/bash

# 上传 update.json 到服务器
# 使用环境变量：TV_UPLOAD_HOST / TV_UPLOAD_USER / TV_UPLOAD_PATH（可选）

# 从环境变量读取配置
SERVER_HOST="${TV_UPLOAD_HOST}"
SERVER_USER="${TV_UPLOAD_USER}"
REMOTE_PATH="${TV_UPLOAD_PATH:-update.json}"
LOCAL_FILE="$(dirname "$0")/update.json"

# 检查环境变量是否设置
if [ -z "$SERVER_HOST" ]; then
    echo "错误：环境变量 TV_UPLOAD_HOST 未设置"
    echo "请运行以下命令配置环境变量："
    echo "  ./config-env.sh"
    exit 1
fi

if [ -z "$SERVER_USER" ]; then
    echo "错误：环境变量 TV_UPLOAD_USER 未设置"
    echo "请运行以下命令配置环境变量："
    echo "  ./config-env.sh"
    exit 1
fi

echo "开始上传 update.json 到服务器..."

# 检查本地文件是否存在
if [ ! -f "$LOCAL_FILE" ]; then
    echo "错误：本地文件不存在 $LOCAL_FILE"
    exit 1
fi

# 使用 scp 上传文件
echo "上传文件: $LOCAL_FILE -> $SERVER_USER@$SERVER_HOST:$REMOTE_PATH"

# 确保远程目录存在
ssh -o StrictHostKeyChecking=no "$SERVER_USER@$SERVER_HOST" "mkdir -p $(dirname $REMOTE_PATH)" 2>/dev/null

# 上传文件
if scp -o StrictHostKeyChecking=no "$LOCAL_FILE" "$SERVER_USER@$SERVER_HOST:$REMOTE_PATH"; then
    echo "上传成功！"
    echo "文件已上传到: $REMOTE_PATH"
else
    echo "上传失败！"
    exit 1
fi

