@echo off
rem Brings a project in line with the devkit this script belongs to - jtools-sync.sh's twin, for Windows: refreshes the
rem files the devkit owns in it (managed-files.list: the scripts, the pre-push hook, .gitattributes) and says what else
rem differs - the framework version its pom names, the externals' versions, its scripts\jtools - without changing
rem those, which are the project's to decide. Commits nothing. Run by the project's scripts\jtools:
rem   scripts\jtools sync            refresh the managed files, report the rest
rem   scripts\jtools sync --check    report only; exit 1 when anything differs (a project's CI)
setlocal EnableExtensions DisableDelayedExpansion
set "DEVKIT=%~dp0"
set "DEVKIT=%DEVKIT:~0,-1%"
if "%~1"=="" (
  echo the folder of the project: jtools-sync.bat PROJECT [--check] 1>&2
  exit /b 2
)
for %%p in ("%~1") do set "PROJECT=%%~fp"
set "CHECK=0"
if "%~2"=="--check" set "CHECK=1"
if not exist "%PROJECT%\jtools.conf" (
  echo %PROJECT% has no jtools.conf: not a project of JToolsFramework's - doc/ADOPTING.md 1>&2
  exit /b 1
)
set "VERSION="
if exist "%DEVKIT%\version" for /f "usebackq delims=" %%v in ("%DEVKIT%\version") do if not defined VERSION set "VERSION=%%v"
if not defined VERSION for /f "tokens=3 delims=<>" %%v in ('findstr /c:"<jtools.version>" "%DEVKIT%\..\conventions\parent\pom.xml"') do if not defined VERSION set "VERSION=%%v"

set "DIFFERS=0"
echo == the managed files (jtools-devkit %VERSION%)
for /f "usebackq eol=# delims=" %%f in ("%DEVKIT%\managed-files.list") do call :managed "%%f"

echo == the framework version
rem the version on the line after jtools-parent's artifactId, in the project's pom - read by findstr, never parsed as
rem a command: its < and > would be redirections
set "USED="
set "AT="
for /f "delims=:" %%n in ('findstr /n /c:"<artifactId>jtools-parent</artifactId>" "%PROJECT%\pom.xml"') do if not defined AT set /a AT=%%n+1
if defined AT for /f "tokens=1* delims=:" %%a in ('findstr /n "^" "%PROJECT%\pom.xml"') do if "%%a"=="%AT%" for /f "tokens=3 delims=<>" %%v in ("%%b") do set "USED=%%v"
if "%USED%"=="%VERSION%" (
  echo    %VERSION%, the devkit's
) else (
  if not defined USED set "USED=(none)"
  set "DIFFERS=1"
  call echo    the project's pom names jtools-parent %%USED%%, the devkit is %VERSION%: change it in %PROJECT%\pom.xml
)

echo == the externals (externals.conf: the project's to change)
setlocal EnableDelayedExpansion
for /f "usebackq eol=# tokens=1* delims==" %%a in ("%DEVKIT%\externals.conf") do (
  set "KEY=%%a"
  set "OURS=%%b"
  set "THEIRS="
  for /f "usebackq eol=# tokens=1* delims==" %%c in ("%PROJECT%\externals.conf") do if "%%c"=="%%a" if not defined THEIRS set "THEIRS=%%d"
  if /i not "!KEY:~0,6!"=="MIRROR" if not "!THEIRS!"=="!OURS!" (
    if not defined THEIRS set "THEIRS=(absent)"
    echo    !KEY!: !THEIRS! -^> !OURS!
    set "DIFFERS=1"
  )
)
for %%d in (!DIFFERS!) do endlocal & set "DIFFERS=%%d"

rem scripts\jtools runs this very sync: it is never replaced by it, only said to differ - copied by hand when wanted
for %%s in (jtools jtools.bat) do if exist "%DEVKIT%\scripts\%%s" (
  fc /b "%DEVKIT%\scripts\%%s" "%PROJECT%\scripts\%%s" >nul 2>&1 || echo == scripts\%%s differs from the devkit's: copy %DEVKIT%\scripts\%%s into %PROJECT%\scripts to take it
)

if "%DIFFERS%"=="0" (
  echo == %PROJECT% is in line with JToolsFramework %VERSION%
  exit /b 0
)
if "%CHECK%"=="1" exit /b 1
echo == read the diff in %PROJECT%, run its scripts\ci.bat, commit there
exit /b 0

:managed
set "FILE=%~1"
set "FILE=%FILE:/=\%"
fc /b "%DEVKIT%\%FILE%" "%PROJECT%\%FILE%" >nul 2>&1 && exit /b 0
set "DIFFERS=1"
if "%CHECK%"=="1" (
  echo    differs: %~1
  exit /b 0
)
for %%d in ("%PROJECT%\%FILE%") do if not exist "%%~dpd" mkdir "%%~dpd"
copy /y "%DEVKIT%\%FILE%" "%PROJECT%\%FILE%" >nul
echo    refreshed: %~1
exit /b 0

