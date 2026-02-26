@echo off
setlocal

set "SCRIPT_DIR=%~dp0"
set "COM_START=%SCRIPT_DIR%com\START.bat"

if not exist "%COM_START%" (
    echo [ERROR] Cannot find "%COM_START%".
    pause
    exit /b 1
)

call "%COM_START%" %*
set "RC=%ERRORLEVEL%"
endlocal & exit /b %RC%
