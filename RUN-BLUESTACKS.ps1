param([string]$Serial='127.0.0.1:5556')
$ErrorActionPreference='Stop'
# Deliberately restrict this helper to loopback: never silently install onto a USB head unit.
if ($Serial -notmatch '^127\.0\.0\.1:\d+$') { throw 'Этот скрипт предназначен только для локального BlueStacks (127.0.0.1:порт).' }
$adb=Join-Path $PSScriptRoot '..\Jeland-ADB-Collector-Portable\tools\adb.exe'
if (-not (Test-Path -LiteralPath $adb)) {
    $found=Get-Command adb.exe -ErrorAction SilentlyContinue
    if (-not $found) { throw 'Не найден adb.exe. Нужна папка ADB-коллектора рядом с JelandOS или adb в PATH.' }
    $adb=$found.Source
}
$apk=Join-Path $PSScriptRoot 'artifacts\JelandOS-0.6.5-rear-seats.apk'
if (-not (Test-Path -LiteralPath $apk)) { $apk=Join-Path $PSScriptRoot 'app\build\outputs\apk\debug\app-debug.apk' }
if (-not (Test-Path -LiteralPath $apk)) { throw 'Сначала соберите APK через BUILD.ps1 или Android Studio.' }
& $adb connect $Serial
$status=(& $adb -s $Serial get-state 2>$null | Out-String).Trim()
if ($status -ne 'device') { throw 'BlueStacks недоступен. Запустите его и включите ADB в дополнительных настройках. Проверьте порт.' }
Write-Host "Устанавливаю Jeland OS только в локальный BlueStacks: $Serial"
& $adb -s $Serial install -r $apk
if ($LASTEXITCODE -ne 0) { throw 'Установка не прошла. Приложение и его данные не удалялись.' }
& $adb -s $Serial shell am start -n 'ru.jeland.os/.MainActivity'
if ($LASTEXITCODE -ne 0) { throw 'Не удалось открыть Jeland OS.' }
Write-Host 'Готово. Jeland VDB Probe не изменён.'
