@echo off
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
        for /f "tokens=2 delims==" %%a in ('findstr "^distributionUrl" "%WRAPPER_PROPS%"') do set DIST_URL=%%a
    )
    if "%DIST_URL%"=="" set DIST_URL=https://services.gradle.org/distributions/gradle-8.7-bin.zip
    set DIST_URL=%DIST_URL:\=%
    
    :: Create temp dir and download
    set TMP_DIR=%TEMP%\gradle-bootstrap
    mkdir "%TMP_DIR%" 2>nul
    powershell -Command "& {Invoke-WebRequest -UseBasicParsing '%DIST_URL%' -OutFile '%TMP_DIR%\gradle.zip'}"
    
    :: Extract wrapper jar
    powershell -Command "& {Add-Type -A 'System.IO.Compression.FileSystem'; [IO.Compression.ZipFile]::ExtractToDirectory('%TMP_DIR%\gradle.zip', '%TMP_DIR%\extracted')}"
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
