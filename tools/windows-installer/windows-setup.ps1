#requires -version 5.1
[CmdletBinding()]
param([string]$Serial,[switch]$LibraryOnly)
$ErrorActionPreference='Stop'

function Invoke-WindowsSetupChild([string]$Root,[string]$Script,[string[]]$Arguments) {
    & powershell.exe -NoProfile -ExecutionPolicy Bypass -File (Join-Path $Root $Script) @Arguments | Out-Host
    return $LASTEXITCODE
}

function Invoke-WindowsSetupAction([string]$Root,[string]$Choice,[string]$Serial) {
    if($Choice -notin @('1','2','3')){throw 'Нет такого пункта меню.'}
    $code=Invoke-WindowsSetupChild -Root $Root -Script 'install.ps1' -Arguments @('-VerifyOnly')
    if($code -ne 0){Write-Host 'Комплект повреждён или распакован не полностью. Действие отменено.' -ForegroundColor Red;return $code}
    $arguments=@()
    if($Serial){$arguments+=@('-Serial',$Serial)}
    switch($Choice) {
        '1' {$scriptName='install.ps1'}
        '2' {$scriptName='install-strelka.ps1'}
        '3' {$scriptName='configure-updates.ps1';$arguments+=@('-Mode','allow','-Yes')}
    }
    return (Invoke-WindowsSetupChild -Root $Root -Script $scriptName -Arguments $arguments)
}

function Start-JelandWindowsSetup([string]$Root,[string]$Serial) {
    while($true) {
        Write-Host ''
        Write-Host 'Jeland OS — установка и настройка' -ForegroundColor Cyan
        Write-Host '1 — Установить / обновить Jeland OS и RuVoice'
        Write-Host '2 — Установить / обновить Стрелку и выдать права'
        Write-Host '3 — Подключить альтернативный вариант установки обновлений'
        Write-Host '0 — Выход'
        $choice=(Read-Host 'Выберите пункт').Trim()
        if($choice -eq '0'){return}
        if($choice -notin @('1','2','3')){Write-Host 'Выберите 1, 2, 3 или 0.' -ForegroundColor Yellow;continue}
        try {
            $code=Invoke-WindowsSetupAction -Root $Root -Choice $choice -Serial $Serial
            if($code -eq 3){Write-Host 'Результат установки неизвестен. Проверьте версии на ГУ и журнал перед повтором.' -ForegroundColor Yellow}
            elseif($code -ne 0){Write-Host ('Действие завершено с кодом '+$code+'. Проверьте сообщение выше и папку logs.') -ForegroundColor Yellow}
        }catch {Write-Host $_.Exception.Message -ForegroundColor Red}
    }
}

if($LibraryOnly){return}
Start-JelandWindowsSetup -Root $PSScriptRoot -Serial $Serial
