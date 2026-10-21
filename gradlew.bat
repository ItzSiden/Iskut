@echo off
setlocal EnableExtensions
set "GRADLE_VERSION=9.8.1"
set "GRADLE_CACHE=%USERPROFILE%\.gradle\skyvoxel-bootstrap"
set "GRADLE_HOME=%GRADLE_CACHE%\gradle-%GRADLE_VERSION%"
set "GRADLE_ZIP=%GRADLE_CACHE%\gradle-%GRADLE_VERSION%-bin.zip"

if not exist "%GRADLE_HOME%\bin\gradle.bat" goto :download

goto :run

:download
echo Gradle %GRADLE_VERSION% is not cached. Downloading it once...
echo This needs an internet connection and around 130 MB of download space.
if not exist "%GRADLE_CACHE%" mkdir "%GRADLE_CACHE%"
if errorlevel 1 goto :failed

powershell -NoProfile -ExecutionPolicy Bypass -Command "$ErrorActionPreference='Stop'; $ProgressPreference='SilentlyContinue'; Invoke-WebRequest -Uri 'https://services.gradle.org/distributions/gradle-%GRADLE_VERSION%-bin.zip' -OutFile $env:GRADLE_ZIP; Expand-Archive -LiteralPath $env:GRADLE_ZIP -DestinationPath $env:GRADLE_CACHE -Force; Remove-Item -LiteralPath $env:GRADLE_ZIP -Force"
if errorlevel 1 goto :failed
if not exist "%GRADLE_HOME%\bin\gradle.bat" goto :failed

:run
call "%GRADLE_HOME%\bin\gradle.bat" %*
exit /b %errorlevel%

:failed
echo.
echo Could not bootstrap Gradle. Check the internet connection and PowerShell availability.
echo You can also install Gradle %GRADLE_VERSION% manually and run gradle :game-client:run from this folder.
exit /b 1
