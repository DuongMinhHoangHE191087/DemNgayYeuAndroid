# Quick Android Emulator & App Runner for PowerShell
$ErrorActionPreference = "Continue"

Write-Host "================================================================" -ForegroundColor Cyan
Write-Host "          KHOI DONG VA TOI UU CHAY APP INLOVE (PS)              " -ForegroundColor Cyan
Write-Host "================================================================`n" -ForegroundColor Cyan

$AndroidSdk = "C:\Users\Admin\AppData\Local\Android\Sdk"
$JavaHome = "C:\Program Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot"
$env:JAVA_HOME = $JavaHome
$env:PATH = "$JavaHome\bin;$AndroidSdk\platform-tools;$env:PATH"

$AdbExe = "$AndroidSdk\platform-tools\adb.exe"
$EmulatorExe = "$AndroidSdk\emulator\emulator.exe"
$AvdName = "medium_phone"
$ApkPath = "$PSScriptRoot\app\build\outputs\apk\debug\app-debug.apk"
$PackageName = "com.aistudio.inlove.kmrv"
$ActivityName = "com.example.MainActivity"

# 1. Kiem tra thiet bi / emulator
Write-Host "[1/3] Kiem tra may ao hoac thiet bi..." -ForegroundColor Yellow
$state = (& $AdbExe get-state 2>$null)
$devices = (& $AdbExe devices 2>$null) | Select-String "device$", "emulator-"

if ($state -eq "device" -or $devices) {
    Write-Host "[OK] Phat hien may ao hoac thiet bi dang chay san sang!`n" -ForegroundColor Green
} else {
    Write-Host "[THONG BAO] Dang giai phong RAM tu Gradle daemons de may khong bi lag..." -ForegroundColor Yellow
    & "$PSScriptRoot\gradlew.bat" --stop 2>$null | Out-Null

    Write-Host "[THONG BAO] May ao chua bat. Dang khoi dong $AvdName (2 Cores, 2048MB RAM, GPU Host)..." -ForegroundColor Cyan
    
    # Clean stale locks
    $lockDir = "$env:USERPROFILE\.android\avd\$AvdName.avd\hardware-qemu.ini.lock"
    $lockFile = "$env:USERPROFILE\.android\avd\$AvdName.avd\multiinstance.lock"
    if (Test-Path $lockDir) { Remove-Item $lockDir -Recurse -Force -ErrorAction SilentlyContinue }
    if (Test-Path $lockFile) { Remove-Item $lockFile -Force -ErrorAction SilentlyContinue }

    Start-Process -FilePath $EmulatorExe -ArgumentList "-avd $AvdName -gpu host -no-boot-anim -cores 2 -memory 2048"
    
    Write-Host "Dang cho may ao khoi dong xong..." -ForegroundColor Yellow
    $timer = 0
    do {
        Start-Sleep -Seconds 2
        $timer += 2
        Write-Host -NoNewline "."
        $bootDone = (& $AdbExe shell getprop sys.boot_completed 2>$null).Trim()
    } while ($bootDone -ne "1" -and $timer -lt 120)
    Write-Host "`n[OK] May ao da san sang!`n" -ForegroundColor Green
}

# 2. Kiem tra APK
Write-Host "[2/3] Kiem tra file APK Debug..." -ForegroundColor Yellow
if (-not (Test-Path $ApkPath)) {
    Write-Host "Dang bien dich APK..." -ForegroundColor Cyan
    & "$PSScriptRoot\gradlew.bat" assembleDebug
} else {
    Write-Host "[OK] Da co file APK: $ApkPath`n" -ForegroundColor Green
}

# 3. Cai dat va chay
Write-Host "[3/3] Cai dat va mo ung dung InLove len may ao..." -ForegroundColor Yellow
& $AdbExe install -r -d $ApkPath
& $AdbExe shell am start -n "$PackageName/$ActivityName" -a android.intent.action.MAIN -c android.intent.category.LAUNCHER

Write-Host "`n================================================================" -ForegroundColor Green
Write-Host "       UNG DUNG INLOVE DA DUOC KHOI CHAY TREN MAY AO!           " -ForegroundColor Green
Write-Host "================================================================`n" -ForegroundColor Green
Start-Sleep -Seconds 4
