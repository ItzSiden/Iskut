@echo off
cd /d "%~dp0"
echo Starting Iskut enhanced world (mipmapped textures, smooth lighting, batched map)...
call gradlew.bat :game-client:run --args="--view=64 --threads=2 --render-scale=0.90 --quality=smooth"
if errorlevel 1 (
  echo.
  echo Iskut did not start. Check the build output above.
)
pause
