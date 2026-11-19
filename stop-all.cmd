@echo off
chcp 65001 >nul
setlocal
set "SCRIPT_DIR=%~dp0scripts"

powershell -NoProfile -ExecutionPolicy Bypass -File "%SCRIPT_DIR%\stop-all.ps1" %*
if errorlevel 1 (
  echo.
  echo Stop failed. See messages above.
  pause
)
endlocal
