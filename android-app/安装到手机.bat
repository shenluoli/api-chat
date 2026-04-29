@echo off
setlocal
set ADB=C:\Users\aa\AppData\Local\Android\Sdk\platform-tools\adb.exe
set APK=D:\本地api\android-app\app\build\outputs\apk\debug\app-debug.apk

if not exist "%ADB%" (
  echo adb not found: %ADB%
  pause
  exit /b 1
)

if not exist "%APK%" (
  echo APK not found: %APK%
  pause
  exit /b 1
)

"%ADB%" devices
echo.
echo If your phone is listed as device, press Enter to install.
echo If it is unauthorized, allow USB debugging on your phone first.
pause
"%ADB%" install -r "%APK%"
pause
