@echo off
rem Managed by JToolsFramework (scripts/jtools-sync.sh refreshes it): change it there, not here.
rem
rem The project's CI, run locally: the build with every test, quiet, the pages' dependencies installed exactly as
rem their package-lock.json has them (npm ci); then scripts\ci.local.bat, when the project has checks of its own.
rem
rem   scripts\ci.bat
rem
cd /d "%~dp0.."
rem (the project root)
call "%~dp0externals.bat" || exit /b 1
rem (jtools.conf; setup done? it is offered otherwise; the externals first)
call mvn -q %BUILD_GOALS% -Dnpm.install=ci || exit /b 1
if exist scripts\ci.local.bat call scripts\ci.local.bat || exit /b 1
exit /b 0
