#requires -version 5.1
[CmdletBinding()]
param(
    [Parameter(Position=0)][string]$ApkPath,
    [string]$Serial,
    [switch]$Yes
)
$ErrorActionPreference = 'Stop'
$Root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
$Adb = Join-Path $Root 'tools\adb.exe'
$Helper = Join-Path $Root 'install-helper\helper.dex'
$RunId = (Get-Date -Format 'yyyyMMdd_HHmmss_fff') + "_$PID"
$OutDir = Join-Path $Root "logs\JelandInstall_$RunId"
$RemoteDir = "/data/local/tmp/jeland_install_tests_$RunId"
$script:DeviceSerial = $null
$script:Uncertain = $false
$transcribing = $false
$remoteCreated = $false
$exitCode = 1
$results = @()

function Quote-NativeArgument([AllowEmptyString()][string]$Value) {
    # Windows CommandLineToArgvW escaping, not shell escaping.
    $escaped = [regex]::Replace($Value, '(\\*)"', '$1$1\"')
    $escaped = [regex]::Replace($escaped, '(\\+)$', '$1$1')
    return '"' + $escaped + '"'
}

function Invoke-Adb {
    param([string[]]$Arguments, [switch]$Global, [switch]$Quiet, [int]$TimeoutSeconds = 30)
    $nativeArgs = @()
    if (-not $Global -and $script:DeviceSerial) { $nativeArgs += @('-s', $script:DeviceSerial) }
    $nativeArgs += $Arguments
    $start = New-Object Diagnostics.ProcessStartInfo
    $start.FileName = $Adb
    $start.Arguments = (($nativeArgs | ForEach-Object { Quote-NativeArgument $_ }) -join ' ')
    $start.UseShellExecute = $false
    $start.CreateNoWindow = $true
    $start.RedirectStandardOutput = $true
    $start.RedirectStandardError = $true
    $start.StandardOutputEncoding = [Text.Encoding]::UTF8
    $start.StandardErrorEncoding = [Text.Encoding]::UTF8
    $process = New-Object Diagnostics.Process
    $process.StartInfo = $start
    try {
        [void]$process.Start()
        $stdout = $process.StandardOutput.ReadToEndAsync()
        $stderr = $process.StandardError.ReadToEndAsync()
        $timedOut = -not $process.WaitForExit($TimeoutSeconds * 1000)
        if ($timedOut) {
            try { $process.Kill() } catch { }
            [void]$process.WaitForExit(5000)
        }
        $code = if ($timedOut) { 124 } else { $process.ExitCode }
        $text = $stdout.GetAwaiter().GetResult().TrimEnd() + "`n" + $stderr.GetAwaiter().GetResult().TrimEnd()
        $text = $text.Trim()
        if (-not $Quiet -and $text) { Write-Host $text }
        return [pscustomobject]@{ Code = $code; Text = $text; TimedOut = $timedOut }
    }
    finally { $process.Dispose() }
}

function Select-Device {
    $list = Invoke-Adb -Arguments @('devices', '-l') -Global
    if ($list.Code -ne 0) { throw 'Не удалось получить список ADB-устройств.' }
    $devices = @()
    foreach ($line in ($list.Text -split '\r?\n')) {
        if ($line -match '^(\S+)\s+(device|offline|unauthorized)\b') {
            $devices += [pscustomobject]@{ Serial = $Matches[1]; State = $Matches[2] }
        }
    }
    if ($Serial) {
        $selected = @($devices | Where-Object { $_.Serial -eq $Serial -and $_.State -eq 'device' })
        if ($selected.Count -ne 1) { throw "Устройство '$Serial' не найдено или не авторизовано." }
        $script:DeviceSerial = $selected[0].Serial
        return
    }
    $ready = @($devices | Where-Object State -eq 'device')
    if ($ready.Count -eq 0) { throw 'Нет авторизованного ГУ. Проверьте кабель и RSA-запрос.' }
    if ($ready.Count -eq 1) { $script:DeviceSerial = $ready[0].Serial; return }
    for ($i=0; $i -lt $ready.Count; $i++) { Write-Host "$($i+1). $($ready[$i].Serial)" }
    $number = 0
    if (-not [int]::TryParse((Read-Host 'Номер ГУ'), [ref]$number) -or $number -lt 1 -or $number -gt $ready.Count) {
        throw 'Некорректный номер устройства.'
    }
    $script:DeviceSerial = $ready[$number-1].Serial
}

