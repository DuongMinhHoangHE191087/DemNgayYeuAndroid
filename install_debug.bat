@echo off
chcp 65001 >nul
setlocal EnableDelayedExpansion

:: ============================================================
::  InLove - Auto Build & Install Debug APK
::  Nhan dup file nay de tu dong build + cai vao dien thoai
:: ============================================================

set "ADB=%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe"
set "APK=app\build\outputs\apk\debug\app-debug.apk"
set "PACKAGE=com.aistudio.inlove.kmrv"
set "PROJECT_DIR=%~dp0"

echo.
echo  +================================================+
echo  ^|       InLove - Auto Build ^& Install           ^|
echo  +================================================+
echo.

:: -----------------------------------------------------------
:: BUOC 1: Kiem tra ADB
:: -----------------------------------------------------------
echo [1/4] Kiem tra ADB...
if not exist "%ADB%" (
    echo  [LOI] Khong tim thay ADB tai: %ADB%
    echo        Vui long cai dat Android SDK hoac chinh duong dan ADB trong file nay.
    pause
    exit /b 1
)
echo  [OK] ADB tim thay: %ADB%

:: -----------------------------------------------------------
:: BUOC 2: Kiem tra thiet bi
:: -----------------------------------------------------------
echo.
echo [2/4] Kiem tra thiet bi Android...
"%ADB%" kill-server >nul 2>&1
"%ADB%" start-server >nul 2>&1

set "DEVICE_ID="
set "DEVICE_STATUS="
for /f "tokens=1,2" %%a in ('"%ADB%" devices 2^>nul ^| findstr /v "List of" ^| findstr /v "^$"') do (
    if "%%b"=="device" (
        set "DEVICE_ID=%%a"
        set "DEVICE_STATUS=%%b"
    )
)

if "!DEVICE_ID!"=="" (
    echo  [LOI] Khong tim thay thiet bi Android hoac thiet bi dang OFFLINE.
    echo.
    echo  Hay kiem tra:
    echo    1. Cap USB da cam chua?
    echo    2. Bat "USB Debugging" trong "Tuy chon nha phat trien" chua?
    echo    3. Mo khoa man hinh dien thoai va chap nhan "Allow USB debugging"
    echo.
    pause
    exit /b 1
)
echo  [OK] Thiet bi: !DEVICE_ID! [!DEVICE_STATUS!]

:: -----------------------------------------------------------
:: BUOC 3: Kiem tra APK
:: -----------------------------------------------------------
echo.
echo [3/4] Kiem tra APK...

:: De tu dong BUILD truoc khi cai, bo comment (xoa dau ::) cac dong duoi:
:: echo  Dang build APK...
:: cd /d "%PROJECT_DIR%"
:: call gradlew.bat assembleDebug --no-daemon
:: if !ERRORLEVEL! NEQ 0 ( echo  [LOI] Build that bai! & pause & exit /b 1 )
:: echo  [OK] Build thanh cong!

if not exist "%PROJECT_DIR%%APK%" (
    echo  [LOI] Khong tim thay APK tai: %APK%
    echo        Hay build truoc bang lenh: gradlew.bat assembleDebug
    pause
    exit /b 1
)

for %%F in ("%PROJECT_DIR%%APK%") do (
    set "APK_SIZE=%%~zF"
    set "APK_DATE=%%~tF"
)
echo  [OK] APK: %APK%
echo       Kich thuoc: !APK_SIZE! bytes  -  Thoi gian: !APK_DATE!

:: -----------------------------------------------------------
:: BUOC 4: Cai dat APK
:: -----------------------------------------------------------
echo.
echo [4/4] Dang cai dat APK len !DEVICE_ID!...
"%ADB%" -s !DEVICE_ID! install -r "%PROJECT_DIR%%APK%"

if !ERRORLEVEL! EQU 0 (
    echo.
    echo  +================================================+
    echo  ^|   CAI DAT THANH CONG! Dang mo ung dung...    ^|
    echo  +================================================+
    "%ADB%" -s !DEVICE_ID! shell monkey -p %PACKAGE% -c android.intent.category.LAUNCHER 1 >nul 2>&1
    echo  [OK] Ung dung InLove da duoc mo tren dien thoai!
) else (
    echo.
    echo  [LOI] Cai dat that bai!
    echo        Thu go cai app cu: "%ADB%" uninstall %PACKAGE%
    pause
    exit /b 1
)

echo.
echo  Nhan phim bat ky de dong cua so...
pause >nul
endlocal
