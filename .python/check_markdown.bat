@ECHO OFF
py "%~dp0generate_markdown.py" --check
EXIT /B %ERRORLEVEL%