function Select-Apk {
    if (-not $ApkPath) {
        $files = @(Get-ChildItem -LiteralPath $Root -Filter '*.apk' -File | Sort-Object Name)
        for ($i=0; $i -lt $files.Count; $i++) { Write-Host "$($i+1). $($files[$i].Name)" }
        Write-Host '0. Указать полный путь'
        $number = 0
        if (-not [int]::TryParse((Read-Host 'Номер APK'), [ref]$number)) { throw 'Некорректный номер APK.' }
        if ($number -eq 0) { $script:ApkPath = Read-Host 'Путь к APK' }
        elseif ($number -ge 1 -and $number -le $files.Count) { $script:ApkPath = $files[$number-1].FullName }
        else { throw 'Некорректный номер APK.' }
    }
    $path = $ApkPath.Trim().Trim('"')
    if (-not [IO.Path]::IsPathRooted($path)) { $path = Join-Path $Root $path }
    $file = Get-Item -LiteralPath $path
    if ($file.PSIsContainer -or $file.Extension -ine '.apk') { throw 'Нужен один обычный .apk файл.' }
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $stream = [IO.Compression.ZipFile]::OpenRead($file.FullName)
    try {
        if (-not $stream.GetEntry('AndroidManifest.xml')) { throw 'В APK отсутствует AndroidManifest.xml. Нужен APK, а не XAPK/APKS/ZIP.' }
    } finally { $stream.Dispose() }
    return $file
}

function Save-Diagnostic([string]$Name, [string[]]$Arguments) {
    $result = Invoke-Adb -Arguments $Arguments -Quiet
    @("EXIT=$($result.Code)", $result.Text) | Out-File -LiteralPath (Join-Path $OutDir $Name) -Encoding utf8
}

