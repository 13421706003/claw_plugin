@echo off
chcp 65001 >nul
title openHSD Plugin

echo.
echo  ================================
echo   openHSD Plugin 正在启动...
echo  ================================
echo.

:: 检查 node 是否安装
where node >nul 2>&1
if %errorlevel% neq 0 (
    echo  [错误] 未检测到 Node.js，请先安装 Node.js
    echo  下载地址：https://nodejs.org
    echo.
    echo  如果已安装但仍报此错误，请右键 .bat 文件选择
    echo  "以管理员身份运行" 或在命令行中直接运行
    pause
    exit /b 1
)

:: 打印 node 版本，确认找到
for /f "tokens=*" %%i in ('node -v') do set NODE_VER=%%i
echo  [提示] 检测到 Node.js %NODE_VER%

:: 切换到脚本所在目录
cd /d "%~dp0"

:: 检查依赖是否已安装
if not exist "node_modules" (
    echo  [提示] 首次启动，正在安装依赖...
    npm install
    echo.
)

echo  [提示] 按 Ctrl+C 可停止运行
echo.

node src/index.js

echo.
if %errorlevel% neq 0 (
    echo  [错误] 插件异常退出，错误码：%errorlevel%
) else (
    echo  [提示] 插件已停止运行
)
pause
