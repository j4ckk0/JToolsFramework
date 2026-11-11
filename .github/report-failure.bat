@echo off
rem The end of a failed step's log, its last 60 lines, as the step's error annotation, which GitHub's API shows to
rem anyone (a job's log needs the repository's admin): report-failure.bat TITLE LOGFILE. The CI's alone; a "!" in the
rem log is lost, and the annotation keeps its last 7000 characters (cmd's variables stop at 8191).
setlocal EnableExtensions EnableDelayedExpansion
set "TITLE=%~1"
set "LOG=%~2"
set "TOTAL=0"
for /f %%n in ('%SystemRoot%\System32\find.exe /c /v "" ^< "%LOG%"') do set "TOTAL=%%n"
set /a FROM=TOTAL-60
set "N=0"
set "TEXT="
rem findstr /n keeps the empty lines, which a for /f alone would drop; the annotation's line breaks are %0A, its % %25
for /f "delims=" %%l in ('%SystemRoot%\System32\findstr.exe /n "^" "%LOG%"') do (
  set /a N+=1
  if !N! GTR !FROM! (
    set "LINE=%%l"
    set "LINE=!LINE:*:=!"
    if defined LINE set "LINE=!LINE:%%=%%25!"
    set "TEXT=!TEXT!!LINE!%%0A"
    set "TEXT=!TEXT:~-7000!"
  )
)
echo ::error title=!TITLE!::!TEXT!
exit /b 0
