@echo off
chcp 65001 >nul
cd /d "%~dp0"

if not exist "node_modules" (
    echo Installing dependencies...
    npm install
)

set NODE_ENV=production
start "" "%~dp0node_modules\electron\dist\electron.exe" "%~dp0electron\main.cjs"
