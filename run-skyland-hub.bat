@echo off
cd /d "%~dp0"
echo Starting Iskut - Skyland Hub (skybox + entities)...
call gradlew.bat :game-client:run --args="--view=48 --threads=2 --render-scale=0.90 --quality=smooth"
if errorlevel 1 (
  echo.
  echo Iskut did not start. Check the Gradle output above.
)
pause
