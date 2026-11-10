@ECHO OFF
SETLOCAL

SET "BASE_DIR=%~dp0"
SET "BASE_DIR=%BASE_DIR:~0,-1%"
SET "WRAPPER_DIR=%BASE_DIR%\.mvn\wrapper"
SET "WRAPPER_JAR=%WRAPPER_DIR%\maven-wrapper.jar"
SET "WRAPPER_PROPERTIES=%WRAPPER_DIR%\maven-wrapper.properties"

IF EXIST "%WRAPPER_JAR%" GOTO runWrapper

FOR /F "tokens=1,* delims==" %%A IN ('FINDSTR /B "wrapperUrl=" "%WRAPPER_PROPERTIES%"') DO SET "WRAPPER_URL=%%B"
IF "%WRAPPER_URL%"=="" (
  ECHO wrapperUrl is not configured in %WRAPPER_PROPERTIES% 1>&2
  EXIT /B 1
)

IF NOT EXIST "%WRAPPER_DIR%" MKDIR "%WRAPPER_DIR%"
powershell -NoProfile -ExecutionPolicy Bypass -Command "Invoke-WebRequest -UseBasicParsing -Uri '%WRAPPER_URL%' -OutFile '%WRAPPER_JAR%'"
IF ERRORLEVEL 1 EXIT /B 1

:runWrapper
java -classpath "%WRAPPER_JAR%" -Dmaven.multiModuleProjectDirectory="%BASE_DIR%" org.apache.maven.wrapper.MavenWrapperMain %*
EXIT /B %ERRORLEVEL%
