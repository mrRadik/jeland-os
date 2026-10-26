#requires -version 5.1
param()
$ErrorActionPreference='Stop'
. (Join-Path $PSScriptRoot 'install.ps1') -LibraryOnly
$fixture=Join-Path ([IO.Path]::GetTempPath()) ('JelandSetupTest_'+[guid]::NewGuid().ToString('N'))
[void](New-Item -ItemType Directory -Path $fixture)
$required=@('tools/adb.exe','tools/AdbWinApi.dll','tools/AdbWinUsbApi.dll','install-helper/helper.dex',
    'engine/AdbTools.ps1','engine/Install-APK.ps1','packages/JelandOS-0.9.38.apk','packages/ruvoice.apk')
foreach($name in $required){$file=Join-Path $fixture $name;[void](New-Item -ItemType Directory -Force -Path (Split-Path -Parent $file));[IO.File]::WriteAllText($file,'fixture')}
$manifest=@{schema=1;os=@{package='ru.jeland.os';file='JelandOS-0.9.38.apk';versionCode=93;versionName='0.9.38'};
    voice=@{package='ru.kost.ruvoice';file='ruvoice.apk';versionCode=10101;versionName='1.1.1'};
    files=@($required | ForEach-Object {$file=Join-Path $fixture $_;@{path=$_;bytes=(Get-Item $file).Length;sha256=(Get-FileHash $file -Algorithm SHA256).Hash}})}
$manifest | ConvertTo-Json -Depth 6 | Set-Content (Join-Path $fixture 'bundle.json') -Encoding UTF8
function Reset-Fake {$script:calls=@();$script:installed=@();$script:sdk='30';$script:user='0';$script:installExit=0;$script:denied=$false;$script:missingGrant=$false}
function Invoke-JelandAdb {
    param([string]$Root,[string]$Serial,[string[]]$Arguments,[int]$TimeoutSeconds=30)
    $command=$Arguments -join ' ';$script:calls+=$command
    if($Serial -and $Serial -ne 'FAKE-HU'){throw 'Unexpected device'}
    $text=switch -Regex ($command) {
        '^devices -l$' {"List of devices attached`nFAKE-HU device model:QA";break}
        '^shell am get-current-user$' {$script:user;break}
        '^shell getprop ro.build.version.sdk$' {$script:sdk;break}
        '^shell getprop ro.product.model$' {'QA-EMU';break}
        '^shell dumpsys package (ru.jeland.os|ru.kost.ruvoice)$' {
            $code=if($Matches[1] -eq 'ru.jeland.os'){93}else{10101}
            $permissions=@('ACCESS_COARSE_LOCATION','ACCESS_FINE_LOCATION','READ_PHONE_STATE','BLUETOOTH_CONNECT','POST_NOTIFICATIONS')
            $out="versionCode=$code`n"
            foreach($p in $permissions){$out+="android.permission.${p}: granted=$((-not $script:missingGrant).ToString().ToLowerInvariant())`n"};$out;break
        }
        '^shell cmd appops set --user 0 ru.jeland.os (SYSTEM_ALERT_WINDOW|REQUEST_INSTALL_PACKAGES) allow$' {
            if($script:denied){'SecurityException: denied'}else{''};break
        }
        '^shell cmd appops get --user 0 ru.jeland.os (SYSTEM_ALERT_WINDOW|REQUEST_INSTALL_PACKAGES)$' {$Matches[1]+': allow';break}
        '^shell pm grant --user 0 ru.jeland.os android.permission.(ACCESS_COARSE_LOCATION|ACCESS_FINE_LOCATION|READ_PHONE_STATE|BLUETOOTH_CONNECT|POST_NOTIFICATIONS)$' {'';break}
        '^shell pm grant --user 0 ru.kost.ruvoice android.permission.POST_NOTIFICATIONS$' {'';break}
        '^shell am start --user 0 -n ru.jeland.os/.MainActivity$' {'Starting';break}
        default {throw ('Unexpected or out-of-scope ADB command: '+$command)}
    }
    [pscustomobject]@{Code=0;TimedOut=$false;Text=$text}
}
function Install-JelandBundlePackage([string]$Root,[string]$Serial,$Package){$script:installed+=$Package.package;return $script:installExit}
function Check($ok,$message){if(-not $ok){throw $message}}
function Run-TestInstall {Invoke-JelandBundleInstall -Root $fixture -Serial FAKE-HU -Yes 6>$null}
$cases=@(
    {$result=Run-TestInstall;Check ($result -eq 0) 'API30 failed';Check ($script:installed.Count -eq 2) 'Wrong packages';Check (-not ($script:calls -match 'pm grant.*(BLUETOOTH_CONNECT|POST_NOTIFICATIONS)')) 'Unsupported API30 permission'},
    {$script:sdk='35';$result=Run-TestInstall;Check ($result -eq 0) 'API35 failed';Check (@($script:calls -match 'pm grant.*POST_NOTIFICATIONS').Count -eq 2) 'Missing per-app notification grants'},
    {$script:installExit=3;$result=Run-TestInstall;Check ($result -eq 3 -and $script:installed.Count -eq 1) 'Unknown install retried';Check (-not ($script:calls -match 'appops set|pm grant')) 'Rights after unknown result'},
    {$script:installExit=1;$result=Run-TestInstall;Check ($result -eq 1 -and $script:installed.Count -eq 1) 'Failed install continued'},
    {$script:user='10';$result=Run-TestInstall;Check ($result -eq 1 -and $script:installed.Count -eq 0) 'Wrong Android user'},
    {$script:sdk='29';$result=Run-TestInstall;Check ($result -eq 1 -and $script:installed.Count -eq 0) 'Unsupported Android'},
    {$script:denied=$true;$result=Run-TestInstall;Check ($result -eq 1) 'SecurityException ignored';Check (-not ($script:calls -match 'pm grant|am start')) 'Continued after denial'},
    {$script:missingGrant=$true;$result=Run-TestInstall;Check ($result -eq 1) 'Unverified grant accepted';Check (-not ($script:calls -match 'am start')) 'Opened OS after unverified rights'},
    {$file=Join-Path $fixture 'packages/ruvoice.apk';[IO.File]::WriteAllText($file,'corrupted');$caught=$false;try{Run-TestInstall}catch{$caught=$true};[IO.File]::WriteAllText($file,'fixture');Check ($caught -and $script:calls.Count -eq 0) 'ADB executed before hash validation'}
)
try {
    foreach($case in $cases){Reset-Fake;& $case}
    Write-Output ('PASS: '+$cases.Count+' installer scenarios; no real ADB/device actions')
}finally {
    $resolved=(Resolve-Path -LiteralPath $fixture).Path
    $tempRoot=(Resolve-Path -LiteralPath ([IO.Path]::GetTempPath())).Path.TrimEnd('\')
    if(-not $resolved.StartsWith($tempRoot+'\') -or (Split-Path -Leaf $resolved) -notmatch '^JelandSetupTest_[a-f0-9]{32}$'){throw 'Unsafe fixture cleanup path'}
    Remove-Item -LiteralPath $resolved -Recurse
}
