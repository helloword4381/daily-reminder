@echo off
if "%JAVA_HOME%"=="" (
    echo ERROR: JAVA_HOME not set. Please set JAVA_HOME to your JDK 17 path.
    exit /b 1
)

set DIRNAME=%~dp0
set APP_HOME=%DIRNAME%
set CLASSPATH=%APP_HOME%\gradle\wrapper\gradle-wrapper.jar

"%JAVA_HOME%\bin\java.exe" -Dorg.gradle.appname=gradlew -classpath "%CLASSPATH%" org.gradle.wrapper.GradleWrapperMain %*
