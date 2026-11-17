#requires -version 5.1
param()
$ErrorActionPreference='Stop'
. (Join-Path $PSScriptRoot 'install-strelka.ps1') -LibraryOnly
$fixture=Join-Path $env:TEMP ('StrelkaInstallerTest_'+[guid]::NewGuid().ToString('N'))
[void](New-Item -ItemType Directory -Force -Path $fixture)
function Reset-Fake {
    $script:calls=@();$script:installCount=0;$script:installCode=0;$script:sdk='30';$script:user='0'
    $script:package='com.ivolk.StrelkaGPS';$script:denied=$false;$script:missingGrant=$false;$script:version='25011'
    $script:opMissing=$false;$script:connected=$false;$script:battery=$false;$script:batteryMissing=$false
}
function Get-StrelkaApk {param($Root,$ApkPath);[pscustomobject]@{Path='C:\fixture\strelka.apk';Package=$script:package;VersionCode=25011;VersionName='25.01.1s';Sha256='fixture'}}
function Select-JelandDevice {param($Root,$Serial);return 'FAKE-HU'}
function Install-StrelkaApk {param($Root,$Serial,$Path);$script:installCount++;return $script:installCode}
function Invoke-StrelkaSetup {param($Root,$Serial,$Mode,$Yes);$script:connected=$true}
function Invoke-JelandAdb {
    param($Root,$Serial,[string[]]$Arguments)
    $command=$Arguments -join ' ';$script:calls+=$command
    $text=switch -Regex($command) {
        '^shell am get-current-user$' {$script:user;break}
        '^shell getprop ro.build.version.sdk$' {$script:sdk;break}
        '^shell dumpsys package com.ivolk.StrelkaGPS$' {
            "versionCode=$script:version minSdk=14 targetSdk=32"
            foreach($p in @('ACCESS_COARSE_LOCATION','ACCESS_FINE_LOCATION','ACCESS_BACKGROUND_LOCATION','READ_PHONE_STATE','BLUETOOTH_CONNECT','POST_NOTIFICATIONS','SYSTEM_ALERT_WINDOW','REQUEST_INSTALL_PACKAGES')) {
                "android.permission.$p"
                "android.permission.${p}: granted=$((-not $script:missingGrant).ToString().ToLowerInvariant())"
            };break
        }
        '^shell pm grant --user 0 com.ivolk.StrelkaGPS android.permission.(ACCESS_COARSE_LOCATION|ACCESS_FINE_LOCATION|ACCESS_BACKGROUND_LOCATION|READ_PHONE_STATE|BLUETOOTH_CONNECT|POST_NOTIFICATIONS)$' {
            if($script:denied){'SecurityException: permission denied'}else{''};break
        }
        '^shell cmd appops set --user 0 com.ivolk.StrelkaGPS (POST_NOTIFICATION|RUN_IN_BACKGROUND|RUN_ANY_IN_BACKGROUND|SYSTEM_ALERT_WINDOW|REQUEST_INSTALL_PACKAGES) allow$' {'';break}
        '^shell cmd appops get --user 0 com.ivolk.StrelkaGPS (POST_NOTIFICATION|RUN_IN_BACKGROUND|RUN_ANY_IN_BACKGROUND|SYSTEM_ALERT_WINDOW|REQUEST_INSTALL_PACKAGES)$' {$Matches[1]+': '+$(if($script:opMissing){'default'}else{'allow'});break}
        '^shell dumpsys deviceidle whitelist \+com.ivolk.StrelkaGPS$' {$script:battery=$true;'';break}
        '^shell dumpsys deviceidle whitelist$' {"user,other.app,10100";if($script:battery -and -not $script:batteryMissing){'user,com.ivolk.StrelkaGPS,10143'};break}
        '^shell am start --user 0 -a android.intent.action.VIEW -n com.ivolk.StrelkaGPS/.MainActivity --ei scut_main 1$' {'Starting';break}
        default {throw ('Unexpected ADB action: '+$command)}
    }
    [pscustomobject]@{Code=0;TimedOut=$false;Text=($text -join "`n")}
}
function Check($Condition,[string]$Message){if(-not $Condition){throw $Message}}
function Run-TestInstall {Invoke-StrelkaInstall -Root $fixture -Serial FAKE-HU}
$cases=@(
    {$result=Run-TestInstall;Check ($result -eq 0 -and $script:connected) 'API30 failed';Check (-not ($script:calls -match 'pm grant.*BLUETOOTH_CONNECT|pm grant.*POST_NOTIFICATIONS')) 'Unsupported runtime permissions on API30'},
    {$script:sdk='35';$result=Run-TestInstall;Check ($result -eq 0) 'API35 failed';Check (@($script:calls -match 'pm grant.*BLUETOOTH_CONNECT|pm grant.*POST_NOTIFICATIONS').Count -eq 2) 'Missing API35 grants'},
    {$script:package='com.ivolk.estrelka';$result=Run-TestInstall;Check ($result -eq 1 -and $script:calls.Count -eq 0 -and $script:installCount -eq 0) 'Wrong APK installed'},
    {$script:user='10';$result=Run-TestInstall;Check ($result -eq 1 -and $script:installCount -eq 0) 'Wrong Android user'},
    {$script:installCode=3;$result=Run-TestInstall;Check ($result -eq 3 -and $script:installCount -eq 1) 'Unknown install retried';Check (-not ($script:calls -match 'pm grant|appops set|whitelist \+|am start') -and -not $script:connected) 'Rights after uncertain installation'},
    {$script:installCode=1;$result=Run-TestInstall;Check ($result -eq 1 -and -not $script:connected) 'Failed install continued'},
    {$script:version='25010';$result=Run-TestInstall;Check ($result -eq 1 -and -not ($script:calls -match 'pm grant')) 'Wrong version granted rights'},
    {$script:denied=$true;$result=Run-TestInstall;Check ($result -eq 1 -and -not $script:connected) 'Permission denial ignored'},
    {$script:missingGrant=$true;$result=Run-TestInstall;Check ($result -eq 1 -and -not ($script:calls -match 'appops set')) 'Unverified grant ignored'},
    {$script:opMissing=$true;$result=Run-TestInstall;Check ($result -eq 1 -and -not $script:connected) 'Unverified app-op ignored'},
    {$script:batteryMissing=$true;$result=Run-TestInstall;Check ($result -eq 1 -and -not $script:connected) 'Unverified battery exemption ignored'}
)
foreach($case in $cases){Reset-Fake;& $case}
Write-Host ('PASS: '+$cases.Count+' installer scenarios; only Strelka permissions; no real device writes') -ForegroundColor Green
