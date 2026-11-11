@echo off
rem Managed by JToolsFramework's devkit (scripts/jtools sync refreshes it): change it in its devkit/, not here.
rem
rem Build (if needed) and run the program on Windows: it serves the page and its API, opens the browser on it, and
rem stops once its last page is closed.
rem
rem   scripts\run.bat
rem   scripts\run.bat --port 9090 --no-browser   see: scripts\run.bat --help
rem   scripts\run.bat --keep-alive               keep serving once the last page is closed (scripts\stop.bat then)
rem   scripts\run.bat --rebuild                  force a rebuild first
rem
rem Any other argument is passed to the program.
rem
setlocal EnableDelayedExpansion

cd /d "%~dp0.."
rem (the project root)
call "%~dp0externals.bat" || exit /b 1
rem (jtools.conf; setup done? it is offered otherwise; the externals first; JAR)
if not defined APP_ID (
  echo jtools.conf names no program ^(APP_ID^): there is nothing to run in this project >&2
  exit /b 1
)

set "REBUILD=0"
set "ARGS="
for %%a in (%*) do (
  if /i "%%~a"=="--rebuild" (
    set "REBUILD=1"
  ) else (
    set "ARGS=!ARGS! %%a"
  )
)

rem Rebuild when asked, when the jar is missing, or when a file of the project is newer than it — what the builds and
rem the tools write left out.
set "NEED_BUILD=%REBUILD%"
if not exist "%JAR%" set "NEED_BUILD=1"
if "%NEED_BUILD%"=="0" (
  powershell -NoProfile -ExecutionPolicy Bypass -Command ^
    "$jar = (Get-Item '%JAR%').LastWriteTimeUtc;" ^
    "$skip = '\\(\.git|target|build|node_modules|\.angular|external|releases|tmp)(\\|$)';" ^
    "$newer = Get-ChildItem -Recurse -File | Where-Object { $_.FullName.Substring($PWD.Path.Length) -notmatch $skip -and $_.LastWriteTimeUtc -gt $jar } | Select-Object -First 1;" ^
    "if ($newer) { exit 1 } else { exit 0 }"
  if errorlevel 1 set "NEED_BUILD=1"
)

if "%NEED_BUILD%"=="1" (
  echo == building %JAR%
  call mvn -q %BUILD_GOALS% -DskipTests || exit /b 1
)

java -jar "%JAR%" %ARGS%
exit /b %errorlevel%
