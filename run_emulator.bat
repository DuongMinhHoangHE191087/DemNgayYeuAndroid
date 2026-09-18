@echo off
setlocal enabledelayedexpansion
title InLove - Android App Runner

echo ================================================================
echo           KHOI DONG VA TOI UU CHAY APP INLOVE (ANDROID)
echo ================================================================
echo.

set "ANDROID_SDK=C:\Users\Admin\AppData\Local\Android\Sdk"
set "EMULATOR_EXE=%ANDROID_SDK%\emulator\emulator.exe"
set "ADB_EXE=%ANDROID_SDK%\platform-tools\adb.exe"
set "JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot"
set "PATH=%JAVA_HOME%\bin;%ANDROID_SDK%\platform-tools;%PATH%"

set "AVD_NAME=medium_phone"
set "APK_PATH=%~dp0app\build\outputs\apk\debug\app-debug.apk"
set "PACKAGE_NAME=com.aistudio.inlove.kmrv"
set "ACTIVITY_NAME=com.example.MainActivity"

:: ================================================================
:: 1. KIEM TRA XEM MAY AO HOAC DIEN THOAI DA KET NOI CHUA
:: ================================================================
echo [1/3] Kiem tra may ao hoac thiet bi...

:: Khoi dong adb server neu chua bat
"%ADB_EXE%" start-server >nul 2>&1

:: Kiem tra thiet bi bang 2 cach: adb get-state hoac tim chu "device" trong adb devices
"%ADB_EXE%" get-state >nul 2>&1
if not errorlevel 1 goto DEVICE_ALREADY_RUNNING

"%ADB_EXE%" devices | findstr /C:"device" | findstr /V "List" >nul 2>&1
if not errorlevel 1 goto DEVICE_ALREADY_RUNNING

"%ADB_EXE%" devices | findstr /C:"emulator-" >nul 2>&1
if not errorlevel 1 goto DEVICE_ALREADY_RUNNING

:: ================================================================
:: 2. KHOI DONG MAY AO (NEU CHUA BAT)
:: ================================================================
echo [THONG BAO] May ao chua bat. Dang toi uu bo nho he thong truoc khi khoi dong...

:: Giai phong RAM tu cac Gradle daemons cu de may khong bi tran RAM/lag
call "%~dp0gradlew.bat" --stop >nul 2>&1

:: Don dep file lock cu (tranh loi running multiple emulators neu truoc do bi tat ngang)
if exist "%USERPROFILE%\.android\avd\%AVD_NAME%.avd\hardware-qemu.ini.lock" (
    rmdir /s /q "%USERPROFILE%\.android\avd\%AVD_NAME%.avd\hardware-qemu.ini.lock" >nul 2>&1
)
if exist "%USERPROFILE%\.android\avd\%AVD_NAME%.avd\multiinstance.lock" (
    del /f /q "%USERPROFILE%\.android\avd\%AVD_NAME%.avd\multiinstance.lock" >nul 2>&1
)

:: Bat may ao voi che do tiet kiem RAM (2GB), 2 Cores va tang toc GPU Host
echo Dang khoi dong "%AVD_NAME%" (2 Cores, 2048MB RAM, GPU Host, No Boot Anim)...
start "" "%EMULATOR_EXE%" -avd %AVD_NAME% -gpu host -no-boot-anim -cores 2 -memory 2048

echo Dang cho may ao khoi dong xong (vui long doi giay lat)...
set /a TIMER=0
:WAIT_BOOT_LOOP
ping -n 3 127.0.0.1 >nul
set /a TIMER+=2

:: Kiem tra adb da ket noi duoc voi may ao chua
"%ADB_EXE%" get-state >nul 2>&1
if errorlevel 1 (
    if !TIMER! GEQ 120 (
        echo.
        echo [CANH BAO] May ao mat nhieu thoi gian de khoi dong. Tiep tuc thu ket noi...
        goto DEVICE_READY
    )
    <nul set /p="."
    goto WAIT_BOOT_LOOP
)

:: Kiem tra Android da boot xong hoan toan vao Home Screen chua
for /f "tokens=*" %%a in ('"%ADB_EXE%" shell getprop sys.boot_completed 2^>nul') do set "BOOT_STATUS=%%a"
if "!BOOT_STATUS!"=="1" goto DEVICE_READY

if !TIMER! GEQ 120 (
    echo.
    echo [CANH BAO] Android dang load man hinh. Tiep tuc cai dat...
    goto DEVICE_READY
)
<nul set /p="."
goto WAIT_BOOT_LOOP

:DEVICE_ALREADY_RUNNING
echo [OK] Phat hien may ao hoac thiet bi dang chay san sang!
goto STEP_APK

:DEVICE_READY
echo.
echo [OK] May ao da khoi dong thanh cong!

:: ================================================================
:: 3. KIEM TRA FILE APK DEBUG
:: ================================================================
:STEP_APK
echo.
echo [2/3] Kiem tra ban build APK Debug...
if not exist "%APK_PATH%" (
    echo [THONG BAO] Chua co file APK, dang tien hanh build...
    call "%~dp0gradlew.bat" assembleDebug
    if errorlevel 1 (
        echo [LOI] Khong the build APK. Vui long kiem tra lai code.
        pause
        exit /b 1
    )
) else (
    echo [OK] File APK da san sang: %APK_PATH%
)

:: ================================================================
:: 4. CAI DAT VA KHOI CHAY UNG DUNG
:: ================================================================
echo.
echo [3/3] Cai dat va mo ung dung InLove...

:: Cai dat APK (su dung -r de cap nhat code moi ma khong mat du lieu cu)
echo Dang cai dat APK vao may ao...
"%ADB_EXE%" install -r -d "%APK_PATH%"
if errorlevel 1 (
    echo [THONG BAO] Thu cai dat lai...
    "%ADB_EXE%" install -r "%APK_PATH%"
)

:: Mo ung dung len man hinh
echo Dang khoi chay MainActivity...
"%ADB_EXE%" shell am start -n "%PACKAGE_NAME%/%ACTIVITY_NAME%" -a android.intent.action.MAIN -c android.intent.category.LAUNCHER >nul 2>&1

echo.
echo ================================================================
echo       UNG DUNG INLOVE DA DUOC KHOI CHAY TREN MAY AO!
echo ================================================================
echo.
echo Cua so se tu dong dong sau 5 giay...
ping -n 6 127.0.0.1 >nul
