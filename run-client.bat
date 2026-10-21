@echo off
setlocal
cd /d "%~dp0"
if "%~1"=="" (
  call "%~dp0gradlew.bat" :game-client:run
) else (
  call "%~dp0gradlew.bat" :game-client:run --args="%*"
)
if errorlevel 1 (
  echo.
  echo Iskut could not start. Check the Java version and error message above.
  pause
)
