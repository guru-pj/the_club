@echo off
:: ============================================================
:: 🏋️‍♂️ The Club — Project Setup & Management Script (Windows)
:: Automates Maven build, Docker image creation, and container control.
:: Usage:
::   project-setup.bat up        -> Build and start all containers with logs
::   project-setup.bat down      -> Stop and remove all containers
::   project-setup.bat rebuild   -> Clean, rebuild, and start everything
:: ============================================================

setlocal enabledelayedexpansion

:: Always run from the project root
cd /d "%~dp0"

:: Colors
set "GREEN="
set "YELLOW="
set "RED="

echo 🏋️ Starting The Club setup script...

:: Check for Docker
where docker >nul 2>nul
if %errorlevel% neq 0 (
    echo ❌ Docker is not installed or not in PATH.
    exit /b 1
)

:: Check for Maven
where mvn >nul 2>nul
if %errorlevel% neq 0 (
    echo ❌ Maven is not installed or not in PATH.
    exit /b 1
)

:: Create .env from the template on first run
if not exist .env (
    echo 📝 No .env found, copying enviroinment_variables.env to .env
    copy enviroinment_variables.env .env >nul
)

:: Generate keys and passwords on first run (needs bash + openssl: Git for Windows' Git Bash or WSL)
if not exist secrets\theclub.jwt.private-key (
    echo 🔐 No secrets found, generating .\secrets ...
    where bash >nul 2>nul
    if !errorlevel! neq 0 (
        echo ❌ bash was not found. Install Git for Windows ^(Git Bash^) or WSL, then run: bash scripts/generate-secrets.sh
        exit /b 1
    )
    bash scripts/generate-secrets.sh
    if !errorlevel! neq 0 exit /b 1
)

:: Parse command arguments
if "%1"=="" (
    echo Usage: project-setup.bat {up^|down^|rebuild}
    exit /b 1
)

if /i "%1"=="up" (
    call :build_services
    call :build_docker
    call :start_containers
    exit /b
)

if /i "%1"=="down" (
    call :stop_containers
    exit /b
)

if /i "%1"=="rebuild" (
    call :rebuild_all
    exit /b
)

echo Unknown command: %1
echo Usage: project-setup.bat {up^|down^|rebuild}
exit /b 1

:: Function: Build all services
:: The root pom builds common-lib first, then every service that depends on it.
:build_services
echo 🔧 Building all microservices with Maven...
call mvn clean install -DskipTests
if %errorlevel% neq 0 exit /b 1
goto :eof

:: Function: Build Docker images
:build_docker
echo 🐳 Building Docker images...
docker compose build
goto :eof

:: Function: Start containers
:start_containers
echo 🚀 Starting all containers...
docker compose up -d
echo 📜 Showing live logs... (Press CTRL + C to stop viewing)
docker compose logs -f
goto :eof

:: Function: Stop containers
:stop_containers
echo 🛑 Stopping and removing all containers...
docker compose down
goto :eof

:: Function: Full rebuild
:rebuild_all
echo 🔁 Full rebuild initiated...
call :build_services
call :build_docker
call :start_containers
goto :eof

