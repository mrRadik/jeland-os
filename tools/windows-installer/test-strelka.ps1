$ErrorActionPreference='Stop'
. (Join-Path $PSScriptRoot 'setup-strelka.ps1') -LibraryOnly
if(-not (Test-StrelkaListener 'other/service:ru.jeland.os/ru.jeland.os.data.roadalerts.StrelkaNotificationListener')){throw 'Exact component missing'}
foreach($bad in @('null','ru.jeland.os.evil/ru.jeland.os.data.roadalerts.StrelkaNotificationListener','ru.jeland.os/.data.Other','ru.jeland.os/ru.jeland.os.data.roadalerts.StrelkaNotificationListenerEvil')) {
    if(Test-StrelkaListener $bad){throw 'Matched another listener'}
}
$script:commands=@();$script:allowed=$false
function Select-JelandDevice {param($Root,$Serial);return 'test-device'}
function Invoke-JelandAdb {
    param($Root,$Serial,[string[]]$Arguments)
    $script:commands+=($Arguments -join ' ')
    $text=switch($Arguments -join ' ') {
        'shell am get-current-user' {'0'}
        'shell dumpsys package ru.jeland.os' {'51462ca ru.jeland.os/.data.roadalerts.StrelkaNotificationListener filter 203cc3b permission android.permission.BIND_NOTIFICATION_LISTENER_SERVICE'}
        'shell cmd notification allow_listener ru.jeland.os/ru.jeland.os.data.roadalerts.StrelkaNotificationListener 0' {$script:allowed=$true;''}
        'shell cmd notification disallow_listener ru.jeland.os/ru.jeland.os.data.roadalerts.StrelkaNotificationListener 0' {$script:allowed=$false;''}
        'shell settings --user 0 get secure enabled_notification_listeners' {if($script:allowed){'other/service:ru.jeland.os/ru.jeland.os.data.roadalerts.StrelkaNotificationListener'}else{'other/service'}}
        default {throw ('Unexpected ADB mutation: '+($Arguments -join ' '))}
    }
    return [pscustomobject]@{Code=0;TimedOut=$false;Text=$text}
}
Invoke-StrelkaSetup -Root '.' -Mode enable -Yes $true
if(-not $script:allowed){throw 'Not enabled'}
Invoke-StrelkaSetup -Root '.' -Mode status -Yes $true
Invoke-StrelkaSetup -Root '.' -Mode disable -Yes $true
if($script:allowed){throw 'Not disabled'}
if(@($script:commands | Where-Object {$_ -match 'settings.*put|pm grant|appops'}).Count){throw 'Unrelated permission write'}
Write-Host 'OK: component isolation, enable/status/revoke, no changes to other listeners'
