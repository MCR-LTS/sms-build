#!/bin/bash
set -euo pipefail

cd "$(dirname "$0")"

JAR_PATH="build/smssync-server.jar"

if ! command -v java >/dev/null 2>&1; then
    echo "错误: 未检测到 Java 环境，请先安装 JRE 或 JDK。"
    exit 1
fi

if [ ! -f "$JAR_PATH" ]; then
    echo "错误: 未找到 $JAR_PATH，请先执行 build.sh 或使用 CI 产出的 artifact。"
    exit 1
fi

echo "正在启动服务端..."
java -jar "$JAR_PATH"
