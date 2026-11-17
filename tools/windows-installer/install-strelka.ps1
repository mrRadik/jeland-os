#requires -version 5.1
[CmdletBinding()]
param([string]$ApkPath,[string]$Serial,[switch]$Yes,[switch]$LibraryOnly)
$ErrorActionPreference='Stop'
. (Join-Path $PSScriptRoot 'engine/AdbTools.ps1')
. (Join-Path $PSScriptRoot 'engine/ApkMetadata.ps1')
. (Join-Path $PSScriptRoot 'engine/StrelkaAccess.ps1')

function Get-StrelkaApk([string]$Root,[string]$ApkPath) {
    if($ApkPath){return (Read-StrelkaApkInfo $ApkPath)}
    $local=Join-Path $Root 'Strelka.apk'
    if(Test-Path -LiteralPath $local){return (Read-StrelkaApkInfo $local)}
    $folder=Join-Path $Root 'packages';[void](New-Item -ItemType Directory -Force -Path $folder)
    $download=Join-Path $folder ('strelka_'+[guid]::NewGuid().ToString('N')+'.apk')
    try {
        Write-Host 'Загрузка обычной Стрелки с официального сайта…'
        [Net.ServicePointManager]::SecurityProtocol=[Net.ServicePointManager]::SecurityProtocol -bor [Net.SecurityProtocolType]::Tls12
        Invoke-WebRequest -UseBasicParsing -Uri 'https://www.ivolk.ru/getfile.php?fid=1' -OutFile $download -TimeoutSec 120
        $info=Read-StrelkaApkInfo $download
        if($info.Package -cne 'com.ivolk.StrelkaGPS'){throw 'Сайт вернул APK другого приложения.'}
        $cache=Join-Path $folder 'Strelka-official.apk'
        Move-Item -LiteralPath $download -Destination $cache -Force
        return (Read-StrelkaApkInfo $cache)
    }finally {if(Test-Path -LiteralPath $download){Remove-Item -LiteralPath $download -Force}}
}

function Install-StrelkaApk([string]$Root,[string]$Serial,[string]$Path) {
    & powershell.exe -NoProfile -ExecutionPolicy Bypass -File (Join-Path $Root 'engine/Install-APK.ps1') -ApkPath $Path -Serial $Serial -Yes | Out-Host
    return $LASTEXITCODE
}

