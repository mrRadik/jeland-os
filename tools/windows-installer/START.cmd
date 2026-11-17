@echo off
setlocal EnableExtensions
chcp 65001 >nul
cd /d "%~dp0"
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0windows-setup.ps1" -Serial "%~1"
set "JELAND_RESULT=%ERRORLEVEL%"
echo.
pause
exit /b %JELAND_RESULT%
