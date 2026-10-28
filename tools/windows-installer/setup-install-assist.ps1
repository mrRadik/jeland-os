#requires -version 5.1
[CmdletBinding()]
param([string]$Serial,[ValidateSet('install','enable','status','disable')][string]$Mode='install',[switch]$Yes)
$ErrorActionPreference='Stop'
$Root=$PSScriptRoot
. (Join-Path $Root 'engine/AdbTools.ps1')
. (Join-Path $Root 'engine/InstallAssist.ps1')
$log=$null
try {
    $manifest=Get-Content -LiteralPath (Join-Path $Root 'assist-bundle.json') -Raw -Encoding UTF8 | ConvertFrom-Json
    if($manifest.schema -ne 1 -or $manifest.os.package -ne 'ru.jeland.os' -or
        $manifest.os.file -notmatch '^JelandOS-[0-9.]+\.apk$' -or [long]$manifest.os.versionCode -le 0){throw 'Некорректное описание комплекта.'}
    foreach($item in $manifest.files) {
        $file=[IO.Path]::GetFullPath((Join-Path $Root $item.path))
        if(-not $file.StartsWith($Root+'\',[StringComparison]::OrdinalIgnoreCase) -or
            -not (Test-Path -LiteralPath $file -PathType Leaf) -or (Get-Item -LiteralPath $file).Length -ne $item.bytes -or
            (Get-FileHash -LiteralPath $file -Algorithm SHA256).Hash -ne $item.sha256){throw ('Повреждён файл комплекта: '+$item.path)}
    }
    $Serial=Select-JelandDevice -Root $Root -Serial $Serial
    $user=Invoke-JelandAdb -Root $Root -Serial $Serial -Arguments @('shell','am','get-current-user')
    if($user.Code -ne 0 -or $user.TimedOut -or $user.Text.Trim() -ne '0'){throw 'Поддерживается только основной пользователь Android 0.'}
    $log=Join-Path $Root ('logs/InstallAssist_'+(Get-Date -Format 'yyyyMMdd_HHmmss_fff'))
    New-Item -ItemType Directory -Path $log -Force | Out-Null
    $save={param($name,$r) @("DEVICE=$Serial","EXIT=$($r.Code)","TIMEOUT=$($r.TimedOut)",$r.Text) |
        Out-File -LiteralPath (Join-Path $log ($name+'.txt')) -Encoding utf8}
    if($Mode -eq 'status'){$s=Invoke-JelandInstallAssist -Root $Root -Serial $Serial -Report $save;Write-Host ('Служба включена: '+$s.Allowed);exit 0}
    Write-Host 'Jeland OS — помощь при нажатии Установить' -ForegroundColor Cyan
    if($Mode -eq 'install'){Write-Host ('Будет установлена Jeland OS '+$manifest.os.versionName+' поверх прежней версии с сохранением данных.')}
    if($Mode -eq 'disable'){Write-Host 'Будет отключена только служба помощи Jeland OS.'}
    else {Write-Host 'Будет включена только служба Jeland OS для нажатия Установить после вашего запуска обновления. Разрешение сохраняется после перезагрузки. Другие службы сохраняются.'}
    if($Mode -ne 'disable'){Write-Host 'Также будет разрешена установка обновлений только для Jeland OS (REQUEST_INSTALL_PACKAGES).'}
    Write-Host 'Меню Android, root, администратор устройства и право WRITE_SECURE_SETTINGS приложению не требуются.'
    if(-not $Yes -and (Read-Host 'Автомобиль припаркован. Выполнить эти действия? [Y/N]') -notmatch '^[YyДд]$'){exit 2}
    if($Mode -eq 'install') {
        $old=Invoke-JelandAdb -Root $Root -Serial $Serial -Arguments @('shell','dumpsys','package','ru.jeland.os')
        if($old.Code -ne 0 -or $old.TimedOut){throw 'Не удалось проверить установленную версию.'}
        if($old.Text -match '\bversionCode=(\d+)' -and [long]$Matches[1] -gt [long]$manifest.os.versionCode){throw 'Уже стоит более новая версия. Понижение не выполняется; нужен более новый APK со службой помощи.'}
        & powershell.exe -NoProfile -ExecutionPolicy Bypass -File (Join-Path $Root 'engine/Install-APK.ps1') -ApkPath (Join-Path $Root $manifest.os.file) -Serial $Serial -Yes
        if($LASTEXITCODE -ne 0){throw 'Установка не подтверждена. Разрешения не менялись; смотрите журнал установки.'}
        $installed=Invoke-JelandAdb -Root $Root -Serial $Serial -Arguments @('shell','dumpsys','package','ru.jeland.os')
        $null=& $save 'installed-version' $installed
        if($installed.Code -ne 0 -or $installed.Text -notmatch ('\bversionCode='+$manifest.os.versionCode+'\b') -or
            $installed.Text -notmatch ('versionName='+[regex]::Escape($manifest.os.versionName)+'\s')){throw 'Не подтверждена версия из комплекта.'}
    }
    $operation=if($Mode -eq 'disable'){'revoke'}else{'allow'}
    $state=Invoke-JelandInstallAssist -Root $Root -Serial $Serial -Mode $operation -Report $save
    if($state.Allowed) {
        foreach($operationArgs in @(@('shell','cmd','appops','set','--user','0','ru.jeland.os','REQUEST_INSTALL_PACKAGES','allow'),
                          @('shell','cmd','appops','get','--user','0','ru.jeland.os','REQUEST_INSTALL_PACKAGES'))) {
            $r=Invoke-JelandAdb -Root $Root -Serial $Serial -Arguments $operationArgs
            $null=& $save ('appop-'+$operationArgs[3]) $r
            if($r.Code -ne 0 -or $r.TimedOut){throw 'Не подтверждено разрешение установки APK.'}
        }
        if($r.Text -notmatch 'REQUEST_INSTALL_PACKAGES:\s*allow\b'){throw 'Android не подтвердил разрешение установки APK.'}
        $r=Invoke-JelandAdb -Root $Root -Serial $Serial -Arguments @('shell','am','start','--user','0','-n','ru.jeland.os/.MainActivity')
        $null=& $save 'open-os' $r
        $bound=$false
        for($attempt=0;$attempt -lt 8;$attempt++) {
            $binding=Invoke-JelandAdb -Root $Root -Serial $Serial -Arguments @('shell','dumpsys','accessibility') -TimeoutSeconds 5
            $null=& $save 'service-binding' $binding
            if($binding.Code -eq 0 -and $binding.Text -match '(?s)Bound services:\{(.*?)\}\s*Enabled services:' -and
                $Matches[1] -match 'Jeland OS|UpdateInstallAssistService'){$bound=$true;break}
            Start-Sleep -Milliseconds 250
        }
        if(-not $bound){Write-Host 'Разрешение сохранено, но подключение службы пока не подтверждено. Откройте Jeland OS и выполните status.cmd. Смотрите service-binding.txt.' -ForegroundColor Yellow;exit 2}
        Write-Host 'Готово. В OS проверьте обновление до 0.9.40. Если галочка помощи раньше была выключена вручную, включите её в настройках Jeland OS.' -ForegroundColor Green
    }else {Write-Host 'Служба помощи Jeland OS отключена. Другие службы сохранены.' -ForegroundColor Green}
    exit 0
}catch {Write-Host $_.Exception.Message -ForegroundColor Red;exit 1}
finally {if($log){Write-Host ('Журнал: '+$log)}}
