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
build_services() {
    echo -e "${GREEN}🔧 Building all microservices with Maven...${NC}"
    for dir in user-service auth-service activity-service gateway-service discovery-service config-service
    do
        if [ -d "$dir" ]; then
            echo -e "${YELLOW}➡️ Building $dir...${NC}"
            cd $dir || exit
            mvn clean package -DskipTests
            cd ..
        else
            echo -e "${RED}⚠️ Directory $dir not found, skipping...${NC}"
        fi
    done
}

# Function: Build Docker images
build_docker() {
    echo -e "${GREEN}🐳 Building Docker images...${NC}"
    docker-compose build
}

# Function: Start containers
start_containers() {
    echo -e "${GREEN}🚀 Starting all containers...${NC}"
    docker-compose up -d
    echo -e "${YELLOW}📜 Showing live logs... (Press CTRL + C to stop viewing)${NC}"
    docker-compose logs -f
}

# Function: Stop containers
stop_containers() {
    echo -e "${RED}🛑 Stopping and removing all containers...${NC}"
    docker-compose down
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
