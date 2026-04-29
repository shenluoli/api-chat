@echo off
setlocal
cd /d "%~dp0"
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0start-local-chat.ps1"
if errorlevel 1 (
  echo.
  echo 启动失败，请查看当前文件夹里的 启动日志.txt
  echo.
  pause
)
