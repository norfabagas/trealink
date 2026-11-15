param(
    [string]$Sdk = $(if ($env:ANDROID_HOME) { $env:ANDROID_HOME } elseif ($env:ANDROID_SDK_ROOT) { $env:ANDROID_SDK_ROOT } else { 'D:\Work\Android-Sdk' }),
    [string]$Jdk = $(if ($env:JAVA_HOME) { $env:JAVA_HOME } else { 'D:\Work\rea-tools\jdk-21' })
)
$ErrorActionPreference = 'Stop'
$project = Split-Path $PSScriptRoot -Parent
$build = Join-Path $project 'build'
$tools = Join-Path $Sdk 'build-tools\36.0.0'
$android = Join-Path $Sdk 'platforms\android-35\android.jar'
foreach ($required in @("$Jdk\bin\javac.exe", "$tools\aapt.exe", $android)) {
    if (!(Test-Path -LiteralPath $required)) { throw "Required tool missing: $required. Pass -Sdk and -Jdk." }
}
function Invoke-Checked([string]$Exe, [string[]]$Arguments) {
    & $Exe @Arguments
    if ($LASTEXITCODE -ne 0) { throw "$Exe failed with exit code $LASTEXITCODE" }
}
$env:JAVA_HOME = $Jdk
$env:PATH = "$Jdk\bin;$env:PATH"
foreach ($generated in @('classes', 'dex', 'staging')) {
    $target = [IO.Path]::GetFullPath((Join-Path $build $generated))
    if (!$target.StartsWith([IO.Path]::GetFullPath($build) + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) { throw 'Unsafe build output path.' }
    if (Test-Path -LiteralPath $target) { Remove-Item -LiteralPath $target -Recurse -Force }
}
New-Item -ItemType Directory -Force -Path "$build\classes", "$build\dex", "$build\staging" | Out-Null
# The project is dependency-free: compile against the Android framework, then dex/package/sign.
$sources = @(Get-ChildItem "$project\app\src\main\java" -Recurse -Filter '*.java' | ForEach-Object FullName)
Invoke-Checked "$Jdk\bin\javac.exe" (@('-encoding', 'UTF-8', '--release', '8', '-classpath', $android, '-d', "$build\classes") + $sources)
Invoke-Checked "$Jdk\bin\jar.exe" @('--create', '--file', "$build\classes.jar", '-C', "$build\classes", '.')
Invoke-Checked "$tools\d8.bat" @('--lib', $android, '--min-api', '26', '--output', "$build\dex", "$build\classes.jar")
Invoke-Checked "$tools\aapt.exe" @('package', '-f', '-M', "$project\app\src\main\AndroidManifest.xml", '-S', "$project\app\src\main\res", '-I', $android, '-F', "$build\staging\unsigned.apk")
Push-Location "$build\dex"
try { Invoke-Checked "$tools\aapt.exe" @('add', "$build\staging\unsigned.apk", 'classes.dex') }
finally { Pop-Location }
Invoke-Checked "$tools\zipalign.exe" @('-f', '4', "$build\staging\unsigned.apk", "$build\staging\aligned.apk")
if (!(Test-Path "$build\debug.keystore")) {
    Invoke-Checked "$Jdk\bin\keytool.exe" @('-genkeypair', '-keystore', "$build\debug.keystore", '-storepass', 'android', '-keypass', 'android', '-alias', 'androiddebugkey', '-dname', 'CN=Trealink Debug', '-keyalg', 'RSA', '-keysize', '2048', '-validity', '10000')
}
Invoke-Checked "$tools\apksigner.bat" @('sign', '--ks', "$build\debug.keystore", '--ks-pass', 'pass:android', '--key-pass', 'pass:android', '--out', "$build\Trealink-debug.apk", "$build\staging\aligned.apk")
Invoke-Checked "$tools\apksigner.bat" @('verify', '--verbose', "$build\Trealink-debug.apk")
Write-Output "Built $build\Trealink-debug.apk"
