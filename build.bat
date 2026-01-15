@echo off
setlocal

echo ==========================================
echo      PastureSong Plugin Build Script
echo ==========================================
echo.

:: 1. Check for Java
java -version >nul 2>&1
if %errorlevel% neq 0 (
    echo [ERROR] Java is not installed or not in PATH!
    echo Please install JDK 21 and make sure 'java' command works.
    pause
    exit /b
)

:: 2. Check for Maven in PATH or Specific Location
set "MAVEN_HOME_CUSTOM=C:\apache-maven-3.9.11-bin\apache-maven-3.9.11"

if exist "%MAVEN_HOME_CUSTOM%\bin\mvn.cmd" (
    echo [INFO] Found Maven at custom path: %MAVEN_HOME_CUSTOM%
    call "%MAVEN_HOME_CUSTOM%\bin\mvn.cmd" clean install
    goto :end
)

call mvn -v >nul 2>&1
if %errorlevel% equ 0 (
    echo [INFO] Found Maven in PATH.
    call mvn clean install
    goto :end
)

:: 3. If not in PATH, ask user
echo [WARN] Maven 'mvn' command not found in PATH or custom location.
echo.
echo Please unzip your 'apache-maven-3.9.11-bin.zip' first.
echo Then, find the folder 'apache-maven-3.9.11' (it should contain a 'bin' folder).
echo.
set /p MAVEN_DIR="> Drag and drop the unzipped 'apache-maven-3.9.11' folder here: "

:: Remove quotes if present
set MAVEN_DIR=%MAVEN_DIR:"=%

if exist "%MAVEN_DIR%\bin\mvn.cmd" (
    echo [INFO] Found Maven at: %MAVEN_DIR%
    call "%MAVEN_DIR%\bin\mvn.cmd" clean install
) else (
    echo.
    echo [ERROR] Could not find '\bin\mvn.cmd' in that folder.
    echo Please make sure you selected the correct directory.
)

:end
echo.
echo ==========================================
echo Build process finished.
if exist "target\PastureSong-1.0-SNAPSHOT.jar" (
    echo [SUCCESS] Jar found at target\PastureSong-1.0-SNAPSHOT.jar
    echo Copying to plugins folder...
    copy /Y "target\PastureSong-1.0-SNAPSHOT.jar" "..\plugins\PastureSong-1.0-SNAPSHOT.jar"
)
echo ==========================================
pause