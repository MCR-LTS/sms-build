#!/bin/bash
# 进入脚本所在目录
cd "$(dirname "$0")"

# 检查是否有 Java 环境
if ! command -v java &> /dev/null; then
    echo "错误: 未检测到 Java 环境，请先安装 JDK。"
    exit 1
fi

echo "正在编译..."
javac ClipboardServer.java

if [ $? -eq 0 ]; then
    echo "编译成功！"
    echo "------------------------------------------------"
    echo "服务正在运行，请在手机端输入以下 IP 之一："
    
    # 获取本机所有的非回环 IPv4 地址
    ifconfig | grep "inet " | grep -v 127.0.0.1 | awk '{print "  " $2}'
    
    echo "------------------------------------------------"
    echo "正在监听端口 8888... (按 Ctrl+C 停止)"
    java ClipboardServer
else
    echo "编译失败，请检查代码或环境。"
fi