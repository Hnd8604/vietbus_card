@echo off
title VietBus Backend - Spring Boot Server (Port 8080)
echo ====================================================
echo  Khoi dong Backend Spring Boot Server (cong 8080)
echo ====================================================
cd /d "%~dp0"

if exist "backend\target\backend-1.0.0-SNAPSHOT.jar" (
    echo [INFO] Dang chay tu file JAR da dong goi: backend\target\backend-1.0.0-SNAPSHOT.jar ...
    java -jar backend\target\backend-1.0.0-SNAPSHOT.jar
) else (
    echo [INFO] Chua co file JAR, tien hanh chay bang Maven...
    cd backend
    set "MVN_BIN=mvn"
    where mvn >nul 2>nul
    if errorlevel 1 (
        if exist "%USERPROFILE%\apache-maven-3.9.16-bin\apache-maven-3.9.16\bin\mvn.cmd" (
            set "MVN_BIN=%USERPROFILE%\apache-maven-3.9.16-bin\apache-maven-3.9.16\bin\mvn.cmd"
        )
    )
    call "%MVN_BIN%" spring-boot:run
)

echo.
echo [INFO] Tien trinh da dung.
pause
