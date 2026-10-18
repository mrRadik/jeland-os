@echo off
setlocal
chcp 65001 >nul
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0RUN-BLUESTACKS.ps1"
echo.
pause
