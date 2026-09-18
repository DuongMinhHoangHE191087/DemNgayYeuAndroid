@echo off
title InLove - Android Emulator Launcher
echo ========================================================
echo   INLOVE - KHOI CHAY MAY AO ANDROID GUI
echo ========================================================
echo.

set "ANDROID_SDK=C:\Users\Admin\AppData\Local\Android\Sdk"
set "EMULATOR_DIR=%ANDROID_SDK%\emulator"
set "ADB_PATH=%ANDROID_SDK%\platform-tools\adb.exe"

if not exist "%EMULATOR_DIR%\emulator.exe" (
    echo [LOI] Khong tim thay emulator.exe tai: %EMULATOR_DIR%
    echo Vui long kiem tra lai duong dan Android SDK.
    echo.
    pause
    exit /b 1
)

:: 1. Don dep cac lock file cu neu co
echo [1/3] Don dep tep khoa AVD cu...
del /f /q "%USERPROFILE%\.android\avd\medium_phone.avd\*.lock" >nul 2>&1

:: 2. Kiem tra xem may ao da co cua so GUI chua
powershell -NoProfile -Command "$p = Get-Process qemu-system-x86_64 -ErrorAction SilentlyContinue; if ($p -and $p.MainWindowHandle -ne 0) { exit 0 } else { exit 1 }"
if not errorlevel 1 (
    echo [THONG BAO] Cua so may ao Android da dang hien thi tren Desktop!
    goto :WAIT_DEV
)

:: Neu co tien trinh chay ngam khong co cua so, tat di de mo lai voi giao dien
echo [2/3] Dang khoi dong cua so may ao Android tren man hinh Desktop...
taskkill /F /IM qemu-system-x86_64.exe >nul 2>&1
taskkill /F /IM emulator.exe >nul 2>&1
del /f /q "%USERPROFILE%\.android\avd\medium_phone.avd\*.lock" >nul 2>&1

cd /d "%EMULATOR_DIR%"
start "" emulator.exe -avd medium_phone

:WAIT_DEV
echo [3/3] Dang doi thiet bi khoi dong va ket noi ADB...
"%ADB_PATH%" wait-for-device

echo.
echo ========================================================
echo   MAY AO ANDROID DA SAN SANG TREN MAN HINH WINDOWS!
echo   Ban co the dung chuot va ban phim de thao tac truc tiep.
echo ========================================================
echo.
pause
