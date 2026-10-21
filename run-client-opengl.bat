@echo off
setlocal
cd /d "%~dp0"
set "SKYVOXEL_RENDERER=opengl"
call "%~dp0gradlew.bat" :game-client:run
if errorlevel 1 (
  echo.
  echo SkyVoxel OpenGL backend failed. The software backend is available via run-client.bat.
  pause
)
