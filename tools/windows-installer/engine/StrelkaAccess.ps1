function Test-StrelkaListener([string]$Text) {
    $component='ru.jeland.os/ru.jeland.os.data.roadalerts.StrelkaNotificationListener'
    foreach($item in $Text.Trim() -split ':') {
        if($item -ceq $component -or $item -ceq 'ru.jeland.os/.data.roadalerts.StrelkaNotificationListener'){return $true}
    }
    return $false
}
function Invoke-StrelkaSetup([string]$Root,[string]$Serial,[string]$Mode,[bool]$Yes) {
    $Serial=Select-JelandDevice -Root $Root -Serial $Serial
    $invoke={param([string[]]$Arguments)
        $result=Invoke-JelandAdb -Root $Root -Serial $Serial -Arguments $Arguments
        if($result.Code -ne 0 -or $result.TimedOut){throw ('ADB: '+$result.Text)}
        return $result.Text
    }
    $user=& $invoke -Arguments @('shell','am','get-current-user')
    if($user.Trim() -ne '0'){throw 'Поддерживается только основной пользователь Android 0.'}
    $package=& $invoke -Arguments @('shell','dumpsys','package','ru.jeland.os')
    if($package -notmatch 'ru\.jeland\.os/(?:ru\.jeland\.os)?\.data\.roadalerts\.StrelkaNotificationListener(?:\s|$)'){throw 'Установите Jeland OS 125 или новее с поддержкой Стрелки.'}
    if($Mode -ne 'status') {
        if(-not $Yes) {
            if($Mode -eq 'enable') {
                Write-Host 'Будет разрешён доступ Jeland OS к уведомлениям Android.' -ForegroundColor Yellow
                Write-Host 'В Android это общее разрешение. Jeland OS обрабатывает только предупреждения Стрелки.'
                Write-Host 'Тексты остальных уведомлений не читаются и не сохраняются. Другие службы не изменяются.'
            }else {Write-Host 'Будет отключён только доступ Jeland OS к уведомлениям Стрелки.'}
            if((Read-Host 'Продолжить? Y / N') -notmatch '^[YyДд]$'){Write-Host 'Отменено.';return}
        }
        $command=if($Mode -eq 'enable'){'allow_listener'}else{'disallow_listener'}
        $null=& $invoke -Arguments @('shell','cmd','notification',$command,'ru.jeland.os/ru.jeland.os.data.roadalerts.StrelkaNotificationListener','0')
    }
    $settings=& $invoke -Arguments @('shell','settings','--user','0','get','secure','enabled_notification_listeners')
    $enabled=Test-StrelkaListener $settings
    if($Mode -eq 'enable' -and -not $enabled){throw 'Android не подтвердил разрешение. Другие настройки не менялись.'}
    if($Mode -eq 'disable' -and $enabled){throw 'Android не подтвердил отключение.'}
    Write-Host ('Доступ Jeland OS к предупреждениям: '+$(if($enabled){'включён'}else{'выключен'})) -ForegroundColor Green
    if($enabled){Write-Host 'В Jeland OS: Настройки -> Камеры и дорожные опасности.'}
}