try {
    New-Item -ItemType Directory -Path $OutDir -Force | Out-Null
    Start-Transcript -LiteralPath (Join-Path $OutDir 'run.log') | Out-Null
    $transcribing = $true
    Write-Host 'Jeland: установка / обновление одного APK через PackageInstaller API' -ForegroundColor Cyan
    if (-not (Test-Path -LiteralPath $Adb -PathType Leaf)) { throw "Не найден ADB: $Adb" }
    if (-not (Test-Path -LiteralPath $Helper -PathType Leaf)) { throw "Не найден DEX: $Helper" }

    Write-Host 'Обновление сохраняет данные. Без удаления, понижения версии и автоматической выдачи разрешений.'
    Select-Device
    Write-Host "Устройство: $script:DeviceSerial (пользователь 0)"
    $currentUser = Invoke-Adb -Arguments @('shell','am','get-current-user') -Quiet
    if ($currentUser.Code -ne 0 -or $currentUser.Text.Trim() -ne '0') { throw 'Поддерживается только активный пользователь Android 0. Установка не начата.' }
    $apk = $null
        $apk = Select-Apk
        Write-Host "APK: $($apk.FullName)"
        Write-Host "Байт: $($apk.Length)"
        Write-Host "SHA256: $((Get-FileHash -LiteralPath $apk.FullName -Algorithm SHA256).Hash)"
        if (-not $Yes -and (Read-Host 'Установить этот APK на выбранное устройство? [Y/N]') -notmatch '^[YyДд]$') {
            throw [OperationCanceledException]::new('Проверка отменена.')
        }
    Write-Host "Выбрано: $script:DeviceSerial"
    Save-Diagnostic 'identity.txt' @('shell', 'id')
    Save-Diagnostic 'selinux.txt' @('shell', 'getenforce')
    Save-Diagnostic 'build.txt' @('shell', 'getprop', 'ro.build.display.id')
    Save-Diagnostic 'install-mode-before.txt' @('shell', 'getprop', 'persist.sys.sv.isl')
    $binary = '/system/bin/app_process'
    $binaryCheck = Invoke-Adb -Arguments @('shell', 'ls', '-l', $binary) -Quiet
    if ($binaryCheck.Code -ne 0) { throw "Недоступен Android runtime: $binary" }
    Write-Host "Runtime: $binary"
    $mkdir = Invoke-Adb -Arguments @('shell', 'mkdir', '-p', $RemoteDir) -Quiet
    if ($mkdir.Code -ne 0) { throw "Не удалось создать временную папку: $($mkdir.Text)" }
    $remoteCreated = $true
    $remoteDex = "$RemoteDir/helper.dex"
    $remoteApk = "$RemoteDir/input.apk"
    $push = Invoke-Adb -Arguments @('push', $Helper, $remoteDex) -TimeoutSeconds 90
    if ($push.Code -ne 0) { throw 'Не удалось передать DEX на ГУ.' }
    if ($apk) {
        $push = Invoke-Adb -Arguments @('push', $apk.FullName, $remoteApk) -TimeoutSeconds 180
        if ($push.Code -ne 0) { throw 'Не удалось передать APK на ГУ.' }
        $size = Invoke-Adb -Arguments @('shell', 'stat', '-c%s', $remoteApk) -Quiet
        if ($size.Code -ne 0 -or $size.Text.Trim() -ne "$($apk.Length)") { throw 'Размер APK на ГУ не совпал.' }
    }

    foreach ($mode in @('Api')) {
        Write-Host "`nУстановка: PackageInstaller API" -ForegroundColor Cyan
        $timeout = 180
        $command = "CLASSPATH=$remoteDex $binary /system/bin ru.jeland.installer.InstallVariants api $remoteApk $timeout"
        Write-Host "Команда: $command" -ForegroundColor DarkGray
        $script:Uncertain = $true
        $result = Invoke-Adb -Arguments @('shell', $command) -TimeoutSeconds ($timeout + 30)
        $result.Text | Out-File -LiteralPath (Join-Path $OutDir "$mode.log") -Encoding utf8
        $unknown = $result.TimedOut -or $result.Text -match '(?m)^JELAND_RESULT=UNKNOWN_TIMEOUT\s*$'
        $success = $result.Code -eq 0 -and $result.Text -match '(?m)^JELAND_RESULT=SUCCESS\s*$'
        # A process dying after commit without a final verdict is uncertain too.
        if (-not $success -and $result.Text -match '(?m)^JELAND_PHASE=COMMIT\s*$' -and
            $result.Text -notmatch '(?m)^JELAND_FINAL_STATUS=(-1|[1-8])\s*$') { $unknown = $true }
        if (-not $success -and $result.Text -match '(?m)^JELAND_FINAL_STATUS=0\s*$') { $unknown = $true }
        if (-not $success -and $result.Text -notmatch '(?m)^JELAND_RESULT=(FAIL|NEEDS_CONFIRMATION)\s*$') { $unknown = $true }
        $script:Uncertain = $unknown
        $state = if ($success) { 'SUCCESS' } elseif ($unknown) { 'UNKNOWN' } else { 'FAIL' }
        if ($result.Text -match '(?m)^JELAND_PACKAGE=([A-Za-z0-9_.]+)\s*$') {
            $package = $Matches[1]
            Save-Diagnostic "$mode-package.txt" @('shell', 'dumpsys', 'package', $package)
            if ($success) {
                $path = Invoke-Adb -Arguments @('shell', 'pm', 'path', $package) -Quiet
                $path.Text | Out-File -LiteralPath (Join-Path $OutDir "$mode-package-path.txt") -Encoding utf8
                if ($path.Code -ne 0 -or $path.Text -notmatch '(?m)^package:') {
                    $success = $false; $state = 'UNKNOWN'; $script:Uncertain = $true
                }
            }
        } elseif ($success) {
            $success = $false; $state = 'UNKNOWN'; $script:Uncertain = $true
        }
        $results += [pscustomobject]@{ Variant=$mode; Runtime='Auto'; Exit=$result.Code; Result=$state; Device=$script:DeviceSerial }
        $results | Export-Csv -LiteralPath (Join-Path $OutDir 'summary.csv') -Encoding UTF8 -NoTypeInformation
        if ($success) {
            Write-Host 'Установлено. Пакет и версия проверены.' -ForegroundColor Green
            if($package -eq 'ru.jeland.os'){
                Write-Host 'Далее комплектный установщик проверит и настроит разрешения Jeland OS.'
            }
            $exitCode = 0
            break
        }
        if ($script:Uncertain) {
            Write-Host 'Результат пока неизвестен. Следующие попытки автоматически не запускаются.' -ForegroundColor Yellow
            $exitCode = 3
            break
        }
        Write-Host "Установка не выполнена. Причина записана в $mode.log. Другие способы автоматически не запускаются." -ForegroundColor Yellow
    }
}
catch {
    if ($_.Exception -is [OperationCanceledException]) {
        Write-Host $_.Exception.Message -ForegroundColor Yellow
        $exitCode = 2
    } else {
        Write-Host "ОШИБКА: $($_.Exception.Message)" -ForegroundColor Red
        $exitCode = if ($script:Uncertain) { 3 } else { 1 }
        if ($script:Uncertain) { Write-Host 'Результат установки не подтверждён. Не повторяйте её автоматически.' -ForegroundColor Yellow }
    }
}
finally {
    if ($remoteCreated) {
        try {
            Save-Diagnostic 'install-mode-after.txt' @('shell', 'getprop', 'persist.sys.sv.isl')
            Save-Diagnostic 'logcat-installer.txt' @('logcat', '-d', '-t', '600', '-v', 'threadtime',
                'PackageManager:*', 'PackageInstaller:*', 'PackageInstallerSession:*', 'InstallStart:*', 'ActivityTaskManager:*', 'AndroidRuntime:*', '*:S')
            if (-not $script:Uncertain) {
                $cleanup = Invoke-Adb -Arguments @('shell', 'rm', '-f', "$RemoteDir/helper.dex", "$RemoteDir/input.apk") -Quiet
                $removeDir = Invoke-Adb -Arguments @('shell', 'rmdir', $RemoteDir) -Quiet
                if ($cleanup.Code -ne 0 -or $removeDir.Code -ne 0) {
                    Write-Host "Временные файлы не полностью удалены: $RemoteDir" -ForegroundColor Yellow
                }
            } else {
                Write-Host "Временные файлы оставлены для диагностики: $RemoteDir" -ForegroundColor Yellow
            }
        } catch { Write-Host "Диагностика/очистка: $($_.Exception.Message)" -ForegroundColor Yellow }
    }
    Write-Host "`nРезультаты: $OutDir" -ForegroundColor Cyan
    if ($transcribing) { Stop-Transcript | Out-Null }
    if (Test-Path -LiteralPath $OutDir) {
        try {
            Compress-Archive -Path (Join-Path $OutDir '*') -DestinationPath "$OutDir.zip"
            Write-Host "Архив логов: $OutDir.zip" -ForegroundColor Cyan
        } catch { Write-Host "Логи сохранены в папке, ZIP не создан: $($_.Exception.Message)" -ForegroundColor Yellow }
    }
}
exit $exitCode
