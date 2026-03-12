@echo off
chcp 65001 >nul
title openHSD Plugin (Development)

echo.
echo  ================================
echo   openHSD Plugin - Development
echo  ================================
echo.

where node >nul 2>&1
if %errorlevel% neq 0 (
    echo  [ERROR] Node.js not found, please install Node.js
    pause
    exit /b 1
)

for /f "tokens=*" %%i in ('node -v') do set NODE_VER=%%i
echo  [INFO] Node.js version: %NODE_VER%

cd /d "%~dp0"

if not exist "node_modules" (
    echo  [INFO] Installing dependencies...
    npm install
    echo.
)

echo  [INFO] Environment: development
echo  [INFO] Press Ctrl+C to stop
echo.

set NODE_ENV=development
node src/index.js

echo.
if %errorlevel% neq 0 (
    echo  [ERROR] Plugin exited with code: %errorlevel%
) else (
    echo  [INFO] Plugin stopped
)
pause
