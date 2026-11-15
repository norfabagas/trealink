param([string]$Serial = 'emulator-5554', [string]$Sdk = 'D:\Work\Android-Sdk', [string]$Jdk = 'D:\Work\rea-tools\jdk-21', [switch]$ExtensionOnly)
$ErrorActionPreference = 'Stop'
if ($Serial -notmatch '^emulator-\d+$') { throw 'This test only runs against a disposable emulator.' }
$project = Split-Path $PSScriptRoot -Parent
$output = "$project\.local\android-test"
New-Item -ItemType Directory -Force $output | Out-Null
$adb = "$Sdk\platform-tools\adb.exe"
$script:checks = [Collections.Generic.List[string]]::new()
function ADB([string[]]$Arguments) {
    $result = & $adb -s $Serial @Arguments 2>&1
    if ($LASTEXITCODE -ne 0) { throw "ADB failed: $result" }
    return $result
}
function Snapshot {
    $lines=ADB @('shell','am','instrument','-w','dev.trealink.uiprobe/.UiSnapshot')
    $line=$lines | Where-Object { $_ -match '^INSTRUMENTATION_RESULT: xml=' } | Select-Object -First 1
    if (!$line) { throw "Non-suppressing UI probe failed: $lines" }
    $xml=$line.ToString().Substring('INSTRUMENTATION_RESULT: xml='.Length)
    $xml | Set-Content -Encoding utf8 "$output\ui.xml"
    return [xml]$xml
}
function Click-Node($Node) {
    $numbers = [regex]::Matches($Node.bounds, '\d+') | ForEach-Object { [int]$_.Value }
    ADB @('shell','input','tap', [string][int](($numbers[0]+$numbers[2])/2), [string][int](($numbers[1]+$numbers[3])/2)) | Out-Null
}
function Click-Text([string]$Text) {
    for ($attempt=0; $attempt -lt 4; $attempt++) {
        $xml = Snapshot
        $node = $xml.SelectNodes('//node') | Where-Object { $_.text -eq $Text -and $_.enabled -eq 'true' } | Select-Object -First 1
        if ($node) { Click-Node $node; return }
        ADB @('shell','input','swipe','160','570','160','270','300') | Out-Null
    }
    throw "UI element not found: $Text"
}
function Pass([string]$Label) { $script:checks.Add($Label); Write-Output "PASS $Label" }
function Share-Screen {
    Click-Text 'Start mirroring'
    $xml = Snapshot
    $one = $xml.SelectNodes('//node') | Where-Object text -EQ 'Share one app' | Select-Object -First 1
    if ($one) { Click-Node $one; Click-Text 'Share entire screen' }
    $xml = Snapshot
    $approve = $xml.SelectNodes('//node') | Where-Object { $_.text -in @('Share screen', 'Start now') } | Select-Object -First 1
    if (!$approve) { throw 'Unsupported screen-sharing dialog. Inspect emulator before continuing.' }
    Click-Node $approve
    $xml = Snapshot
    $passwordNode = $xml.SelectNodes('//node') | Where-Object { $_.text -match 'Session password: ([A-Z2-9]{8})' } | Select-Object -First 1
    if (!$passwordNode) { throw 'No active mirroring session found.' }
    return [regex]::Match($passwordNode.text, 'Session password: ([A-Z2-9]{8})').Groups[1].Value
}
function Capture([string]$Name, [string]$Password, [int]$Port) {
    & "$Jdk\bin\java.exe" -cp "$project\build\tests" dev.trealink.app.VncProbe 127.0.0.1 $Port $Password "$output\$Name.png"
    if ($LASTEXITCODE -ne 0) { throw 'Actual screen framebuffer verification failed.' }
}
function Stop-Sharing([string]$Label = 'Stop mirroring') {
    ADB @('shell','am','start','-W','-f','0x14000000','-n','dev.trealink.app/.MainActivity') | Out-Null
    Click-Text $Label
    $xml = Snapshot
    if (!($xml.SelectNodes('//node') | Where-Object text -EQ 'Ready to connect')) { throw 'Session did not stop.' }
    $listeners = ADB @('shell','ss','-ltn') | Out-String
    if ($listeners -match ':5900\b') { throw 'VNC port is still listening after stop.' }
}

