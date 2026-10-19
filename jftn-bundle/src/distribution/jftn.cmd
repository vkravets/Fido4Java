@echo off
setlocal
cd /d "%~dp0"
if exist "%~dp0jre\windows-x64\bin\java.exe" (
    set "JFTN_JAVA=%~dp0jre\windows-x64\bin\java.exe"
) else if exist "%~dp0jre\bin\java.exe" (
    set "JFTN_JAVA=%~dp0jre\bin\java.exe"
) else if defined JAVA_HOME (
    set "JFTN_JAVA=%JAVA_HOME%\bin\java.exe"
) else (
    set "JFTN_JAVA=java.exe"
)
"%JFTN_JAVA%" -Dfile.encoding=UTF-8 -cp "%~dp0scripts;%~dp0lib\*;%~dp0lib\groovy\*;%~dp0plugins\*" org.fidonet.jftn.JFtnRunner %*
exit /b %ERRORLEVEL%
