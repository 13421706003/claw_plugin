#!/bin/bash

# ================================
#  openHSD Plugin - Development
# ================================

echo ""
echo "  ================================"
echo "   openHSD Plugin - Development"
echo "  ================================"
echo ""

# 检查 Node.js
if ! command -v node &> /dev/null; then
    echo "  [ERROR] Node.js not found, please install Node.js"
    echo "  Download: https://nodejs.org"
    echo ""
    exit 1
fi

NODE_VER=$(node -v)
echo "  [INFO] Node.js version: $NODE_VER"

# 切换到脚本所在目录
cd "$(dirname "$0")"

# 安装依赖
if [ ! -d "node_modules" ]; then
    echo "  [INFO] Installing dependencies..."
    npm install
    echo ""
fi

echo "  [INFO] Environment: development"
echo "  [INFO] Press Ctrl+C to stop"
echo ""

export NODE_ENV=development
node src/index.js

if [ $? -ne 0 ]; then
    echo ""
    echo "  [ERROR] Plugin exited with code: $?"
else
    echo ""
    echo "  [INFO] Plugin stopped"
fi
