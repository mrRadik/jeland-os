#requires -version 5.1
# Fixed Jeland OS service only. No device actions on import.
function Invoke-JelandInstallAssist {
    param([string]$Root,[string]$Serial,[ValidateSet('status','allow','revoke')][string]$Mode='status',
        [scriptblock]$Report={})
    $component='ru.jeland.os/ru.jeland.os.data.updates.UpdateInstallAssistService'
    $short='ru.jeland.os/.data.updates.UpdateInstallAssistService'
    $call={param([string]$name,[string[]]$arguments)
        $r=Invoke-JelandAdb -Root $Root -Serial $Serial -Arguments $arguments -TimeoutSeconds 15
        $null=& $Report $name $r
        if($r.Code -ne 0 -or $r.TimedOut -or $r.Text -match '(?im)^\s*(Error:|Exception|Security exception|Permission Denial|Permission denied|Unknown command)'){
            throw ('Не выполнено: '+$name+'. '+$r.Text)
        }
        return $r.Text.Trim()
    }
    $parse={param([string]$raw)
        if(-not $raw -or $raw -eq 'null'){return}
        if($raw.Length -gt 8192){throw 'Список служб слишком длинный; изменения отменены.'}
        foreach($part in ($raw -split ':')) {
            if($part -notmatch '^[A-Za-z][A-Za-z0-9_.]*/\.?[A-Za-z][A-Za-z0-9_.]*$'){throw 'Неизвестный формат списка служб; изменения отменены.'}
            $part
        }
    }
    $user=& $call 'user' @('shell','am','get-current-user')
    if($user -ne '0'){throw 'Поддерживается только основной пользователь Android 0.'}
    if($Mode -ne 'revoke') {
        $package=& $call 'os-package' @('shell','dumpsys','package','ru.jeland.os')
        if($package -notmatch 'UpdateInstallAssistService'){throw 'Установите APK Jeland OS со службой помощи из этого комплекта.'}
    }
    $oldRaw=& $call 'services-before' @('shell','settings','--user','0','get','secure','enabled_accessibility_services')
    $oldGlobal=& $call 'enabled-before' @('shell','settings','--user','0','get','secure','accessibility_enabled')
    if($oldGlobal -notin @('0','1','null')){throw 'Неизвестное состояние специальных возможностей; изменения отменены.'}
    $before=@(& $parse $oldRaw)
    $others=@($before | Where-Object {$_ -cne $component -and $_ -cne $short})
    $present=$others.Count -ne $before.Count
    if($Mode -eq 'status'){return [pscustomobject]@{Allowed=($present -and $oldGlobal -eq '1');Changed=$false}}
    if($Mode -eq 'allow' -and $present -and $oldGlobal -eq '1'){return [pscustomobject]@{Allowed=$true;Changed=$false}}
    if($Mode -eq 'revoke' -and -not $present){return [pscustomobject]@{Allowed=$false;Changed=$false}}
    if($Mode -eq 'allow' -and $oldGlobal -ne '1' -and $others.Count -gt 0){
        throw 'Другие службы настроены, но выключены общим переключателем. Автовключение отменено, чтобы не активировать их вместе с Jeland OS.'
    }
    $next=@($others);if($Mode -eq 'allow'){$next+=@($component)}
    if($next.Count -gt 0){$null=& $call 'services-set' @('shell','settings','--user','0','put','secure','enabled_accessibility_services',($next -join ':'))}
    else {$null=& $call 'services-set' @('shell','settings','--user','0','delete','secure','enabled_accessibility_services')}
    if($Mode -eq 'allow'){$null=& $call 'enabled-set' @('shell','settings','--user','0','put','secure','accessibility_enabled','1')}
    elseif($next.Count -eq 0){$null=& $call 'enabled-set' @('shell','settings','--user','0','put','secure','accessibility_enabled','0')}
    $actualRaw=& $call 'services-after' @('shell','settings','--user','0','get','secure','enabled_accessibility_services')
    $actualGlobal=& $call 'enabled-after' @('shell','settings','--user','0','get','secure','accessibility_enabled')
    $actual=@(& $parse $actualRaw)
    $actualOthers=@($actual | Where-Object {$_ -cne $component -and $_ -cne $short})
    foreach($other in $others){if($actualOthers -cnotcontains $other){throw 'Проверка не пройдена: изменился список других служб. Смотрите журнал.'}}
    $isPresent=@($actual | Where-Object {$_ -ceq $component -or $_ -ceq $short}).Count -gt 0
    if($Mode -eq 'allow' -and (-not $isPresent -or $actualGlobal -ne '1')){throw 'Android не подтвердил включение службы. Смотрите журнал.'}
    if($Mode -eq 'revoke' -and $isPresent){throw 'Android не подтвердил отключение службы. Смотрите журнал.'}
    return [pscustomobject]@{Allowed=($isPresent -and $actualGlobal -eq '1');Changed=$true}
}
