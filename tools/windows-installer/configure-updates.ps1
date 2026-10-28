#requires -version 5.1
[CmdletBinding()]
param([string]$Serial,[ValidateSet('allow','revoke','status')][string]$Mode='allow',[switch]$Yes)
$ErrorActionPreference='Stop'
. (Join-Path $PSScriptRoot 'install.ps1') -LibraryOnly
. (Join-Path $PSScriptRoot 'engine/InstallAssist.ps1')
try {
    $null=Read-JelandBundle $PSScriptRoot
    $Serial=Select-JelandDevice -Root $PSScriptRoot -Serial $Serial
    if($Mode -eq 'allow'){
        Write-Host 'Альтернативный вариант установки обновлений Jeland OS' -ForegroundColor Cyan
        Write-Host 'Служба нажимает Установить только для обновления Jeland OS после вашего запуска и подтверждения.'
        Write-Host 'Разрешение сохраняется после перезагрузки. Другие службы не изменяются.'
    }
    if($Mode -ne 'status' -and -not $Yes -and (Read-Host 'Разрешить выбранное изменение? [Y/N]') -notmatch '^[YyДд]$'){exit 2}
    $result=Invoke-JelandInstallAssist -Root $PSScriptRoot -Serial $Serial -Mode $Mode
    Write-Host ('Альтернативная установка подключена: '+$result.Allowed) -ForegroundColor Green
    exit 0
}catch {Write-Host $_.Exception.Message -ForegroundColor Red;exit 1}
