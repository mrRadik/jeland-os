#requires -version 5.1
param([string]$PortableRoot='D:/projects/Jeland-ADB-Collector-Portable')
$ErrorActionPreference='Stop'
. (Join-Path $PortableRoot 'Jeland-Tools.ps1')
function Invoke-JelandAdb {
    param([string]$Root,[string]$Serial,[string[]]$Arguments)
    if($Serial -ne 'FAKE-HU'){throw 'Unexpected device'}
    $script:calls+=,($Arguments -join ' ')
    $reply=switch -Regex ($Arguments -join ' ') {
        '^shell am get-current-user$' {$script:user;break}
        '^shell content call --user 0 --uri content://ru\.jeland\.os\.maintenance --method apk-cache-info$' {$script:info;break}
        '^shell content call --user 0 --uri content://ru\.jeland\.os\.maintenance --method clear-apk-cache$' {$script:clear;break}
        default {throw 'Out-of-scope command'}
    }
    [pscustomobject]@{Code=0;TimedOut=$false;Text=$reply}
}
function Reset-Fake {
    $script:user='0';$script:calls=@()
    $script:info='Result: Bundle[{status=READY, count=2, bytes=4096}]'
    $script:clear='Result: Bundle[{status=CLEARED, deleted=2, bytes=4096, failed=0}]'
}
function Check($ok,$message){if(-not $ok){throw $message}}
function Rejects([scriptblock]$action){$caught=$false;try{& $action | Out-Null}catch{$caught=$true};Check $caught 'Expected rejection'}
function Run-Mode($mode){Invoke-JelandOsApkCache -Root $PortableRoot -Serial FAKE-HU -Mode $mode}
$cases=@(
    {$r=Run-Mode info;Check ($r.Count -eq 2 -and $r.Bytes -eq 4096) 'Wrong preview';Check (-not ($script:calls -match 'clear-apk-cache')) 'Preview deleted files'},
    {$r=Run-Mode clear;Check ($r.Status -eq 'CLEARED' -and $r.Count -eq 2) 'Clear result'},
    {$script:user='10';Rejects {Run-Mode clear};Check ($script:calls.Count -eq 1) 'Wrong user was changed'},
    {$script:info='No provider found';Rejects {Run-Mode info}},
    {$script:clear='Result: Bundle[{status=BUSY, deleted=0, bytes=0, failed=0}]';Rejects {Run-Mode clear}},
    {$script:clear='Result: Bundle[{status=FAILED, deleted=1, bytes=2048, failed=1}]';Rejects {Run-Mode clear}},
    {$script:clear='Result: Bundle[{status=CLEARED, bytes=2048, failed=0}]';Rejects {Run-Mode clear}},
    {$script:clear='Result: Bundle[{status=READY, deleted=0, bytes=0, failed=0}]';Rejects {Run-Mode clear}}
)
foreach($case in $cases){Reset-Fake;& $case}
Write-Output ('PASS: '+$cases.Count+' APK cleanup menu scenarios; mocked ADB only')
