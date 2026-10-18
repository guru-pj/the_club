#!/bin/bash

# ============================================================
# 🏋️‍♂️ The Club — Project Setup & Management Script
# Automates Maven build, Docker image creation, and container control.
# Usage:
#   ./project-setup.sh up        -> Build and start all containers with logs
#   ./project-setup.sh down      -> Stop and remove all containers
#   ./project-setup.sh rebuild   -> Clean, rebuild, and start everything
# ============================================================

# Define colors for output
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

echo -e "${YELLOW}🏋️ Starting The Club setup script...${NC}"

# Always run from the project root
cd "$(dirname "$0")" || exit 1

# Check Docker
if ! command -v docker &> /dev/null
then
    echo -e "${RED}❌ Docker is not installed or not in PATH.${NC}"
    exit 1
fi

# Check Maven
if ! command -v mvn &> /dev/null
then
    echo -e "${RED}❌ Maven is not installed or not in PATH.${NC}"
    exit 1
fi

# Function: Build all services with Maven
# The root pom builds common-lib first, then every service that depends on it.
build_services() {
    echo -e "${GREEN}🔧 Building all microservices with Maven...${NC}"
    mvn clean install -DskipTests || exit 1
}

# Use the Compose v2 plugin when available, fall back to the standalone binary
if docker compose version &> /dev/null; then
    COMPOSE="docker compose"
else
    COMPOSE="docker-compose"
fi

# Create .env from the template on first run
if [ ! -f .env ]; then
    echo -e "${YELLOW}📝 No .env found, copying enviroinment_variables.env -> .env${NC}"
    cp enviroinment_variables.env .env
fi

# Generate keys and passwords on first run (existing secrets are never overwritten)
if [ ! -f secrets/theclub.jwt.private-key ]; then
    echo -e "${YELLOW}🔐 No secrets found, generating ./secrets ...${NC}"
    ./scripts/generate-secrets.sh || exit 1
fi

# Function: Build Docker images
build_docker() {
    echo -e "${GREEN}🐳 Building Docker images...${NC}"
    $COMPOSE build
}

# Function: Start containers
start_containers() {
    echo -e "${GREEN}🚀 Starting all containers...${NC}"
    $COMPOSE up -d
    echo -e "${YELLOW}📜 Showing live logs... (Press CTRL + C to stop viewing)${NC}"
    $COMPOSE logs -f
}

# Function: Stop containers
stop_containers() {
    echo -e "${RED}🛑 Stopping and removing all containers...${NC}"
    $COMPOSE down
}

# Function: Full rebuild (clean build + docker rebuild + start)
rebuild_all() {
    echo -e "${GREEN}🔁 Full rebuild initiated...${NC}"
    build_services
    build_docker
    start_containers
}

# Parse arguments
case "$1" in
    up)
        build_services
        build_docker
        start_containers
        ;;
    down)
        stop_containers
        ;;
    rebuild)
        rebuild_all
        ;;
    *)
        echo -e "${YELLOW}Usage: ./project-setup.sh {up|down|rebuild}${NC}"
        exit 1
        ;;
esac

echo -e "${GREEN}✅ Done.${NC}"
