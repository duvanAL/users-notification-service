@REM ----------------------------------------------------------------------------
@REM Licensed to the Apache Software Foundation (ASF) under one
@REM or more contributor license agreements.  See the NOTICE file
@REM distributed with this work for additional information
@REM regarding copyright ownership.
@REM
@REM Apache Maven Wrapper - descarga Maven automáticamente si no está instalado.
@REM Uso en PowerShell: .\mvnw.cmd clean install
@REM ----------------------------------------------------------------------------
@echo off
setlocal enabledelayedexpansion

set "MAVEN_VERSION=3.9.6"
set "MAVEN_CACHE=%USERPROFILE%\.mvn\wrapper"
set "MAVEN_HOME=%MAVEN_CACHE%\apache-maven-%MAVEN_VERSION%"
set "MAVEN_CMD=%MAVEN_HOME%\bin\mvn.cmd"
set "DIST_URL=https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/%MAVEN_VERSION%/apache-maven-%MAVEN_VERSION%-bin.zip"
set "DIST_ZIP=%MAVEN_CACHE%\mvn-dist-%MAVEN_VERSION%.zip"

if exist "%MAVEN_CMD%" goto :run_maven

echo Descargando Apache Maven %MAVEN_VERSION% en %MAVEN_HOME% ...
if not exist "%MAVEN_CACHE%" mkdir "%MAVEN_CACHE%"
powershell -NoProfile -ExecutionPolicy Bypass -Command "Invoke-WebRequest -Uri '%DIST_URL%' -OutFile '%DIST_ZIP%'"
if errorlevel 1 goto :download_fail
powershell -NoProfile -ExecutionPolicy Bypass -Command "Expand-Archive -Path '%DIST_ZIP%' -DestinationPath '%MAVEN_CACHE%' -Force"
if errorlevel 1 goto :download_fail
del "%DIST_ZIP%" 2>nul
echo Maven %MAVEN_VERSION% descargado correctamente.

:run_maven
"%MAVEN_CMD%" %*
goto :end

:download_fail
echo.
echo ERROR: No se pudo descargar Apache Maven %MAVEN_VERSION%.
echo Opciones alternativas:
echo   1. Instala Maven manualmente desde https://maven.apache.org/download.cgi
echo      y agrégalo al PATH del sistema.
echo   2. Usa IntelliJ IDEA: clic derecho en pom.xml > Maven > Reload Project
echo      y ejecuta los goals desde el panel Maven de IntelliJ.
echo   3. Usa .\mvnw.ps1 clean install  (requiere PowerShell 5+)
exit /b 1

:end
endlocal
