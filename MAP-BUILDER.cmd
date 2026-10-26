@echo off
setlocal
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0tools\map-builder\start.ps1" %*
if errorlevel 1 pause
