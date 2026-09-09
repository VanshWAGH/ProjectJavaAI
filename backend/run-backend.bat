@echo off
setlocal enabledelayedexpansion

if exist .env (
    echo Loading environment variables from .env...
    for /f "usebackq tokens=1,* delims==" %%A in (".env") do (
        set "line=%%A"
        if not "!line:~0,1!"=="#" (
            set "%%A=%%B"
        )
    )
)

echo Starting Spring Boot backend on http://localhost:8080...
mvnw.cmd spring-boot:run
