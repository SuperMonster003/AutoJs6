@echo off
setlocal
pushd "%~dp0"
where python >nul 2>nul
if errorlevel 1 (
    echo Python 3.10 or later with Tcl/Tk is required. Install Python, then run this file again.
    pause
    popd
    exit /b 1
)
python -m capture.real_gui %*
set "collector_exit=%errorlevel%"
if not "%collector_exit%"=="0" pause
popd
exit /b %collector_exit%
