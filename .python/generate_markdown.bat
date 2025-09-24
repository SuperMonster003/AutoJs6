@ECHO OFF
:RUN
py "generate_markdown.py"
ECHO.

ECHO Press [R] to rerun the scrapers, or [ESC]/[Enter]/[Space] to exit...
powershell -NoLogo -NoProfile -Command "$ErrorActionPreference='Stop'; while($true){ $k=$Host.UI.RawUI.ReadKey('NoEcho,IncludeKeyDown'); if($k.VirtualKeyCode -eq 27 -or $k.VirtualKeyCode -eq 13 -or $k.Character -eq ' '){ exit 1 } elseif($k.Character -match '^[Rr]$'){ exit 0 } }"

IF ERRORLEVEL 1 GOTO EXIT

ECHO.
GOTO RUN

:EXIT
ECHO.
EXIT /B