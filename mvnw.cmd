@echo off
:: =====================================================
:: Maven launcher — CRM System
:: Uses Maven from PATH; falls back to a local Maven 3.9.6
:: install for this machine. No hardcoded JDK path.
:: =====================================================
where mvn >nul 2>nul
if %ERRORLEVEL% EQU 0 (
    call mvn %*
    exit /b %ERRORLEVEL%
)

set "MAVEN_HOME=%USERPROFILE%\.gemini\maven\apache-maven-3.9.6"
"%MAVEN_HOME%\bin\mvn.cmd" %*
