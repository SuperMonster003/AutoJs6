@ECHO OFF
:RUN
node "run-scrapers.mjs"
ECHO.

ECHO Press [R] to rerun, [Shift+R] to clear screen then rerun, or [ESC]/[Enter]/[Space] to exit...
powershell -NoLogo -NoProfile -Command ^
  "$ErrorActionPreference='Stop';" ^
  "while($true){" ^
  "  $k=$Host.UI.RawUI.ReadKey('NoEcho,IncludeKeyDown');" ^
  "  if($k.VirtualKeyCode -eq 27 -or $k.VirtualKeyCode -eq 13 -or $k.Character -eq ' '){ exit 1 }" ^
  "  elseif($k.VirtualKeyCode -eq 82){" ^
  "    $isShift = ($k.ControlKeyState -band 0x0010) -ne 0 -or ($k.ControlKeyState -band 0x0080) -ne 0;" ^
  "    if($isShift){ exit 2 } else { exit 0 }" ^
  "  }" ^
  "}"

IF ERRORLEVEL 2 GOTO SHIFT_R
IF ERRORLEVEL 1 GOTO EXIT

ECHO.
GOTO RUN

:SHIFT_R
CLS
ECHO.
GOTO RUN

:EXIT
ECHO.
EXIT /B