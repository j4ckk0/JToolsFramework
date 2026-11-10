@echo off
rem Managed by JToolsFramework (scripts/jtools-sync.sh refreshes it): change it there, not here.
rem
rem Called by the scripts, from the project root. Reads jtools.conf — what the scripts know of this project — and
rem makes sure scripts\setup.bat has been run: every tool externals.conf names is deployed in external\ (offered with
rem a 30 s timeout, No by default: the CI does not hang). Then puts the externals in front: external\jdk as JAVA_HOME,
rem its java.exe, Maven's mvn.cmd, and Gradle's and Node's when deployed. No setlocal: the caller keeps what is set here.
rem
rem Sets APP_ID, APP_NAME, APP_MODULE, BUILD_GOALS (jtools.conf) and JAR, the program's jar when APP_ID names one.
rem
if not exist jtools.conf (
  echo no jtools.conf in %CD%: not a project of JToolsFramework's >&2
  exit /b 1
)
set "APP_ID="
set "APP_NAME="
set "APP_MODULE=app"
set "BUILD_GOALS=clean package"
for /f "usebackq eol=# tokens=1* delims==" %%a in ("jtools.conf") do set "%%a=%%b"

set "EXT=%CD%\external"
call :missing
if defined MISSING (
  echo external\ is not set up: missing%MISSING% ^(scripts\setup.bat deploys them, see README: Externals^) >&2
  choice /c YN /n /t 30 /d N /m "Run scripts\setup.bat now? [Y/N] "
  if errorlevel 2 exit /b 1
  call "%~dp0setup.bat" || exit /b 1
  call :missing
  if defined MISSING (
    echo still missing%MISSING% >&2
    exit /b 1
  )
)
set "JAVA_HOME=%EXT%\jdk"
set "PATH=%EXT%\jdk\bin;%EXT%\maven\bin;%PATH%"
if exist "%EXT%\gradle\bin" set "PATH=%EXT%\gradle\bin;%PATH%"
if exist "%EXT%\node" set "PATH=%EXT%\node;%PATH%"
set "JAR="
if defined APP_ID set "JAR=%APP_MODULE%\target\%APP_ID%.jar"
exit /b 0

rem ---- what setup deploys and is not there, in MISSING (undefined when all is there); a tool externals.conf does
rem      not name is not wanted
:missing
set "MISSING="
call :wanted JDK_WINDOWS && if not exist "%EXT%\jdk\bin\java.exe" set "MISSING=%MISSING% jdk\"
call :wanted MAVEN_WINDOWS && if not exist "%EXT%\maven\bin\mvn.cmd" set "MISSING=%MISSING% maven\"
call :wanted GRADLE && if not exist "%EXT%\gradle\bin\gradle.bat" set "MISSING=%MISSING% gradle\"
call :wanted NODE_WINDOWS && if not exist "%EXT%\node\node.exe" set "MISSING=%MISSING% node\"
call :wanted JRE_LINUX && if not exist "%EXT%\jre-linux.tar.gz" set "MISSING=%MISSING% jre-linux.tar.gz"
call :wanted JRE_WINDOWS && if not exist "%EXT%\jre-windows.zip" set "MISSING=%MISSING% jre-windows.zip"
exit /b 0

:wanted
findstr /b /c:"%~1=" externals.conf >nul
exit /b %errorlevel%
