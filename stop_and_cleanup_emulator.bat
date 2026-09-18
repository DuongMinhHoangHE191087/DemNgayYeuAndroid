@echo off
setlocal enabledelayedexpansion
title InLove - Emulator Cleanup & Shutdown

echo ================================================================
echo        DON DEP & TAT MAY AO ANDROID AN TOAN (INLOVE)
echo ================================================================
echo.

set "ANDROID_SDK=C:\Users\Admin\AppData\Local\Android\Sdk"
set "ADB_EXE=%ANDROID_SDK%\platform-tools\adb.exe"
set "AVD_NAME=medium_phone"

echo [1/4] Gui lenh tat may ao an toan (adb emu kill)...
if exist "%ADB_EXE%" (
    "%ADB_EXE%" emu kill >nul 2>&1
)

ping -n 3 127.0.0.1 >nul

echo [2/4] Kiem tra va don dep tien trinh emulator/qemu con sot lai...
taskkill /F /IM qemu-system-x86_64.exe /T >nul 2>&1
taskkill /F /IM emulator.exe /T >nul 2>&1

echo [3/4] Giai phong bo nho RAM tu Gradle daemons...
call "%~dp0gradlew.bat" --stop >nul 2>&1

echo [4/4] Xoa cac file khoa lock tranh loi cho lan chay sau...
if exist "%USERPROFILE%\.android\avd\%AVD_NAME%.avd\hardware-qemu.ini.lock" (
    rmdir /s /q "%USERPROFILE%\.android\avd\%AVD_NAME%.avd\hardware-qemu.ini.lock" >nul 2>&1
)
if exist "%USERPROFILE%\.android\avd\%AVD_NAME%.avd\multiinstance.lock" (
    del /f /q "%USERPROFILE%\.android\avd\%AVD_NAME%.avd\multiinstance.lock" >nul 2>&1
)

echo.
echo ================================================================
echo   DA TAT MAY AO & GIAI PHONG TOAN BO RAM, CPU THANH CONG!
echo ================================================================
echo.
echo Cua so se dong sau 3 giay...
ping -n 4 127.0.0.1 >nul
