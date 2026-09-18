@echo off
title InLove - Build, Install and Launch App
echo ========================================================
echo   INLOVE - BIEN DICH, CAI DAT VA KHOI CHAY UNG DUNG
echo ========================================================
echo.

cd /d "%~dp0\.."

set "ANDROID_SDK=C:\Users\Admin\AppData\Local\Android\Sdk"
set "ADB=%ANDROID_SDK%\platform-tools\adb.exe"
set "APK=app\build\outputs\apk\debug\app-debug.apk"

if not exist "%ADB%" (
    echo [LOI] Khong tim thay adb.exe tai: %ADB%
    echo Vui long kiem tra Android SDK.
    pause
    exit /b 1
)

:: 1. Bien dich ban cap nhat APK moi nhat
echo [1/4] Dang bien dich ban cap nhat APK Debug (incremental build)...
call gradlew.bat assembleDebug
if errorlevel 1 (
    echo.
    echo [LOI] Bien dich APK that bai! Vui long kiem tra code.
    pause
    exit /b 1
)
echo [1/4] File APK da duoc bien dich thanh cong.

:: 2. Kiem tra may ao
echo [2/4] Dang kiem tra ket noi may ao qua ADB...
"%ADB%" wait-for-device

:: 3. Cai dat ban cap nhat
echo [3/4] Dang cai dat APK InLove vao may ao...
"%ADB%" install -r -d "%APK%"
if errorlevel 1 (
    echo.
    echo [LOI] Cai dat APK that bai!
    pause
    exit /b 1
)

:: 4. Khoi chay MainActivity
echo [4/4] Dang mo ung dung InLove tren man hinh may ao...
"%ADB%" shell am start -n com.aistudio.inlove.kmrv/com.example.MainActivity

echo.
echo ========================================================
echo   UNG DUNG INLOVE DA DUOC MO THANH CONG TREN MAY AO!
echo   Giao dien Romantic Rose Light Mode da san sang.
echo ========================================================
echo.
pause
