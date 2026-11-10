@echo off
rem Managed by JToolsFramework (scripts/jtools-sync.sh refreshes it): change it there, not here.
rem
rem The build, every test included: mvn with jtools.conf's BUILD_GOALS ("clean package" unless it says otherwise).
rem
rem   scripts\build.bat
rem   scripts\build.bat -DskipTests        extra arguments are passed to mvn
rem
cd /d "%~dp0.."
rem (the project root)
call "%~dp0externals.bat" || exit /b 1
rem (jtools.conf; setup done? it is offered otherwise; the externals first)
call mvn %BUILD_GOALS% %*
exit /b %errorlevel%
