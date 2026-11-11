@echo off
rem Run ${jtools.app.name} with the bundled JRE, in a console (messages, --help). Arguments are passed to the program.
rem ${jtools.app.name}.exe starts the same program without a console.
"%~dp0jre\bin\java.exe" -jar "%~dp0${jtools.app.id}.jar" %*
