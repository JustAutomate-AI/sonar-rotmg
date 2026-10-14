@echo off
rem Checks the install without changing anything.
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0tools\setup.ps1" -CheckOnly %*
echo.
pause
