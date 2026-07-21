@echo off
setlocal enabledelayedexpansion
set DIRNAME=%~dp0
set APP_HOME=%DIRNAME%
set WRAPPER_JAR=%APP_HOME%\gradle\wrapper\gradle-wrapper.jar
set WRAPPER_PROPS=%APP_HOME%\gradle\wrapper\gradle-wrapper.properties

:: Find Java
if "%JAVA_HOME%"=="" (
    set JAVACMD=java
) else (
    set JAVACMD="%JAVA_HOME%\bin\java"
)

:: Bootstrap: download wrapper jar if not present
if not exist "%WRAPPER_JAR%" (
    echo Downloading Gradle wrapper...
    if exist "%WRAPPER_PROPS%" (
        for /f "tokens=1,* delims==" %%a in ('findstr "^distributionUrl" "%WRAPPER_PROPS%"') do set "DIST_URL=%%b"
    )
    if "!DIST_URL!"=="" set "DIST_URL=https://services.gradle.org/distributions/gradle-8.7-bin.zip"
    set "DIST_URL=!DIST_URL:\=!"
    
    :: Create temp dir and download
    set TMP_DIR=%TEMP%\gradle-bootstrap
    mkdir "%TMP_DIR%" 2>nul
    powershell -Command "& {Invoke-WebRequest -UseBasicParsing '!DIST_URL!' -OutFile '%TMP_DIR%\gradle.zip'}"
    
    :: Extract Gradle distribution and use it directly when wrapper jar is absent
    powershell -Command "& {Add-Type -A 'System.IO.Compression.FileSystem'; [IO.Compression.ZipFile]::ExtractToDirectory('%TMP_DIR%\gradle.zip', '%TMP_DIR%\extracted')}"
    for /d %%d in ("%TMP_DIR%\extracted\gradle-*") do set "BOOTSTRAP_GRADLE=%%d\bin\gradle.bat"
    if exist "!BOOTSTRAP_GRADLE!" (
        call "!BOOTSTRAP_GRADLE!" %*
        set "EXIT_CODE=!ERRORLEVEL!"
        rmdir /s /q "%TMP_DIR%" 2>nul
        exit /b !EXIT_CODE!
    )

    :: Fallback for environments that provide gradle-wrapper.jar in the distribution
    for /r "%TMP_DIR%\extracted" %%f in (gradle-wrapper.jar) do copy "%%f" "%WRAPPER_JAR%" 2>nul
    
    :: Cleanup
    rmdir /s /q "%TMP_DIR%" 2>nul
    
    if not exist "%WRAPPER_JAR%" (
        echo ERROR: Failed to download Gradle wrapper
        exit /b 1
    )
    echo Wrapper jar ready.
)

%JAVACMD% -Dorg.gradle.appname=gradlew -classpath "%WRAPPER_JAR%" org.gradle.wrapper.GradleWrapperMain %*
if errorlevel 1 exit /b 1
