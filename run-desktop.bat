@echo off
title VietBus Card - Desktop Swing Client
echo ====================================================
echo  Khoi dong VietBus Card Desktop App (Java Swing)
echo ====================================================
cd /d "%~dp0"

set "VM_ARGS=--add-opens java.base/java.security=ALL-UNNAMED --add-opens java.base/java.math=ALL-UNNAMED --add-opens java.base/java.util=ALL-UNNAMED --add-opens java.base/java.util.concurrent.atomic=ALL-UNNAMED --add-opens java.base/java.lang=ALL-UNNAMED --add-opens java.base/java.nio=ALL-UNNAMED"

if exist "desktop\target\vietbus-desktop.jar" (
    echo [INFO] Dang chay tu file JAR da dong goi: desktop\target\vietbus-desktop.jar ...
    java %VM_ARGS% -jar desktop\target\vietbus-desktop.jar
) else (
    echo [INFO] Chua co file JAR, tien hanh chay bang Maven...
    cd desktop
    set "MAVEN_OPTS=%VM_ARGS%"
    set "MVN_BIN=mvn"
    where mvn >nul 2>nul
    if errorlevel 1 (
        if exist "%USERPROFILE%\apache-maven-3.9.16-bin\apache-maven-3.9.16\bin\mvn.cmd" (
            set "MVN_BIN=%USERPROFILE%\apache-maven-3.9.16-bin\apache-maven-3.9.16\bin\mvn.cmd"
        )
    )
    call "%MVN_BIN%" exec:java -Dexec.mainClass="com.buscard.desktop.VietBusApp" -Dexec.cleanupDaemonThreads=false
)

echo.
echo [INFO] Ung dung Desktop da dong.
pause
