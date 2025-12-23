@echo off
setlocal
set SCRIPT_DIR=%~dp0
powershell -NoProfile -ExecutionPolicy Bypass -File "%SCRIPT_DIR%gradle-wrapper-switcher.ps1" %*
echo.
pause
endlocal
