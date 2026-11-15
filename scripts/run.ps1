param([string]$Serial, [string]$Sdk = 'D:\Work\Android-Sdk', [string]$Jdk = 'D:\Work\rea-tools\jdk-21')
$ErrorActionPreference = 'Stop'
$project = Split-Path $PSScriptRoot -Parent
& "$PSScriptRoot\build.ps1" -Sdk $Sdk -Jdk $Jdk
$adb = "$Sdk\platform-tools\adb.exe"
if (!$Serial) {
    $devices = @(& $adb devices | Select-String '^\S+\s+device$' | ForEach-Object { ($_.Line -split '\s+')[0] })
    if ($devices.Count -ne 1) { throw 'Connect exactly one device/emulator, or pass -Serial DEVICE_ID. No device was modified.' }
    $Serial = $devices[0]
}
& $adb -s $Serial install -r "$project\build\Trealink-debug.apk"
if ($LASTEXITCODE -ne 0) { throw 'APK installation failed.' }
& $adb -s $Serial shell am start -n dev.trealink.app/.MainActivity
if ($LASTEXITCODE -ne 0) { throw 'App launch failed.' }