function Invoke-StrelkaInstall([string]$Root,[string]$Serial,[string]$ApkPath) {
    $package='com.ivolk.StrelkaGPS'
    $log=Join-Path $Root ('logs/Strelka_'+(Get-Date -Format 'yyyyMMdd_HHmmss_fff')+"_$PID")
    [void](New-Item -ItemType Directory -Force -Path $log)
    try {
        $apk=Get-StrelkaApk -Root $Root -ApkPath $ApkPath
        if($apk.Package -cne $package){throw 'Нужна обычная Стрелка (com.ivolk.StrelkaGPS). Этот APK не будет установлен.'}
        $Serial=Select-JelandDevice -Root $Root -Serial $Serial
        $step={param([string]$Name,[string[]]$Arguments)
            $reply=Invoke-JelandAdb -Root $Root -Serial $Serial -Arguments $Arguments
            @("EXIT=$($reply.Code)","TIMEOUT=$($reply.TimedOut)",$reply.Text) | Out-File -LiteralPath (Join-Path $log ($Name+'.txt')) -Encoding utf8
            if($reply.Code -ne 0 -or $reply.TimedOut -or $reply.Text -match '(?im)^\s*(error\b|.*exception\b|permission deni|unknown command|unknown option)'){throw ("Не выполнено ${Name}: "+$reply.Text)}
            return $reply.Text
        }
        $user=& $step 'user' @('shell','am','get-current-user')
        if($user.Trim() -ne '0'){throw 'Поддерживается только основной пользователь Android 0.'}
        $sdkText=& $step 'sdk' @('shell','getprop','ro.build.version.sdk')
        $sdk=0;if(-not [int]::TryParse($sdkText.Trim(),[ref]$sdk) -or $sdk -lt 30){throw 'Требуется Android 11 или новее.'}
        Write-Host ("Стрелка "+$apk.VersionName+" / "+$apk.VersionCode) -ForegroundColor Cyan
        Write-Host ('APK: '+$apk.Path)
        Write-Host ('SHA256: '+$apk.Sha256)
        Write-Host 'Установка сохраняет данные. GPS, фоновый GPS, телефон и доступные Bluetooth/уведомления будут разрешены автоматически.'
        Write-Host 'Также: фоновые процессы, окна поверх приложений, обновления APK и исключение Стрелки из энергосбережения.'
        Write-Host 'Jeland OS получит доступ к системным уведомлениям; он обрабатывает только предупреждения Стрелки.'
        $result=Install-StrelkaApk -Root $Root -Serial $Serial -Path $apk.Path
        if($result -ne 0){Write-Host 'Установка не подтверждена. Разрешения не изменены.' -ForegroundColor Yellow;return $result}
        $installed=& $step 'package' @('shell','dumpsys','package',$package)
        if($installed -notmatch ('\bversionCode='+$apk.VersionCode+'\b')){throw 'Версия установленной Стрелки не совпадает с выбранным APK.'}
        function Has-Permission([string]$Permission){return $installed -match ('(?m)^\s*'+[regex]::Escape($Permission)+'(?:\s|:|$)')}
        $permissions=@('android.permission.ACCESS_COARSE_LOCATION','android.permission.ACCESS_FINE_LOCATION','android.permission.READ_PHONE_STATE')
        if(Has-Permission 'android.permission.ACCESS_BACKGROUND_LOCATION'){$permissions+='android.permission.ACCESS_BACKGROUND_LOCATION'}
        if($sdk -ge 31 -and (Has-Permission 'android.permission.BLUETOOTH_CONNECT')){$permissions+='android.permission.BLUETOOTH_CONNECT'}
        if($sdk -ge 33 -and (Has-Permission 'android.permission.POST_NOTIFICATIONS')){$permissions+='android.permission.POST_NOTIFICATIONS'}
        foreach($permission in $permissions) {
            if(-not (Has-Permission $permission)){throw ('APK не запрашивает обязательное право: '+$permission)}
            $null=& $step ('grant-'+$permission) @('shell','pm','grant','--user','0',$package,$permission)
        }
        $granted=& $step 'permissions-after' @('shell','dumpsys','package',$package)
        foreach($permission in $permissions){if($granted -notmatch ([regex]::Escape($permission)+':\s*granted=true\b')){throw ('Не подтверждено право: '+$permission)}}
        $ops=@('POST_NOTIFICATION','RUN_IN_BACKGROUND','RUN_ANY_IN_BACKGROUND')
        if(Has-Permission 'android.permission.SYSTEM_ALERT_WINDOW'){$ops+='SYSTEM_ALERT_WINDOW'}
        if(Has-Permission 'android.permission.REQUEST_INSTALL_PACKAGES'){$ops+='REQUEST_INSTALL_PACKAGES'}
        foreach($op in $ops) {
            $null=& $step ('set-'+$op) @('shell','cmd','appops','set','--user','0',$package,$op,'allow')
            $check=& $step ('check-'+$op) @('shell','cmd','appops','get','--user','0',$package,$op)
            if($check -notmatch ('(?im)^\s*'+$op+':\s*allow\b')){throw ('Не подтверждено разрешение: '+$op)}
        }
        $null=& $step 'battery-add' @('shell','dumpsys','deviceidle','whitelist',('+'+$package))
        $whitelist=& $step 'battery-check' @('shell','dumpsys','deviceidle','whitelist')
        if($whitelist -notmatch ('(?m)^(?:user|system(?:-excidle)?),'+[regex]::Escape($package)+',\d+\s*$')){throw 'Не подтверждено исключение Стрелки из энергосбережения.'}
        Invoke-StrelkaSetup -Root $Root -Serial $Serial -Mode enable -Yes $true
        $null=& $step 'open-strelka' @('shell','am','start','--user','0','-a','android.intent.action.VIEW','-n','com.ivolk.StrelkaGPS/.MainActivity','--ei','scut_main','1')
        Write-Host 'Стрелка установлена, разрешения выданы и проверены.' -ForegroundColor Green
        Write-Host 'В Стрелке: загрузите базу, активируйте PRO при наличии и включите Опции -> Дополнительно -> Вывод уведомлений.'
        Write-Host 'В Jeland OS откройте Настройки -> Камеры и дорожные опасности и включите предупреждения Стрелки.'
        return 0
    }catch {Write-Host $_.Exception.Message -ForegroundColor Red;return 1}
    finally {Write-Host ('Журнал: '+$log)}
}
if($LibraryOnly){return}
exit (Invoke-StrelkaInstall -Root $PSScriptRoot -Serial $Serial -ApkPath $ApkPath)
