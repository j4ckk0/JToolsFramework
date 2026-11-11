@echo off
rem Managed by JToolsFramework (scripts/jtools-sync.sh refreshes it): change it there, not here.
rem
rem Build the self-contained distributions (jar + a runtime jlink trims to what the program needs + launchers;
rem <name>.exe without a console), into releases\:
rem   releases\<id>-<version>-linux.tar.gz, -windows.zip, and -macos.tar.gz when externals.conf names JDK_MACOS
rem
rem   scripts\package.bat
rem   scripts\package.bat -DskipTests      extra arguments are passed to mvn
rem
rem The runtimes are linked from the JDKs of external\ (scripts\setup.bat deploys them). The version is the root pom's <revision>.
rem The releases of the previous versions are removed once the new one is built.
rem
cd /d "%~dp0.."
rem (the project root)
call "%~dp0externals.bat" || exit /b 1
rem (jtools.conf; setup done? it is offered otherwise; the externals first)
if not defined APP_ID (
  echo jtools.conf names no program ^(APP_ID^): there is nothing to package in this project >&2
  exit /b 1
)

call mvn %BUILD_GOALS% -Pdist %* || exit /b 1

for /f "delims=" %%l in ('findstr /r /c:"<revision>.*</revision>" pom.xml') do if not defined VERSION_LINE set "VERSION_LINE=%%l"
if not defined VERSION_LINE (
  echo pom.xml has no ^<revision^>: the scripts read the version there >&2
  exit /b 1
)
set "VERSION=%VERSION_LINE:*<revision>=%"
set "VERSION=%VERSION:</revision>=%"
for %%a in (releases\%APP_ID%-*-windows.zip releases\%APP_ID%-*-linux.tar.gz releases\%APP_ID%-*-macos.tar.gz) do call :previous "%%a"

rem What every distribution must carry: the jar, its launchers, the .ini and the runtime.
set "BASE=%APP_ID%-%VERSION%"
call :check "releases\%BASE%-windows.zip" %APP_ID%.jar "%APP_NAME%.exe" %APP_ID%.bat %APP_ID%.ini README.md jre/bin/java.exe jre/release || exit /b 1
call :check "releases\%BASE%-linux.tar.gz" %APP_ID%.jar %APP_ID%.sh %APP_ID%.ini README.md jre/bin/java jre/release || exit /b 1
if exist external\jdk-macos.tar.gz call :check "releases\%BASE%-macos.tar.gz" %APP_ID%.jar %APP_ID%.sh %APP_ID%.ini README.md jre/bin/java jre/release || exit /b 1
echo == releases\ checked: each with its runtime and launchers
exit /b 0

rem ---- one archive (%1) and the files it must carry (%2...)
:check
if not exist "%~1" ( echo no %~1: the build did not make it >&2 & exit /b 1 )
set "ARCHIVE=%~1"
:checkNext
shift
if "%~1"=="" exit /b 0
tar -tf "%ARCHIVE%" | findstr /x /c:"%BASE%/%~1" >nul || ( echo %ARCHIVE%: no %~1 >&2 & exit /b 1 )
goto checkNext

rem ---- an archive of another version: removed
:previous
echo %~nx1 | findstr /c:"-%VERSION%-" >nul && exit /b 0
del /q "%~1"
echo == %~1 removed ^(previous release^)
exit /b 0
