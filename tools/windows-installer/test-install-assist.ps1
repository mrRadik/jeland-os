#requires -version 5.1
$ErrorActionPreference='Stop'
. (Join-Path $PSScriptRoot 'engine/InstallAssist.ps1')
$component='ru.jeland.os/ru.jeland.os.data.updates.UpdateInstallAssistService'
function Reset-State([string]$Services='null',[string]$Enabled='0') {
    $script:State=@{enabled_accessibility_services=$Services;accessibility_enabled=$Enabled}
    $script:User='0';$script:Package='Service ru.jeland.os/.data.updates.UpdateInstallAssistService'
    $script:Writes=0;$script:Deny=$false
}
function Invoke-JelandAdb {
    param($Root,$Serial,[string[]]$Arguments,$TimeoutSeconds)
    $text='';$code=0
    if(($Arguments -join ' ') -eq 'shell am get-current-user'){$text=$script:User}
    elseif(($Arguments -join ' ') -eq 'shell dumpsys package ru.jeland.os'){$text=$script:Package}
    elseif($Arguments[1] -eq 'settings') {
        $key=$Arguments[6]
        switch($Arguments[4]) {
            'get' {$text=$script:State[$key]}
            'put' {if($script:Deny){$code=1;$text='Permission denied'}else{$script:State[$key]=$Arguments[7];$script:Writes++}}
            'delete' {$script:State[$key]='null';$script:Writes++;$text='Deleted 1 rows'}
            default {throw 'Unexpected settings operation'}
        }
    }else {throw ('Unexpected ADB call '+($Arguments -join ' '))}
    [pscustomobject]@{Code=$code;TimedOut=$false;Text=$text}
}
function Assert-True($Condition,[string]$Message){if(-not $Condition){throw $Message}}
function Must-Refuse {
    $failed=$false
    try {$null=Invoke-JelandInstallAssist -Root '.' -Serial 'mock' -Mode allow}catch {$failed=$true}
    Assert-True $failed 'Operation must be refused'
    Assert-True ($script:Writes -eq 0) 'Refusal must not change settings'
}
Reset-State 'other.app/.Assist' '1'
$r=Invoke-JelandInstallAssist -Root '.' -Serial mock -Mode allow
Assert-True ($r.Allowed -and $script:State.enabled_accessibility_services -ceq ('other.app/.Assist:'+$component)) 'Existing service must survive enable'
$writes=$script:Writes
$r=Invoke-JelandInstallAssist -Root '.' -Serial mock -Mode allow
Assert-True (-not $r.Changed -and $script:Writes -eq $writes) 'Enable must be idempotent'
$r=Invoke-JelandInstallAssist -Root '.' -Serial mock -Mode revoke
Assert-True (-not $r.Allowed -and $script:State.enabled_accessibility_services -ceq 'other.app/.Assist' -and $script:State.accessibility_enabled -eq '1') 'Disable must preserve other service and global state'
Reset-State $component '1'
$r=Invoke-JelandInstallAssist -Root '.' -Serial mock -Mode revoke
Assert-True ($script:State.enabled_accessibility_services -eq 'null' -and $script:State.accessibility_enabled -eq '0') 'Last service removal must clear only own entry'
Reset-State;$script:User='10';Must-Refuse
Reset-State;$script:Package='No requested service';Must-Refuse
Reset-State 'other.app/.Assist' '0';Must-Refuse
Reset-State;$script:Deny=$true;Must-Refuse
Reset-State 'bad value' '1';Must-Refuse
Reset-State 'ru.jeland.os/.data.updates.UpdateInstallAssistService' '1'
$r=Invoke-JelandInstallAssist -Root '.' -Serial mock -Mode status
Assert-True ($r.Allowed -and $script:Writes -eq 0) 'Status must recognize shorthand and stay read-only'
'PASS: 10 installer-service scenarios; no device used.'
