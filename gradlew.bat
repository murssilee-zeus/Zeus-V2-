@echo off
setlocal
set "APP_HOME=%~dp0"
set "GRADLE_VERSION=8.7"
set "DIST_NAME=gradle-%GRADLE_VERSION%-bin.zip"
set "CACHE_DIR=%USERPROFILE%\.gradle\wrapper\dists\zeus-v2\%GRADLE_VERSION%"
set "GRADLE_DIR=%CACHE_DIR%\gradle-%GRADLE_VERSION%"

if exist "%GRADLE_DIR%\bin\gradle.bat" goto run

if not exist "%CACHE_DIR%" mkdir "%CACHE_DIR%"
set "ZIP=%CACHE_DIR%\%DIST_NAME%"
if exist "%ZIP%" goto extract

echo Downloading Gradle %GRADLE_VERSION%...
powershell -NoProfile -ExecutionPolicy Bypass -Command "Invoke-WebRequest -UseBasicParsing -Uri 'https://services.gradle.org/distributions/%DIST_NAME%' -OutFile '%ZIP%'"
if errorlevel 1 exit /b 1

:extract
powershell -NoProfile -ExecutionPolicy Bypass -Command "Expand-Archive -Force '%ZIP%' '%CACHE_DIR%'"
if errorlevel 1 exit /b 1

:run
call "%GRADLE_DIR%\bin\gradle.bat" --no-daemon %*
exit /b %ERRORLEVEL%
