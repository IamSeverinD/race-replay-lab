@echo off
setlocal
cd /d "%~dp0"

echo ============================================================
echo RACE REPLAY LAB
echo ============================================================
echo.

set "JAVA_CMD="

if defined JAVA_HOME (
    if exist "%JAVA_HOME%\bin\java.exe" (
        set "JAVA_CMD=%JAVA_HOME%\bin\java.exe"
    )
)

if not defined JAVA_CMD (
    for /f "delims=" %%J in ('where java 2^>nul') do (
        if not defined JAVA_CMD (
            set "JAVA_CMD=%%J"
        )
    )
)

if not defined JAVA_CMD (
    echo FEHLER: Java JDK 25 wurde nicht gefunden.
    echo.
    echo Bitte Java 25 installieren und JAVA_HOME setzen.
    echo.
    pause
    exit /b 1
)

powershell -NoProfile -Command "$java = '%JAVA_CMD%'; $line = ^& $java -version 2^>^&1 ^| Select-Object -First 1; Write-Host $line; if ($line -match '\b25\.') { exit 0 } else { exit 1 }"

if errorlevel 1 (
    echo.
    echo FEHLER: Gefunden wurde nicht Java 25.
    echo JAVA_HOME=%JAVA_HOME%
    echo.
    pause
    exit /b 1
)

echo.
echo Starte Race Replay Lab ...
echo.

call mvnw.cmd javafx:run
set "EXIT_CODE=%ERRORLEVEL%"

echo.

if "%EXIT_CODE%"=="0" (
    echo Race Replay Lab wurde ordnungsgemaess beendet.
) else (
    echo Race Replay Lab wurde mit einem Fehler beendet.
    echo Fehlercode: %EXIT_CODE%
)

echo.
pause
exit /b %EXIT_CODE%
