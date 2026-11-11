@echo off
rem Brings a project in line with the devkit this script belongs to - jtools-sync.sh's twin, for Windows: refreshes the
rem files the devkit owns in it (managed-files.list: the scripts, the pre-push hook, .gitattributes) and says what else
rem differs - the framework version its pom names, the externals' versions, its scripts\windows\jtools - without changing
rem those, which are the project's to decide. Commits nothing. Run by the project's scripts\windows\jtools:
rem   scripts\windows\jtools sync            refresh the managed files, report the rest
rem   scripts\windows\jtools sync --check    report only; exit 1 when anything differs (a project's CI)
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

rem 1.3's layout, scripts\ alone, moved to 1.4's (moved-files.list)
if exist "%DEVKIT%\moved-files.list" for /f "usebackq eol=# tokens=1,2" %%a in ("%DEVKIT%\moved-files.list") do call :moved "%%a" "%%b"

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

rem scripts\windows\jtools runs this very sync: it is never replaced by it, only said to differ - copied by hand when
rem wanted; likewise its Linux twin, scripts\linux\jtools
for %%s in (scripts\linux\jtools scripts\windows\jtools.bat) do if exist "%DEVKIT%\%%s" if exist "%PROJECT%\%%s" (
  fc /b "%DEVKIT%\%%s" "%PROJECT%\%%s" >nul 2>&1 || echo == %%s differs from the devkit's: copy %DEVKIT%\%%s into %PROJECT%\%%s to take it
)

if "%DIFFERS%"=="0" (
  echo == %PROJECT% is in line with JToolsFramework %VERSION%
  exit /b 0
)
if "%CHECK%"=="1" exit /b 1
echo == read the diff in %PROJECT%, run its scripts\windows\ci.bat, commit there
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

rem ---- a file of 1.3's layout, moved to 1.4's: the devkit's own copy removed (the refresh put the new one in place),
rem ---- the project's own extra moved, the stub put in place beside the old one, which may be the one running
:moved
set "OLD=%~1"
set "OLD=%OLD:/=\%"
set "NEW=%~2"
set "NEW=%NEW:/=\%"
if not exist "%PROJECT%\%OLD%" exit /b 0
set "DIFFERS=1"
if "%CHECK%"=="1" (
  echo    to move: %~1 -^> %~2
  exit /b 0
)
for %%d in ("%PROJECT%\%NEW%") do if not exist "%%~dpd" mkdir "%%~dpd"
set "KIND=managed"
echo %~2| findstr /e /c:"/jtools" /c:"/jtools.bat" >nul && set "KIND=stub"
echo %~2| findstr /c:".local." >nul && set "KIND=local"
if "%KIND%"=="stub" goto :stub
if "%KIND%"=="local" goto :local
del "%PROJECT%\%OLD%"
echo    moved: %~1 -^> %~2
exit /b 0
:local
if exist "%PROJECT%\%NEW%" (
  echo    %~1 and %~2 are both there: keep one
  exit /b 0
)
move "%PROJECT%\%OLD%" "%PROJECT%\%NEW%" >nul
echo    moved: %~1 -^> %~2
exit /b 0
:stub
if not exist "%PROJECT%\%NEW%" copy /y "%DEVKIT%\%NEW%" "%PROJECT%\%NEW%" >nul
echo    %~2 in place; remove %~1 by hand - it may be the one running this sync
exit /b 0
