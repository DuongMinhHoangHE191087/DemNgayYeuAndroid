# PowerShell Script to Stop & Cleanup Android Emulator & Free System Memory
$ErrorActionPreference = "SilentlyContinue"

Write-Host "================================================================" -ForegroundColor Cyan
Write-Host "       DON DEP & TAT MAY AO ANDROID AN TOAN (POWERSHELL)        " -ForegroundColor Cyan
Write-Host "================================================================`n" -ForegroundColor Cyan

$AndroidSdk = "C:\Users\Admin\AppData\Local\Android\Sdk"
$AdbExe = "$AndroidSdk\platform-tools\adb.exe"
$AvdName = "medium_phone"

Write-Host "[1/4] Gui lenh tat may ao an toan (adb emu kill)..." -ForegroundColor Yellow
if (Test-Path $AdbExe) {
    & $AdbExe emu kill 2>$null | Out-Null
}

Start-Sleep -Seconds 2

Write-Host "[2/4] Kiem tra va don dep tien trinh qemu/emulator/adb con sot lai..." -ForegroundColor Yellow
Get-Process -Name "*qemu*", "*emulator*" -ErrorAction SilentlyContinue | Stop-Process -Force -ErrorAction SilentlyContinue

Write-Host "[3/4] Giai phong bo nho RAM tu Gradle daemons..." -ForegroundColor Yellow
& "$PSScriptRoot\gradlew.bat" --stop 2>$null | Out-Null

Write-Host "[4/4] Xoa cac file khoa lock..." -ForegroundColor Yellow
$lockDir = "$env:USERPROFILE\.android\avd\$AvdName.avd\hardware-qemu.ini.lock"
$lockFile = "$env:USERPROFILE\.android\avd\$AvdName.avd\multiinstance.lock"
if (Test-Path $lockDir) { Remove-Item $lockDir -Recurse -Force -ErrorAction SilentlyContinue }
if (Test-Path $lockFile) { Remove-Item $lockFile -Force -ErrorAction SilentlyContinue }

Write-Host "`n================================================================" -ForegroundColor Green
Write-Host "  DA TAT MAY AO & GIAI PHONG TOAN BO RAM, CPU THANH CONG!      " -ForegroundColor Green
Write-Host "================================================================`n" -ForegroundColor Green
Start-Sleep -Seconds 3
