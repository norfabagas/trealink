param([string]$Serial='emulator-5554', [string]$Sdk='D:\Work\Android-Sdk', [string]$Jdk='D:\Work\rea-tools\jdk-21')
$ErrorActionPreference='Stop'
if ($Serial -notmatch '^emulator-\d+$') { throw 'Touch tests require a disposable emulator, never a user phone.' }
$project=Split-Path $PSScriptRoot -Parent
$output="$project\.local\touch-test"
New-Item -ItemType Directory -Force $output | Out-Null
$adb="$Sdk\platform-tools\adb.exe"
$checks=[Collections.Generic.List[string]]::new()
function ADB([string[]]$Arguments) {
    $r=& $adb -s $Serial @Arguments 2>&1
    if ($LASTEXITCODE -ne 0) { throw "ADB failed: $r" }
    return $r
}
function Snapshot {
    $lines=ADB @('shell','am','instrument','-w','dev.trealink.uiprobe/.UiSnapshot')
    $line=$lines | Where-Object { $_ -match '^INSTRUMENTATION_RESULT: xml=' } | Select-Object -First 1
    if (!$line) { throw "Non-suppressing UI probe failed: $lines" }
    $xml=$line.ToString().Substring('INSTRUMENTATION_RESULT: xml='.Length)
    $xml | Set-Content -Encoding utf8 "$output\ui.xml"
    return [xml]$xml
}
function Bounds($Node) { return @([regex]::Matches($Node.bounds,'\d+') | ForEach-Object { [int]$_.Value }) }
function Find-Node([string]$Text) {
    $xml=Snapshot
    return $xml.SelectNodes('//node') | Where-Object { $_.text -eq $Text } | Select-Object -First 1
}
function Click-Text([string]$Text) {
    for ($i=0;$i -lt 4;$i++) {
        $n=Find-Node $Text
        if ($n) { $b=Bounds $n; ADB @('shell','input','tap',[string][int](($b[0]+$b[2])/2),[string][int](($b[1]+$b[3])/2)) | Out-Null; return }
        ADB @('shell','input','swipe','160','570','160','260','250') | Out-Null
    }
    throw "UI text missing: $Text"
}
function Main { ADB @('shell','am','start','-W','-f','0x14000000','-n','dev.trealink.app/.MainActivity') | Out-Null }
function Pass([string]$Text) { $checks.Add($Text); Write-Output "PASS $Text" }
function Send-Touch([string]$Kind,[int]$X,[int]$Y,[int]$X2=0,[int]$Y2=0) {
    & "$Jdk\bin\java.exe" -cp "$project\build\tests" dev.trealink.app.RemoteTouchProbe 127.0.0.1 $script:port $script:password $Kind $X $Y $X2 $Y2
    if ($LASTEXITCODE -ne 0) { throw 'Pointer probe failed.' }
}
function Remote-Text([string]$Text,[string]$Kind) {
    $n=Find-Node $Text
    if (!$n) { throw "Remote target missing: $Text" }
    $b=Bounds $n
    Send-Touch $Kind ([int](($b[0]+$b[2])/2)) ([int](($b[1]+$b[3])/2))
}
$oldServices=(ADB @('shell','settings','get','secure','enabled_accessibility_services') | Out-String).Trim()
$oldAccessibility=(ADB @('shell','settings','get','secure','accessibility_enabled') | Out-String).Trim()
$script:port=$null
$complete=$false
try {
    & "$PSScriptRoot\test.ps1" -Jdk $Jdk
    & "$PSScriptRoot\build-ui-probe.ps1" -Sdk $Sdk -Jdk $Jdk
    ADB @('install','-r','-t',"$project\build\ui-probe\UiProbe.apk") | Out-Null
    ADB @('install','-r',"$project\build\Trealink-debug.apk") | Out-Null
    ADB @('shell','input','keyevent','KEYCODE_WAKEUP') | Out-Null
    ADB @('shell','input','keyevent','KEYCODE_MENU') | Out-Null
    Main
    Click-Text 'Menu'; Click-Text 'Output mode'; Click-Text 'Phone screen mirroring'
    Click-Text 'USB (ADB)'
    Click-Text 'Menu'; Click-Text 'Touch control'; Click-Text 'Enable direct touch'
    Main
    # Only this disposable emulator is modified; original Accessibility settings are restored below.
    $service='dev.trealink.app/dev.trealink.app.RemoteTouchService'
    $services=if (!$oldServices -or $oldServices -eq 'null') { $service } elseif ($oldServices -like "*$service*") { $oldServices } else { "$($oldServices):$service" }
    ADB @('shell','settings','put','secure','enabled_accessibility_services',$services) | Out-Null
    ADB @('shell','settings','put','secure','accessibility_enabled','1') | Out-Null
    $bound=$false
    for ($attempt=0;$attempt -lt 20;$attempt++) {
        $accessibility=ADB @('shell','dumpsys','accessibility') | Out-String
        if ($accessibility -match 'Bound services:\{Service\[label=Trealink Touch Control') { $bound=$true; break }
        Start-Sleep -Milliseconds 250
    }
    if (!$bound) { $accessibility | Set-Content "$output\binding-failure.txt"; throw 'Accessibility service failed to bind in the disposable emulator.' }
    Click-Text 'Start mirroring'
    $xml=Snapshot
    $share=$xml.SelectNodes('//node') | Where-Object { $_.text -in @('Share screen','Start now') } | Select-Object -First 1
    if (!$share) { throw 'Expected full-screen sharing confirmation.' }
    $b=Bounds $share; ADB @('shell','input','tap',[string][int](($b[0]+$b[2])/2),[string][int](($b[1]+$b[3])/2)) | Out-Null
    $xml=Snapshot
    $text=($xml.SelectNodes('//node') | Where-Object { $_.text -like 'Session password:*' } | Select-Object -First 1).text
    if ($text -notmatch 'Direct touch ready') { throw "Touch service not ready: $text" }
    $script:password=[regex]::Match($text,'Session password: ([A-Z2-9]{8})').Groups[1].Value
    $script:port=[int]((ADB @('forward','tcp:0','tcp:5900') | Out-String).Trim())
    Pass 'explicit touch opt-in and full-screen capture activate the Accessibility bridge'
    Click-Text 'Menu'; Click-Text 'Touch test'
    Remote-Text 'Tap count: 0' 'hover'
    if (!(Find-Node 'Tap count: 0')) { throw 'Hover must not click.' }
    Pass 'hover causes no phone action'
    Remote-Text 'Tap count: 0' 'tap'
    if (!(Find-Node 'Tap count: 1')) { throw 'Remote direct tap did not click the Android button.' }
    Pass 'viewer coordinate becomes an actual Android button tap'
    Remote-Text 'Hold count: 0' 'hold'
    if (!(Find-Node 'Hold count: 1')) { throw 'Remote hold did not generate a native long press.' }
    Pass 'press and hold produces a native Android long click'
    $xml=Snapshot; $slider=$xml.SelectNodes('//node') | Where-Object { $_.'content-desc' -eq 'Drag test' } | Select-Object -First 1
    $b=Bounds $slider; $middle=[int](($b[1]+$b[3])/2)
    Send-Touch 'swipe' ($b[0]+25) $middle ($b[2]-25) $middle
    $xml=Snapshot; $value=($xml.SelectNodes('//node') | Where-Object { $_.text -match '^Slider: (\d+)$' } | Select-Object -First 1).text
    if ([int]([regex]::Match($value,'\d+').Value) -lt 70) { throw "Drag failed: $value" }
    Pass 'continuous drag updates a native SeekBar'
    $items=@($xml.SelectNodes('//node') | Where-Object { $_.text -match '^Scroll item \d+$' } | ForEach-Object { [int]([regex]::Match($_.text,'\d+').Value) })
    $before=($items | Measure-Object -Maximum).Maximum
    $scroll=$xml.SelectNodes('//node') | Where-Object { $_.class -eq 'android.widget.ScrollView' } | Select-Object -First 1
    $b=Bounds $scroll; $x=[int](($b[0]+$b[2])/2)
    Send-Touch 'swipe' $x ($b[3]-40) $x ($b[1]+30)
    $xml=Snapshot; $after=($xml.SelectNodes('//node') | Where-Object { $_.text -match '^Scroll item \d+$' } | ForEach-Object { [int]([regex]::Match($_.text,'\d+').Value) } | Measure-Object -Maximum).Maximum
    if ($after -le $before) { throw 'Remote swipe did not scroll native content.' }
    Pass 'viewer swipe scrolls Android content'
    Remote-Text 'Tap count: 1' 'down'
    if (!(Find-Node 'Finger: up')) { throw 'Viewer disconnect left a held finger.' }
    Pass 'viewer disconnect releases a held touch'
    $target=Find-Node 'Tap count: 2'
    if (!$target) { $target=Find-Node 'Tap count: 1' }
    if (!$target) { throw 'Missing button for outside-release test.' }
    $b=Bounds $target
    $launch=[Diagnostics.ProcessStartInfo]::new("$Jdk\bin\java.exe")
    $launch.UseShellExecute=$false; $launch.CreateNoWindow=$true
    $launch.RedirectStandardOutput=$true; $launch.RedirectStandardError=$true
    foreach ($argument in @('-cp',"$project\build\tests",'dev.trealink.app.RemoteTouchProbe','127.0.0.1',"$script:port",$script:password,'outside',"$([int](($b[0]+$b[2])/2))","$([int](($b[1]+$b[3])/2))")) { $launch.ArgumentList.Add($argument) }
    $outside=[Diagnostics.Process]::Start($launch)
    try {
        if ($outside.StandardOutput.ReadLine() -ne 'OUTSIDE_RELEASE_SENT') { throw 'Outside-release probe failed to send input.' }
        if (!(Find-Node 'Finger: up') -or $outside.HasExited) { throw 'A release outside the image left a finger down while the viewer stayed connected.' }
        Pass 'release outside the image lifts the Android finger before viewer disconnect'
        if (!$outside.WaitForExit(12000) -or $outside.ExitCode -ne 0) { throw 'Outside-release probe did not finish cleanly.' }
    } finally { if (!$outside.HasExited) { $outside.Kill() }; $outside.Dispose() }
    ADB @('shell','am','start','-W','-f','0x14000000','-a','android.settings.SETTINGS') | Out-Null
    Remote-Text 'Connected devices' 'tap'
    $xml=Snapshot
    if (!($xml.SelectNodes('//node') | Where-Object { $_.text -in @('Pair new device','Connection preferences') })) { throw 'Cross-app touch did not navigate Android Settings.' }
    Pass 'direct touch operates a different phone app without changing its settings'
    Main; Click-Text 'Menu'; Click-Text 'Touch control'; Click-Text 'Turn off'
    Click-Text 'Menu'; Click-Text 'Touch test'
    Remote-Text 'Tap count: 0' 'tap'
    if (!(Find-Node 'Tap count: 0')) { throw 'Touch control off did not block input.' }
    Pass 'local off switch immediately blocks remote gestures'
    Main; Click-Text 'Stop mirroring'
    if ((ADB @('shell','ss','-ltn') | Out-String) -match ':5900\b') { throw 'Listener remained after stop.' }
    Pass 'stop removes the remote input session and listener'
    @{result='PASS';date=(Get-Date).ToString('yyyy-MM-dd');checks=@($checks);physicalPhoneTested=$false;carTested=$false} |
        ConvertTo-Json -Depth 4 | Set-Content -Encoding utf8 "$output\results.json"
    $complete=$true
    Write-Output "RESULT $($checks.Count) Android touch checks passed"
} finally {
    if ($script:port) { ADB @('forward','--remove',"tcp:$($script:port)") | Out-Null }
    if (!$complete) { ADB @('shell','am','force-stop','dev.trealink.app') | Out-Null }
    if (!$oldServices -or $oldServices -eq 'null') { ADB @('shell','settings','delete','secure','enabled_accessibility_services') | Out-Null }
    else { ADB @('shell','settings','put','secure','enabled_accessibility_services',$oldServices) | Out-Null }
    if (!$oldAccessibility -or $oldAccessibility -eq 'null') { ADB @('shell','settings','delete','secure','accessibility_enabled') | Out-Null }
    else { ADB @('shell','settings','put','secure','accessibility_enabled',$oldAccessibility) | Out-Null }
}
