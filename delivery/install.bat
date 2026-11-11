@echo off
rem Installs JToolsFramework on Windows from this folder - nothing to clone, nothing to build - then, when asked, makes
rem a first project: its folder, its development environment (JDK, Maven, Node.js in its external\), built. install.sh
rem does the same on Linux.
rem
rem   install.bat                                   asks the project's name and folder (a double-click)
rem   install.bat Bookshelf                         the project Bookshelf, beside this folder
rem   install.bat Bookshelf --dir C:\work\shelf     the project in C:\work\shelf (a folder that does not exist yet)
rem   install.bat Bookshelf --port 8094 ...         any other option of jtools new (jtools new --help)
rem   install.bat --no-project                      the framework and jtools alone
rem
rem The framework goes into the local Maven repository (%USERPROFILE%\.m2\repository; M2_REPO says another), jtools
rem into %USERPROFILE%\.local\bin (JTOOLS_BIN says another). Windows 10 or later; a project's repository needs git (Git
rem for Windows), jtools new --no-git makes one without.
setlocal EnableExtensions DisableDelayedExpansion
set "HERE=%~dp0"
set "HERE=%HERE:~0,-1%"
set "VERSION="
for /f "usebackq delims=" %%v in ("%HERE%\VERSION") do if not defined VERSION set "VERSION=%%v"
if "%~1"=="-h" goto :help
if "%~1"=="--help" goto :help
rem no argument: a double-click - it asks, and waits for a key at the end
set "ASK=0"
if "%~1"=="" set "ASK=1"

rem the framework's artifacts: added beside what the repository holds, this version's replaced when already there
set "REPOSITORY=%M2_REPO%"
if not defined REPOSITORY set "REPOSITORY=%USERPROFILE%\.m2\repository"
for /d %%a in ("%HERE%\repository\org\jtools\framework\*") do call :artifact "%%~nxa"
echo == JToolsFramework %VERSION% in %REPOSITORY%

rem jtools: jtools.bat for cmd and PowerShell, jtools for Git Bash
set "BIN=%JTOOLS_BIN%"
if not defined BIN set "BIN=%USERPROFILE%\.local\bin"
if not exist "%BIN%" mkdir "%BIN%"
copy /y "%HERE%\jtools.bat" "%BIN%\jtools.bat" >nul
copy /y "%HERE%\jtools" "%BIN%\jtools" >nul
echo == jtools in %BIN%
set "ONPATH=0"
for %%p in ("%PATH:;=" "%") do if /i "%%~p"=="%BIN%" set "ONPATH=1"
if "%ONPATH%"=="1" goto :project
set "USERPATH="
for /f "tokens=2,*" %%a in ('reg query HKCU\Environment /v Path 2^>nul ^| findstr /i "REG_"') do set "USERPATH=%%b"
set "ANSWER=n"
if "%ASK%"=="1" set /p "ANSWER=Add %BIN% to your PATH, so that jtools works in every new window? [Y/n] "
if /i "%ANSWER%"=="n" goto :nopath
if defined USERPATH (reg add HKCU\Environment /v Path /t REG_EXPAND_SZ /d "%USERPATH%;%BIN%" /f >nul) else (reg add HKCU\Environment /v Path /t REG_EXPAND_SZ /d "%BIN%" /f >nul)
rem setx tells the open programs that the environment changed: the windows opened from now on see the new PATH
setx JTOOLS_BIN "%BIN%" >nul
echo == %BIN% added to your PATH: jtools works in the windows you open from now on
goto :project
:nopath
echo    %BIN% is not on your PATH: add it - Start, "Edit environment variables for your account", Path, New -
echo    or, in one window: set "PATH=%BIN%;%%PATH%%"

:project
set "PATH=%BIN%;%PATH%"
rem by default beside this folder, never inside it
set "BASE=%CD%"
if /i "%CD%"=="%HERE%" for %%d in ("%HERE%\..") do set "BASE=%%~fd"
if "%~1"=="--no-project" goto :ready
if "%ASK%"=="1" goto :ask
set "HASDIR=0"
for %%a in (%*) do if "%%~a"=="--dir" set "HASDIR=1"
if "%HASDIR%"=="1" goto :given
if /i "%BASE%"=="%CD%" goto :given
call "%BIN%\jtools.bat" new %* --dir "%BASE%\%~1"
goto :end
:given
call "%BIN%\jtools.bat" new %*
goto :end
:ask
set "NAME="
set /p "NAME=The project's name (CamelCase, a letter first; empty: no project now): "
if not defined NAME goto :ready
set "DIR="
set /p "DIR=The folder its files go in [%BASE%\%NAME%]: "
if not defined DIR set "DIR=%BASE%\%NAME%"
call "%BIN%\jtools.bat" new "%NAME%" --dir "%DIR%"
goto :end
:ready
echo == ready: jtools new NAME [--dir DIR] makes a project (jtools new --help)
:end
set "STATUS=%ERRORLEVEL%"
if "%ASK%"=="1" pause
exit /b %STATUS%

:help
for /f "tokens=1,* delims= " %%a in ('findstr /b /c:"rem   install" "%~f0"') do echo %%b
exit /b 0

:artifact
if not exist "%HERE%\repository\org\jtools\framework\%~1\%VERSION%\" exit /b 0
set "TARGET=%REPOSITORY%\org\jtools\framework\%~1\%VERSION%"
if exist "%TARGET%\" echo == %~1 %VERSION% was in %REPOSITORY% already: replaced
if not exist "%TARGET%\" mkdir "%TARGET%"
copy /y "%HERE%\repository\org\jtools\framework\%~1\%VERSION%\*" "%TARGET%\" >nul
exit /b 0
