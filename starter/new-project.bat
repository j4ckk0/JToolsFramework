@echo off
rem Creates a new project on JToolsFramework, from the template beside this script - new-project.sh's twin, for
rem Windows: a Java server holding the business (core, api), an Angular page as its view (frontend), the program and
rem its distributions (app), the devkit's scripts, its externals.conf and a port; then, unless told otherwise, a git
rem repository with its first commit, its externals and a first build. Run through scripts\windows\jtools new (anywhere).
rem The name is the Java class of the business (core) and the program's name: CamelCase, a letter first.
setlocal EnableExtensions DisableDelayedExpansion
set "STARTER=%~dp0"
set "STARTER=%STARTER:~0,-1%"

rem Where its parts are: the unpacked jtools-devkit (a file "version" above this folder), or a clone of the framework.
set "CLONE="
if exist "%STARTER%\..\version" (
  for %%d in ("%STARTER%\..") do set "DEVKIT=%%~fd"
  set "FRONTEND=%STARTER%\frontend"
) else (
  for %%d in ("%STARTER%\..") do set "CLONE=%%~fd"
)
if defined CLONE set "DEVKIT=%CLONE%\devkit"
if defined CLONE set "FRONTEND=%CLONE%\libraries\web"
set "VERSION="
if not defined CLONE for /f "usebackq delims=" %%v in ("%DEVKIT%\version") do if not defined VERSION set "VERSION=%%v"
if defined CLONE for /f "tokens=3 delims=<>" %%v in ('findstr /c:"<jtools.version>" "%CLONE%\conventions\parent\pom.xml"') do if not defined VERSION set "VERSION=%%v"

set "NAME="
set "ID="
set "PORT="
set "DIR="
set "BARE="
set "GIT=1"
set "BUILD=1"
:args
if "%~1"=="" goto :parsed
if "%~1"=="--port" goto :value
if "%~1"=="--id" goto :value
if "%~1"=="--dir" goto :value
if "%~1"=="--bare" goto :value
if "%~1"=="--no-git" (
  set "GIT=0"
  shift
  goto :args
)
if "%~1"=="--no-build" (
  set "BUILD=0"
  shift
  goto :args
)
if "%~1"=="-h" goto :usage
if "%~1"=="--help" goto :usage
set "ARG=%~1"
if "%ARG:~0,1%"=="-" (
  echo unknown option: %~1 1>&2
  goto :usage
)
if defined NAME (
  echo one name only: %~1 1>&2
  goto :usage
)
set "NAME=%~1"
shift
goto :args
:value
if "%~2"=="" (
  echo %~1 takes a value 1>&2
  exit /b 2
)
if "%~1"=="--port" set "PORT=%~2"
if "%~1"=="--id" set "ID=%~2"
if "%~1"=="--dir" set "DIR=%~2"
if "%~1"=="--bare" set "BARE=%~2"
shift
shift
goto :args

