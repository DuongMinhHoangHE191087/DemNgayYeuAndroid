@echo off
title InLove - Realtime Debug Monitor
echo ========================================================
echo   INLOVE - HE THONG GIAM SAT DEBUG VA CHONG CRASH LIVE
echo ========================================================
echo.

set "ANDROID_SDK=C:\Users\Admin\AppData\Local\Android\Sdk"
set "ADB=%ANDROID_SDK%\platform-tools\adb.exe"

if not exist "%ADB%" (
    echo [LOI] Khong tim thay adb.exe tai: %ADB%
    pause
    exit /b 1
)

echo Dang ket noi thiet bi qua ADB...
"%ADB%" wait-for-device

echo.
echo ====================================================================
echo DANG THEO DOI LOGCAT: [Ads] [BillingManager] [AndroidRuntime] [Crash]
echo Nhan Ctrl+C de dung theo doi.
echo ====================================================================
echo.

"%ADB%" logcat -c
"%ADB%" logcat -v time Ads:V BillingManager:V AndroidRuntime:E FATAL:E InLoveApp:V *:S
pause
