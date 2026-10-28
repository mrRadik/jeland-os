#requires -version 5.1
param([string]$PortableRoot='D:/projects/Jeland-ADB-Collector-Portable')
$ErrorActionPreference='Stop'
. (Join-Path $PortableRoot 'Jeland-Tools.ps1')
# The real ADB function is replaced; these checks never connect to a device.
function Invoke-JelandAdb {
    param([string]$Root,[string]$Serial,[string[]]$Arguments)
    if($Serial -ne 'FAKE-HU'){throw 'Wrong selected device'}
    $script:calls+=,@($Arguments)
    $command=$Arguments -join ' '
    $reply=switch -Regex ($command) {
        '^shell am get-current-user$' {$script:fakeUser;break}
        '^shell pm path ru\.jeland\.os$' {if($script:fakeInstalled){'package:/data/app/example/base.apk'}else{''};break}
        '^shell dumpsys package ru\.jeland\.os$' {if($script:fakeManifest){'  android.permission.REQUEST_INSTALL_PACKAGES'}else{'old APK'};break}
        '^shell cmd appops get --user 0 ru\.jeland\.os REQUEST_INSTALL_PACKAGES$' {"REQUEST_INSTALL_PACKAGES: $script:fakePermission";break}
        '^shell cmd appops set --user 0 ru\.jeland\.os REQUEST_INSTALL_PACKAGES (allow|default)$' {
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
function Invoke-Mode([string]$mode){Invoke-JelandOsUpdatePermission -Root $PortableRoot -Serial FAKE-HU -Mode $mode}
function Assert-Rejects([scriptblock]$action){$caught=$false;try{& $action | Out-Null}catch{$caught=$true};Assert-True $caught 'Expected rejection'}
function Write-Count {@($script:calls | Where-Object {($_ -join ' ') -like 'shell cmd appops set *'}).Count}
$cases=@(
    {$state=Invoke-Mode status;Assert-True (-not $state.Allowed) 'Status';Assert-True ((Write-Count) -eq 0) 'Status wrote a setting'},
    {$state=Invoke-Mode allow;Assert-True ($state.Allowed -and $state.Changed) 'Grant not verified';Assert-True ((Write-Count) -eq 1) 'Grant retried'},
    {$script:fakePermission='allow';$state=Invoke-Mode allow;Assert-True ($state.Allowed -and -not $state.Changed) 'Already granted';Assert-True ((Write-Count) -eq 0) 'Unneeded write'},
    {$script:fakeDenied=$true;Assert-Rejects {Invoke-Mode allow};Assert-True ((Write-Count) -eq 1) 'Denied write retried'},
    {$script:fakeStuck=$true;Assert-Rejects {Invoke-Mode allow};Assert-True ((Write-Count) -eq 1) 'Unverified write retried'},
    {$script:fakeUser='10';Assert-Rejects {Invoke-Mode allow};Assert-True ((Write-Count) -eq 0) 'Wrong user changed'},
    {$script:fakeInstalled=$false;Assert-Rejects {Invoke-Mode allow};Assert-True ((Write-Count) -eq 0) 'Absent package changed'},
    {$script:fakeManifest=$false;Assert-Rejects {Invoke-Mode allow};Assert-True ((Write-Count) -eq 0) 'Missing manifest permission changed'},
    {$script:fakePermission='allow';$state=Invoke-Mode default;Assert-True (-not $state.Allowed -and $state.Changed) 'Revoke not verified'}
)
$passed=0
foreach($case in $cases){Reset-Fake;& $case;$passed++}
Write-Output "PASS: $passed update permission scenarios; mocked ADB only"