:parsed
if not defined NAME goto :usage
echo %NAME%| findstr /r /x /c:"[ABCDEFGHIJKLMNOPQRSTUVWXYZ][ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789]*" >nul || (
  echo %NAME%: a CamelCase name, a letter first - it is a Java class 1>&2
  exit /b 2
)
if defined ID goto :id
rem the name in lower case: a replacement in cmd ignores the case, so A=a turns every A into a
set "ID=%NAME%"
for %%c in (a b c d e f g h i j k l m n o p q r s t u v w x y z) do call set "ID=%%ID:%%c=%%c%%"
:id
echo %ID%| findstr /r /x /c:"[abcdefghijklmnopqrstuvwxyz][abcdefghijklmnopqrstuvwxyz0123456789]*" >nul || (
  echo %ID%: an id of lower-case letters and digits - it is a Java package 1>&2
  exit /b 2
)
if not defined DIR set "DIR=%CD%\%NAME%"
if not "%DIR:~1,1%"==":" if not "%DIR:~0,1%"=="\" set "DIR=%CD%\%DIR%"
for %%d in ("%DIR%") do set "DIR=%%~fd"
if exist "%DIR%" (
  echo %DIR% exists: choose another name or --dir 1>&2
  exit /b 1
)
if not defined BARE goto :port
if not "%BARE:~1,1%"==":" if not "%BARE:~0,1%"=="\" set "BARE=%CD%\%BARE%"
for %%d in ("%BARE%") do set "BARE=%%~fd"
if "%GIT%"=="0" (
  echo --bare wants a git repository: not with --no-git 1>&2
  exit /b 2
)
if exist "%BARE%" (
  echo %BARE% exists: choose another --bare 1>&2
  exit /b 1
)
:port
rem the port, the stubs, the page's files: new-project.list, which new-project.sh reads too
if not defined PORT for /f "usebackq eol=# tokens=1,2" %%a in ("%DEVKIT%\new-project.list") do if "%%a"=="port" set "PORT=%%b"
set "NOT_DIGITS="
for /f "delims=0123456789" %%x in ("%PORT%") do set "NOT_DIGITS=%%x"
if defined NOT_DIGITS goto :badport
if %PORT% LSS 1024 goto :badport
if %PORT% GTR 65535 goto :badport
rem git is optional: without it, the project is made without a repository - unless --bare asked for one
if "%GIT%"=="0" goto :header
where git >nul 2>&1 && goto :header
if defined BARE (
  echo git not found: --bare wants it - Git for Windows, https://git-scm.com/download/win 1>&2
  exit /b 1
)
echo == git not found: the project is made without a repository - git init in it, once Git for Windows is installed
set "GIT=0"
:header

echo == %NAME% (%ID%, port %PORT%) in %DIR%, from JToolsFramework %VERSION%

rem The template, its placeholders filled in - in the files and in the paths. Its texts are UTF-8: so is the console
rem while they are copied.
set "TEMPLATE=%STARTER%\template"
for /f "tokens=2 delims=:." %%c in ('chcp') do set "CODEPAGE=%%c"
chcp 65001 >nul
for /f "delims=" %%f in ('dir /s /b /a-d "%TEMPLATE%"') do call :template "%%f"
chcp %CODEPAGE% >nul

rem What the devkit owns (managed-files.list), then the rest of new-project.list: the stubs, the default externals.conf,
rem the page's dependencies and editor settings.
for /f "usebackq eol=# delims=" %%f in ("%DEVKIT%\managed-files.list") do call :devkit "%%f"
for /f "usebackq eol=# tokens=1,2" %%a in ("%DEVKIT%\new-project.list") do (
  if "%%a"=="stub" call :devkit "%%b"
  if "%%a"=="devkit" call :devkit "%%b"
  if "%%a"=="page" copy /y "%FRONTEND%\%%b" "%DIR%\frontend\%%b" >nul
)
for %%f in (package.json package-lock.json) do (
  move /y "%DIR%\frontend\%%f" "%DIR%\frontend\%%f.jtools" >nul
  call :rename "%DIR%\frontend\%%f.jtools" "%DIR%\frontend\%%f"
  del "%DIR%\frontend\%%f.jtools"
)

