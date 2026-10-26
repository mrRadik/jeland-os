#requires -version 5.1
[CmdletBinding()]
param([string]$Serial,[switch]$Yes,[switch]$LibraryOnly)
$ErrorActionPreference='Stop'
. (Join-Path $PSScriptRoot 'engine\AdbTools.ps1')

function Read-JelandBundle([string]$Root) {
    $absolute=(Resolve-Path -LiteralPath $Root).Path
    $bundle=Get-Content -LiteralPath (Join-Path $absolute 'bundle.json') -Raw -Encoding UTF8 | ConvertFrom-Json
    if($bundle.schema -ne 1 -or $bundle.os.package -ne 'ru.jeland.os' -or $bundle.voice.package -ne 'ru.kost.ruvoice'){throw 'Некорректное описание комплекта.'}
    foreach($item in $bundle.files) {
        $path=[IO.Path]::GetFullPath((Join-Path $absolute $item.path))
        if(-not $path.StartsWith($absolute+'\',[StringComparison]::OrdinalIgnoreCase) -or -not (Test-Path -LiteralPath $path -PathType Leaf)){throw ('Не найден файл: '+$item.path)}
        if((Get-Item -LiteralPath $path).Length -ne $item.bytes -or (Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash -ne $item.sha256){throw ('Файл повреждён: '+$item.path)}
    }
    foreach($package in @($bundle.voice,$bundle.os)) {
        if($package.file -notmatch '^[A-Za-z0-9_.-]+\.apk$' -or [long]$package.versionCode -le 0){throw 'Некорректное имя или версия APK.'}
        if(@($bundle.files | Where-Object {$_.path -eq ('packages/'+$package.file)}).Count -ne 1){throw 'APK отсутствует в списке контрольных сумм.'}
    }
    foreach($required in @('tools/adb.exe','tools/AdbWinApi.dll','tools/AdbWinUsbApi.dll',
        'install-helper/helper.dex','engine/AdbTools.ps1','engine/Install-APK.ps1')) {
        if(@($bundle.files | Where-Object {$_.path -eq $required}).Count -ne 1){throw ('Отсутствует обязательный файл: '+$required)}
    }
    return $bundle
}

function Install-JelandBundlePackage([string]$Root,[string]$Serial,$Package) {
    & powershell.exe -NoProfile -ExecutionPolicy Bypass -File (Join-Path $Root 'engine\Install-APK.ps1') -ApkPath (Join-Path $Root ('packages\'+$Package.file)) -Serial $Serial -Yes | Out-Host
    return $LASTEXITCODE
}

function Invoke-JelandBundleInstall {
    param([string]$Root,[string]$Serial,[switch]$Yes)
    $bundle=Read-JelandBundle $Root
    $Serial=Select-JelandDevice -Root $Root -Serial $Serial
    $log=Join-Path $Root ('logs\Setup_'+(Get-Date -Format 'yyyyMMdd_HHmmss_fff')+"_$PID")
    [void](New-Item -ItemType Directory -Path $log)
    $step={param([string]$Name,[string[]]$Arguments)
        $reply=Invoke-JelandAdb -Root $Root -Serial $Serial -Arguments $Arguments
        @("EXIT=$($reply.Code)","TIMEOUT=$($reply.TimedOut)",$reply.Text) | Out-File -LiteralPath (Join-Path $log ($Name+'.txt')) -Encoding utf8
        if($reply.Code -ne 0 -or $reply.TimedOut -or $reply.Text -match '(?im)^\s*(error\b|.*exception\b|permission deni|unknown command|unknown option)'){throw ("Не выполнено ${Name}: "+$reply.Text)}
        return $reply
    }
    try {
        $user=& $step 'user' @('shell','am','get-current-user')
        if($user.Text.Trim() -ne '0'){throw 'Поддерживается только основной пользователь Android 0. Ничего не установлено.'}
        $android=& $step 'sdk' @('shell','getprop','ro.build.version.sdk')
        $sdk=0;if(-not [int]::TryParse($android.Text.Trim(),[ref]$sdk) -or $sdk -lt 30){throw 'Требуется Android 11 или новее.'}
        $model=& $step 'model' @('shell','getprop','ro.product.model')
        Write-Host ("Устройство: $Serial / "+$model.Text.Trim()) -ForegroundColor Cyan
        Write-Host ("Jeland OS "+$bundle.os.versionName+" + RuVoice "+$bundle.voice.versionName)
        Write-Host 'Установка сохраняет данные. Права Jeland OS: GPS, состояние связи, Bluetooth, уведомления, автозапуск и установка обновлений.'
        Write-Host 'RuVoice получает только уведомления. Лицензия RuVoice и модели: CC BY-NC-SA 4.0, некоммерческое использование. Тексты — в licenses.'
        if(-not $Yes -and (Read-Host 'Автомобиль припаркован. Установить комплект и выдать эти права? [Y/N]') -notmatch '^[YyДд]$'){return 2}
        foreach($package in @($bundle.voice,$bundle.os)) {
            Write-Host ('Устанавливаем '+$package.package+'… Дождитесь результата.') -ForegroundColor Cyan
            $code=Install-JelandBundlePackage -Root $Root -Serial $Serial -Package $package
            if($code -ne 0){Write-Host 'Установка не подтверждена. Следующий APK и выдача прав не запускаются автоматически.' -ForegroundColor Yellow;return $code}
            $installed=& $step ('version-'+$package.package) @('shell','dumpsys','package',$package.package)
            if($installed.Text -notmatch ('\bversionCode='+$package.versionCode+'\b')){throw ('Не подтверждена установленная версия: '+$package.package)}
        }
        Write-Host 'Настраиваем разрешения…' -ForegroundColor Cyan
        foreach($op in @('SYSTEM_ALERT_WINDOW','REQUEST_INSTALL_PACKAGES')) {
            $null=& $step ('set-'+$op) @('shell','cmd','appops','set','--user','0','ru.jeland.os',$op,'allow')
            $check=& $step ('get-'+$op) @('shell','cmd','appops','get','--user','0','ru.jeland.os',$op)
            if($check.Text -notmatch ('(?im)^\s*'+$op+':\s*allow\b')){throw ('Право не подтверждено: '+$op)}
        }
        $permissions=@('android.permission.ACCESS_COARSE_LOCATION','android.permission.ACCESS_FINE_LOCATION','android.permission.READ_PHONE_STATE')
        if($sdk -ge 31){$permissions+='android.permission.BLUETOOTH_CONNECT'}
        if($sdk -ge 33){$permissions+='android.permission.POST_NOTIFICATIONS'}
        foreach($permission in $permissions){$null=& $step ('grant-'+$permission) @('shell','pm','grant','--user','0','ru.jeland.os',$permission)}
        $granted=& $step 'os-permissions' @('shell','dumpsys','package','ru.jeland.os')
        foreach($permission in $permissions){if($granted.Text -notmatch ([regex]::Escape($permission)+':\s*granted=true\b')){throw ('Не подтверждено право: '+$permission)}}
        if($sdk -ge 33){
            $null=& $step 'voice-notifications' @('shell','pm','grant','--user','0','ru.kost.ruvoice','android.permission.POST_NOTIFICATIONS')
            $voice=& $step 'voice-permissions' @('shell','dumpsys','package','ru.kost.ruvoice')
            if($voice.Text -notmatch 'android\.permission\.POST_NOTIFICATIONS:\s*granted=true\b'){throw 'Не подтверждены уведомления RuVoice.'}
        }
        $null=& $step 'open-os' @('shell','am','start','--user','0','-n','ru.jeland.os/.MainActivity')
        Write-Host 'Готово. При необходимости включите автозапуск в OS. В настройках навигации выберите RuVoice и проверьте голос.' -ForegroundColor Green
        return 0
    }catch {Write-Host $_.Exception.Message -ForegroundColor Red;return 1}
    finally {Write-Host ('Журнал: '+$log)}
}

if($LibraryOnly){return}
try {exit (Invoke-JelandBundleInstall -Root $PSScriptRoot -Serial $Serial -Yes:$Yes)}
catch {Write-Host $_.Exception.Message -ForegroundColor Red;exit 1}
