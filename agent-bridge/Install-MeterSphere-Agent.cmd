@echo off
setlocal
title MeterSphere Agent Installer
echo Installing MeterSphere Agent for the current Windows user...
echo.
powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\windows\install.ps1" -PackageRoot "%~dp0"
set "MS_AGENT_INSTALL_EXIT=%ERRORLEVEL%"
echo.
if not "%MS_AGENT_INSTALL_EXIT%"=="0" (
  echo Installation failed. Keep this window open and send the error above to your administrator.
) else (
  echo Installation completed. Return to MeterSphere to continue the connection.
)
pause
exit /b %MS_AGENT_INSTALL_EXIT%
