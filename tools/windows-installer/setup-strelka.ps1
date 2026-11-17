#requires -version 5.1
[CmdletBinding()]
param([string]$Serial,[ValidateSet('enable','disable','status')][string]$Mode='enable',[switch]$Yes,[switch]$LibraryOnly)
$ErrorActionPreference='Stop'
. (Join-Path $PSScriptRoot 'engine/AdbTools.ps1')

. (Join-Path $PSScriptRoot 'engine/StrelkaAccess.ps1')
if(-not $LibraryOnly){try {Invoke-StrelkaSetup -Root $PSScriptRoot -Serial $Serial -Mode $Mode -Yes $Yes.IsPresent}catch {Write-Host $_.Exception.Message -ForegroundColor Red;exit 1}}
