param(
    [Parameter(Mandatory)][string]$ReaScript,
    [Parameter(Mandatory)][string]$JadxJar,
    [string]$Apk = "$PSScriptRoot\..\T-Link_1.3.12_APKPure.apk"
)
$ErrorActionPreference = 'Stop'
$project = Split-Path $PSScriptRoot -Parent
$output = Join-Path $project 'analysis'
$env:REA_JADX_MCP_JAR = (Resolve-Path -LiteralPath $JadxJar).Path
$env:REA_JADX_HEAP_MIB = '2048'
$Apk = (Resolve-Path -LiteralPath $Apk).Path
function Evidence([string]$Name, [string[]]$Arguments) {
    $json = & node $ReaScript @Arguments --format json
    if ($LASTEXITCODE -ne 0) { throw "REA command failed: $Arguments" }
    $parsed = $json | ConvertFrom-Json
    if (!$parsed.evidence_id) { throw "REA did not return completed Evidence: $json" }
    $json | Set-Content -Encoding utf8 (Join-Path $output "$Name.json")
    Write-Output "$Name : $($parsed.evidence_id)"
}
Evidence 'package' @('inspect-android-package', $Apk)
Evidence 'classes' @('search-android-classes', $Apk, 'com.realvnc.androidsampleserver')
Evidence 'main-activity' @('inspect-android-class', $Apk, 'com.realvnc.androidsampleserver.activity.VNCMobileServer')
Evidence 'onCreate' @('inspect-android-method', $Apk, 'com.realvnc.androidsampleserver.activity.VNCMobileServer', 'onCreate', '--overload-index', '0')
Evidence 'vnc-service' @('inspect-android-class', $Apk, 'com.realvnc.androidsampleserver.service.VncServerService')
Evidence 'service-onCreate' @('inspect-android-method', $Apk, 'com.realvnc.androidsampleserver.service.VncServerService', 'onCreate', '--overload-index', '0')
