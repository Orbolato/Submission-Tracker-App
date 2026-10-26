@echo off
set "ROOT=%~dp0"
set "JAR=%ROOT%gradle\wrapper\gradle-wrapper.jar"
if not exist "%JAR%" (
  echo Fetching official Gradle 8.9 wrapper JAR...
  powershell -NoProfile -Command "Invoke-WebRequest -UseBasicParsing 'https://raw.githubusercontent.com/gradle/gradle/v8.9.0/gradle/wrapper/gradle-wrapper.jar' -OutFile '%JAR%'"
  if errorlevel 1 exit /b 1
)
java -classpath "%JAR%" org.gradle.wrapper.GradleWrapperMain %*
