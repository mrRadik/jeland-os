param(
    [Parameter(Mandatory=$true)][string]$ApkPath,
    [string]$AaptPath,
    [string]$OutputDirectory
)
$ErrorActionPreference='Stop'
# Prepares release metadata from an EXISTING APK. Does not build, sign, upload or publish anything.
$apk=(Resolve-Path -LiteralPath $ApkPath).Path
$name=[IO.Path]::GetFileName($apk)
if ($name -cnotmatch '^JelandOS-[A-Za-z0-9._-]+\.apk$' -or $name.Contains('..')) { throw 'Имя файла должно быть JelandOS-<версия>.apk' }
if (-not $AaptPath) {
    $sdk=$env:ANDROID_SDK_ROOT
    if (-not $sdk) { $sdk='C:\Users\nonam\Documents\Codex\2026-10-15\referenced-chatgpt-conversation-this-is-an\work\jelandos-build\sdk' }
    $AaptPath=Join-Path $sdk 'build-tools\35.0.0\aapt.exe'
}
if (-not (Test-Path -LiteralPath $AaptPath)) { throw 'Укажи -AaptPath к aapt.exe из Android SDK' }
$badging=@(& $AaptPath dump badging $apk)
if ($LASTEXITCODE -ne 0) { throw 'Не удалось прочитать APK' }
$package=$badging | Where-Object { $_ -match '^package:' } | Select-Object -First 1
if ($package -notmatch "name='([^']+)' versionCode='(\d+)' versionName='([^']+)'") { throw 'Некорректное описание APK' }
$appId=$Matches[1];$code=[long]$Matches[2];$version=$Matches[3]
if ($appId -ne 'ru.jeland.os') { throw 'Это не Jeland OS' }
$sdkLine=$badging | Where-Object { $_ -match '^sdkVersion:' } | Select-Object -First 1
if ($sdkLine -notmatch "sdkVersion:'(\d+)'") { throw 'Не определён minSdk' }
$minSdk=[int]$Matches[1]
$size=(Get-Item -LiteralPath $apk).Length
if ($size -lt 1 -or $size -gt 512MB -or $minSdk -lt 30) { throw 'Размер APK или minSdk не поддерживается обновлением' }
$sha=(Get-FileHash -LiteralPath $apk -Algorithm SHA256).Hash.ToLowerInvariant()
if (-not $OutputDirectory) { $OutputDirectory=[IO.Path]::GetDirectoryName($apk) }
$output=(Resolve-Path -LiteralPath $OutputDirectory).Path
$manifest=Join-Path $output 'jelandos-update.json'
$data=[ordered]@{schema=1;applicationId=$appId;versionCode=$code;versionName=$version;minSdk=$minSdk;apkName=$name;size=$size;sha256=$sha}
[IO.File]::WriteAllText($manifest,($data | ConvertTo-Json),[Text.UTF8Encoding]::new($false))
Write-Host "Готово: $manifest"
Write-Host "В один публичный стабильный GitHub Release mrRadik/jeland-os загрузи $name и jelandos-update.json."
Write-Host 'Ничего не опубликовано. APK должен иметь ту же подпись, что установленное приложение.'
