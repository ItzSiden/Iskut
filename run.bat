@echo off
setlocal
cd /d "%~dp0"
call "%~dp0gradlew.bat" :game-client:run --args="--view=48 --threads=2 --render-scale=1.0"
if errorlevel 1 (
  echo.
  echo Iskut could not start. Check the error output above.
  pause
)