cd /d "%DIR%"
if "%GIT%"=="0" goto :build
git init -q -b master || exit /b 1
git config core.hooksPath .githooks || exit /b 1
git add -A || exit /b 1
git commit -q -m "Initial commit: %NAME%, generated by JToolsFramework %VERSION%" -m "A Java server holding the business (core, api), an Angular page as its view (frontend), the program stopping once its last page is closed and its distributions (app); the devkit's scripts and externals." || exit /b 1
echo == a git repository, its first commit made
:build
if "%BUILD%"=="0" goto :bare
rem the externals, from the clone's downloads when it has them: nothing downloaded twice
if defined CLONE if exist "%CLONE%\external\downloads\" set "JTOOLS_DOWNLOADS=%CLONE%\external\downloads"
call scripts\windows\setup.bat || exit /b 1
call scripts\windows\build.bat -q || exit /b 1
echo == built: app\target\%ID%.jar
:bare
if not defined BARE goto :ready
for %%d in ("%BARE%") do if not exist "%%~dpd" mkdir "%%~dpd"
git init -q --bare -b master "%BARE%" || exit /b 1
git remote add origin "%BARE%" || exit /b 1
if "%BUILD%"=="0" (
  echo == %BARE% created; push once built: scripts\windows\build.bat, then git push -u origin master
  goto :ready
)
rem the pre-push hook runs scripts/linux/ci.sh: the first push is the first CI
git push -q -u origin master || exit /b 1
echo == pushed to %BARE%
:ready
echo == %NAME% ready in %DIR%: scripts\windows\run.bat starts it
exit /b 0

:badport
echo %PORT%: a port from 1024 to 65535 1>&2
exit /b 2

:usage
echo   jtools new Bookshelf                              .\Bookshelf, on port 8090, a git repository, built 1>&2
echo   jtools new Bookshelf --port 8094                  the port of its .ini 1>&2
echo   jtools new Bookshelf --id shelf                   the program's id (default: the name in lower case) 1>&2
echo   jtools new Bookshelf --dir C:\work\shelf          elsewhere (a folder that does not exist yet) 1>&2
echo   jtools new Bookshelf --bare C:\git\Bookshelf.git  and a bare repository, pushed to once built 1>&2
echo   jtools new Demo --no-git --no-build               the files alone 1>&2
echo. 1>&2
echo The name is the Java class of the business (core) and the program's name: CamelCase, a letter first. 1>&2
exit /b 2

rem ---- a file of the template, its path and its text filled in
:template
set "SOURCE=%~1"
call set "RELATIVE=%%SOURCE:%TEMPLATE%\=%%"
call set "RELATIVE=%%RELATIVE:__APP_NAME__=%NAME%%%"
call set "RELATIVE=%%RELATIVE:__APP_ID__=%ID%%%"
set "TARGET=%DIR%\%RELATIVE%"
for %%d in ("%TARGET%") do if not exist "%%~dpd" mkdir "%%~dpd"
setlocal DisableDelayedExpansion
> "%TARGET%" (
  for /f "delims=" %%l in ('findstr /n "^" "%SOURCE%"') do (
    set "LINE=%%l"
    setlocal EnableDelayedExpansion
    rem findstr's "N:" taken off: it keeps the empty lines, which a for /f alone would drop
    set "LINE=!LINE:*:=!"
    if defined LINE (
      set "LINE=!LINE:__APP_NAME__=%NAME%!"
      set "LINE=!LINE:__APP_ID__=%ID%!"
      set "LINE=!LINE:__APP_PORT__=%PORT%!"
      set "LINE=!LINE:__JTOOLS_VERSION__=%VERSION%!"
      echo(!LINE!
    ) else echo(
    endlocal
  )
)
endlocal
exit /b 0

rem ---- a file of the devkit's, copied as it is
:devkit
set "FILE=%~1"
set "FILE=%FILE:/=\%"
for %%d in ("%DIR%\%FILE%") do if not exist "%%~dpd" mkdir "%%~dpd"
copy /y "%DEVKIT%\%FILE%" "%DIR%\%FILE%" >nul
exit /b 0

rem ---- the page's package named after the program, as new-project.sh's sed does
:rename
setlocal DisableDelayedExpansion
> "%~2" (
  for /f "delims=" %%l in ('findstr /n "^" "%~1"') do (
    set "LINE=%%l"
    setlocal EnableDelayedExpansion
    set "LINE=!LINE:*:=!"
    if defined LINE (
      set "LINE=!LINE:"name": "jtools-web"="name": "%ID%"!"
      echo(!LINE!
    ) else echo(
    endlocal
  )
)
endlocal
exit /b 0
