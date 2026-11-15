param([string]$Jdk = 'D:\Work\rea-tools\jdk-21')
$ErrorActionPreference = 'Stop'
$project = Split-Path $PSScriptRoot -Parent
$classes = "$project\build\tests"
New-Item -ItemType Directory -Force $classes | Out-Null
$sources = @(Get-ChildItem "$project\tests" -Filter '*.java' | ForEach-Object FullName)
& "$Jdk\bin\javac.exe" -encoding UTF-8 --release 8 -d $classes "$project\app\src\main\java\dev\trealink\app\RfbServer.java" "$project\app\src\main\java\dev\trealink\app\TouchMapper.java" @sources
if ($LASTEXITCODE -ne 0) { throw 'Test compilation failed.' }
& "$Jdk\bin\java.exe" -cp $classes dev.trealink.app.RfbServerTest
if ($LASTEXITCODE -ne 0) { throw 'RFB tests failed.' }
& "$Jdk\bin\java.exe" -cp $classes dev.trealink.app.TouchMapperTest
if ($LASTEXITCODE -ne 0) { throw 'Touch mapping tests failed.' }