& "$PSScriptRoot\test.ps1" -Jdk $Jdk
& "$PSScriptRoot\build-ui-probe.ps1" -Sdk $Sdk -Jdk $Jdk
ADB @('install','-r','-t',"$project\build\ui-probe\UiProbe.apk") | Out-Null
ADB @('install','-r',"$project\build\Trealink-debug.apk") | Out-Null
ADB @('shell','input','keyevent','KEYCODE_WAKEUP') | Out-Null
ADB @('shell','input','keyevent','KEYCODE_MENU') | Out-Null
ADB @('shell','am','start','-W','-f','0x14000000','-n','dev.trealink.app/.MainActivity') | Out-Null
$forwardPort = $null
$networkRedirect = $false
try {
    $xml = Snapshot
    if ($xml.SelectNodes('//node') | Where-Object text -EQ 'Stop mirroring') { Stop-Sharing }
    if ($xml.SelectNodes('//node') | Where-Object text -EQ 'Stop extend test') { Stop-Sharing 'Stop extend test' }
    Click-Text 'Menu'; Click-Text 'Output mode'; Click-Text 'Phone screen mirroring'
    if (!$ExtensionOnly) {
    Click-Text 'USB (ADB)'
    Click-Text 'Start mirroring'
    Click-Text 'Cancel'
    $xml = Snapshot
    if (!($xml.SelectNodes('//node') | Where-Object { $_.text -like 'Screen sharing cancelled*' })) { throw 'Cancellation state missing.' }
    Pass 'permission cancellation returns to ready UI'
    $usbPassword = Share-Screen
    Pass 'Android full-screen permission and foreground capture'
    $forwardPort = [int]((ADB @('forward','tcp:0','tcp:5900') | Out-String).Trim())
    Capture 'usb-frame' $usbPassword $forwardPort
    Pass 'USB ADB tunnel authenticates and delivers captured pixels'
    ADB @('shell','am','start','-a','android.settings.SETTINGS') | Out-Null
    Start-Sleep -Milliseconds 1200
    Capture 'settings-frame' $usbPassword $forwardPort
    if ((Get-FileHash "$output\usb-frame.png").Hash -eq (Get-FileHash "$output\settings-frame.png").Hash) { throw 'Screen did not update after switching apps.' }
    Pass 'sharing continues in background and updates after app switch'
    Stop-Sharing
    Pass 'Stop mirroring releases projection and closes listener'
    ADB @('forward','--remove',"tcp:$forwardPort") | Out-Null; $forwardPort = $null
    Click-Text 'Wi-Fi'
    $wifiPassword = Share-Screen
    if ($wifiPassword -eq $usbPassword) { throw 'Session password did not rotate.' }
    Pass 'new session requires consent and rotates password'
    $listeners = ADB @('shell','ss','-ltn') | Out-String
    if ($listeners -notmatch '(0\.0\.0\.0|\*|\[::\]):5900\b') { throw 'Network listener not found.' }
    # This exercises guest networking rather than an ADB TCP tunnel. Physical LAN remains untested.
    ADB @('emu','redir','add','tcp:15902:5900') | Out-Null; $networkRedirect = $true
    Capture 'network-frame' $wifiPassword 15902
    Pass 'Wi-Fi mode binds network interface and serves via emulator NAT'
    Stop-Sharing
    Pass 'second session stops cleanly'
    $null = Share-Screen
    ADB @('shell','input','keyevent','KEYCODE_SLEEP') | Out-Null
    Start-Sleep -Milliseconds 1000
    ADB @('shell','input','keyevent','KEYCODE_WAKEUP') | Out-Null
    ADB @('shell','input','keyevent','KEYCODE_MENU') | Out-Null
    ADB @('shell','am','start','-W','-f','0x14000000','-n','dev.trealink.app/.MainActivity') | Out-Null
    $xml = Snapshot
    if (!($xml.SelectNodes('//node') | Where-Object text -EQ 'Ready to connect')) { throw 'System projection stop was not handled.' }
    $listeners = ADB @('shell','ss','-ltn') | Out-String
    if ($listeners -match ':5900\b') { throw 'Listener survived system projection revocation.' }
    Pass 'system screen-lock revocation releases capture and network listener'
    }
    Click-Text 'Menu'; Click-Text 'Output mode'; Click-Text 'Independent display test (no Android Auto)'
    Click-Text 'Start extend test'
    $xml = Snapshot
    $node = $xml.SelectNodes('//node') | Where-Object { $_.text -match 'Session password: ([A-Z2-9]{8})' } | Select-Object -First 1
    if (!$node) { throw 'Independent session did not start.' }
    $extendPassword = [regex]::Match($node.text, 'Session password: ([A-Z2-9]{8})').Groups[1].Value
    $projection = ADB @('shell','dumpsys','media_projection') | Out-String
    if ($projection -match 'dev\.trealink\.app') { throw 'Independent session must not capture the phone display.' }
    Pass 'independent display starts without a phone screen-capture grant'
    $forwardPort = [int]((ADB @('forward','tcp:0','tcp:5900') | Out-String).Trim())
    ADB @('shell','am','start','-a','android.settings.SETTINGS') | Out-Null
    Start-Sleep -Milliseconds 1200
    & "$Jdk\bin\java.exe" -cp "$project\build\tests" dev.trealink.app.ExtensionProbe 127.0.0.1 $forwardPort $extendPassword "$output\extend-frame.png" view
    if ($LASTEXITCODE -ne 0) { throw 'Independent display was blank or mirrored phone content.' }
    Pass 'phone opens Settings while viewer retains independent test-surface pixels'
    $focusBefore = ADB @('shell','dumpsys','window') | Select-String 'mCurrentFocus=' | Out-String
    & "$Jdk\bin\java.exe" -cp "$project\build\tests" dev.trealink.app.ExtensionProbe 127.0.0.1 $forwardPort $extendPassword "$output\extend-tap.png" tap
    if ($LASTEXITCODE -ne 0) { throw 'Independent pointer test failed.' }
    $focusAfter = ADB @('shell','dumpsys','window') | Select-String 'mCurrentFocus=' | Out-String
    if ($focusBefore -notmatch 'com.android.settings' -or $focusAfter -notmatch 'com.android.settings') { throw 'Pointer input disturbed phone app focus.' }
    ADB @('shell','screencap','-p','/sdcard/trealink-independent-phone.png') | Out-Null
    ADB @('pull','/sdcard/trealink-independent-phone.png',"$output\extend-phone-settings.png") | Out-Null
    ADB @('shell','am','start','-W','-f','0x14000000','-n','dev.trealink.app/.MainActivity') | Out-Null
    $xml = Snapshot
    if (!($xml.SelectNodes('//node') | Where-Object { $_.text -match 'test taps: 1\b' })) { throw 'App-owned test tap was not delivered exactly once.' }
    Pass 'viewer tap reaches only the independent test surface and preserves phone app focus'
    Stop-Sharing 'Stop extend test'
    $displays = ADB @('shell','cmd','display','get-displays') | Out-String
    if ($displays -match 'Trealink independent test') { throw 'Independent virtual display was not released.' }
    Pass 'stopping extension removes its virtual display and closes the listener'
    Click-Text 'Menu'; Click-Text 'Output mode'; Click-Text 'Phone screen mirroring'
    ADB @('shell','screencap','-p','/sdcard/trealink-test-screen.png') | Out-Null
    ADB @('pull','/sdcard/trealink-test-screen.png',"$output\stopped-ui.png") | Out-Null
    @{ date = (Get-Date).ToString('yyyy-MM-dd'); serial = $Serial; checks = @($script:checks); result = 'PASS'; physicalLanTested = $false; receiverPairingTested = $false } |
        ConvertTo-Json -Depth 4 | Set-Content -Encoding utf8 (Join-Path $output $(if ($ExtensionOnly) { 'results-extension.json' } else { 'results.json' }))
    Write-Output "RESULT $($script:checks.Count) Android checks passed. Evidence: $output"
} finally {
    if ($forwardPort) { ADB @('forward','--remove',"tcp:$forwardPort") | Out-Null }
    if ($networkRedirect) { ADB @('emu','redir','del','tcp:15902:5900') | Out-Null }
}
