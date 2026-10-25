#requires -version 5.1
param([string]$PortableRoot='D:/projects/Jeland-ADB-Collector-Portable')
$ErrorActionPreference='Stop'
. (Join-Path $PortableRoot 'Jeland-Tools.ps1')
# All ADB calls are substituted. No device, permissions or processes are touched.
function Invoke-JelandAdb {
    param([string]$Root,[string]$Serial,[string[]]$Arguments)
    $script:calls+=,@($Arguments)
    if($Serial -ne 'FAKE-HU'){throw 'Wrong selected device'}
    $command=$Arguments -join ' '
    $reply=switch -Regex ($command) {
        '^shell am get-current-user$' {$script:fakeUser;break}
        '^shell pm path ru\.jeland\.os$' {if($script:fakeInstalled){'package:/data/app/example/base.apk'}else{''};break}
        '^shell dumpsys package ru\.jeland\.os$' {if($script:fakeManifest){"  android.permission.SYSTEM_ALERT_WINDOW`n  android.permission.RECEIVE_BOOT_COMPLETED"}else{'old version'};break}
        '^shell cmd appops get --user 0 ru\.jeland\.os SYSTEM_ALERT_WINDOW$' {"SYSTEM_ALERT_WINDOW: $script:fakePermission";break}
        '^shell cmd appops set --user 0 ru\.jeland\.os SYSTEM_ALERT_WINDOW (allow|default)$' {
            if($script:fakeDenied){return [pscustomobject]@{Code=0;TimedOut=$false;Text='SecurityException: denied'}}
            if(-not $script:fakeStuck){$script:fakePermission=$Matches[1]};'';break
        }
        default {throw "Unexpected or out-of-scope ADB call: $command"}
    }
    [pscustomobject]@{Code=0;TimedOut=$false;Text=[string]$reply}
}
function Reset-Fake {
    $script:calls=@();$script:fakeUser='0';$script:fakeInstalled=$true;$script:fakeManifest=$true
    $script:fakePermission='default';$script:fakeDenied=$false;$script:fakeStuck=$false
}
function Assert-True($value,[string]$message){if(-not $value){throw $message}}
function Invoke-Mode([string]$mode){Invoke-JelandOsStartupPermission -Root $PortableRoot -Serial FAKE-HU -Mode $mode}
function Assert-Rejects([scriptblock]$action){$caught=$false;try{& $action | Out-Null}catch{$caught=$true};Assert-True $caught 'Expected rejection'}
function Write-Count {@($script:calls | Where-Object {($_ -join ' ') -like 'shell cmd appops set *'}).Count}
$cases=@(
    { $state=Invoke-Mode status;Assert-True (-not $state.Allowed) 'Missing permission';Assert-True ((Write-Count) -eq 0) 'Status wrote a permission' },
    { $state=Invoke-Mode allow;Assert-True ($state.Allowed -and $state.Changed) 'Grant was not verified';Assert-True ((Write-Count) -eq 1) 'Grant retried' },
    { $script:fakePermission='allow';$state=Invoke-Mode allow;Assert-True ($state.Allowed -and -not $state.Changed) 'Already granted';Assert-True ((Write-Count) -eq 0) 'Already allowed was written again' },
    { $script:fakeDenied=$true;Assert-Rejects {Invoke-Mode allow};Assert-True ((Write-Count) -eq 1) 'Denied command retried' },
    { $script:fakeStuck=$true;Assert-Rejects {Invoke-Mode allow};Assert-True ((Write-Count) -eq 1) 'Unverified grant retried' },
    { $script:fakeUser='10';Assert-Rejects {Invoke-Mode allow};Assert-True ((Write-Count) -eq 0) 'Wrong user was changed' },
    { $script:fakeInstalled=$false;Assert-Rejects {Invoke-Mode allow};Assert-True ((Write-Count) -eq 0) 'Missing package was changed' },
    { $script:fakeManifest=$false;Assert-Rejects {Invoke-Mode allow};Assert-True ((Write-Count) -eq 0) 'Old APK was changed' },
    { $script:fakePermission='allow';$state=Invoke-Mode default;Assert-True (-not $state.Allowed -and $state.Changed) 'Revoke failed';Assert-True ((Write-Count) -eq 1) 'Revoke retried' }
)
$passed=0
foreach($case in $cases){Reset-Fake;& $case;$passed++}
Write-Output "PASS: $passed startup permission scenarios; mocked ADB only"
