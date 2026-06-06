@REM ----------------------------------------------------------------------------
@REM Maven Wrapper bootstrap script (Windows).
@REM Mirrors ./mvnw: downloads Maven if not already cached, then runs it.
@REM ----------------------------------------------------------------------------
@echo off
setlocal

set "BASE_DIR=%~dp0"
if "%BASE_DIR:~-1%"=="\" set "BASE_DIR=%BASE_DIR:~0,-1%"

set "PROPS=%BASE_DIR%\.mvn\wrapper\maven-wrapper.properties"
if not exist "%PROPS%" (
  echo ERROR: Cannot find %PROPS% 1>&2
  exit /b 1
)

@REM Parse distributionUrl=... from the properties file
set "DIST_URL="
for /f "usebackq tokens=1,* delims==" %%A in ("%PROPS%") do (
  if "%%A"=="distributionUrl" set "DIST_URL=%%B"
)
if not defined DIST_URL (
  echo ERROR: distributionUrl not found in %PROPS% 1>&2
  exit /b 1
)

@REM Derive the cache directory name from the zip filename (without extension)
for %%I in ("%DIST_URL%") do set "DIST_NAME=%%~nI"
if not defined MAVEN_USER_HOME set "MAVEN_USER_HOME=%USERPROFILE%\.m2"
set "DIST_DIR=%MAVEN_USER_HOME%\wrapper\dists\%DIST_NAME%"
set "MVN_BIN=%DIST_DIR%\bin\mvn.cmd"

if not exist "%MVN_BIN%" (
  echo Downloading Maven: %DIST_URL%
  powershell -NoProfile -ExecutionPolicy Bypass -Command "$ErrorActionPreference='Stop'; $ProgressPreference='SilentlyContinue'; $url='%DIST_URL%'; $dist='%DIST_DIR%'; $zip=$dist+'.zip'; New-Item -ItemType Directory -Force -Path $dist | Out-Null; Invoke-WebRequest -Uri $url -OutFile $zip; $tmp=Join-Path ([IO.Path]::GetTempPath()) ([IO.Path]::GetRandomFileName()); New-Item -ItemType Directory -Force -Path $tmp | Out-Null; Expand-Archive -Path $zip -DestinationPath $tmp -Force; $inner=Get-ChildItem -Path $tmp -Directory | Select-Object -First 1; Copy-Item -Path (Join-Path $inner.FullName '*') -Destination $dist -Recurse -Force; Remove-Item -Recurse -Force $tmp,$zip"
  if errorlevel 1 (
    echo ERROR: Maven download failed: %DIST_URL% 1>&2
    exit /b 1
  )
  echo Maven installed to %DIST_DIR%
)

"%MVN_BIN%" -f "%BASE_DIR%\pom.xml" %*
exit /b %ERRORLEVEL%
