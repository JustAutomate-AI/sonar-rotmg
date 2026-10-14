@echo off
rem Checks the prerequisites, offers to install what is missing, builds Sonar and creates Sonar.exe.
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0tools\setup.ps1" %*
echo.
pause
