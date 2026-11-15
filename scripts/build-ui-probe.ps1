param([string]$Sdk='D:\Work\Android-Sdk',[string]$Jdk='D:\Work\rea-tools\jdk-21')
$ErrorActionPreference='Stop'
$project=Split-Path $PSScriptRoot -Parent
$output="$project\build\ui-probe"
$tools="$Sdk\build-tools\36.0.0"
$android="$Sdk\platforms\android-35\android.jar"
$env:JAVA_HOME=$Jdk; $env:PATH="$Jdk\bin;$env:PATH"
New-Item -ItemType Directory -Force "$output\classes","$output\dex" | Out-Null
function Checked([string]$Exe,[string[]]$Arguments) { & $Exe @Arguments; if($LASTEXITCODE -ne 0){throw "UI probe build failed: $Exe"} }
if (!(Test-Path "$project\build\debug.keystore")) { throw 'Build Trealink first to create the local test signing key.' }
Checked "$Jdk\bin\javac.exe" @('-encoding','UTF-8','--release','8','-cp',$android,'-d',"$output\classes","$project\tests\ui\UiSnapshot.java")
Checked "$Jdk\bin\jar.exe" @('--create','--file',"$output\classes.jar",'-C',"$output\classes",'.')
Checked "$tools\d8.bat" @('--lib',$android,'--min-api','26','--output',"$output\dex","$output\classes.jar")
Checked "$tools\aapt.exe" @('package','-f','-M',"$project\tests\ui\AndroidManifest.xml",'-I',$android,'-F',"$output\unsigned.apk")
Push-Location "$output\dex"
try { Checked "$tools\aapt.exe" @('add',"$output\unsigned.apk",'classes.dex') } finally { Pop-Location }
Checked "$tools\zipalign.exe" @('-f','4',"$output\unsigned.apk","$output\aligned.apk")
Checked "$tools\apksigner.bat" @('sign','--ks',"$project\build\debug.keystore",'--ks-pass','pass:android','--key-pass','pass:android','--out',"$output\UiProbe.apk","$output\aligned.apk")
