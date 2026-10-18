
# 🏋️‍♂️ The Club — Microservices Fitness Platform

**The Club** is a microservice-based fitness tracking platform built with **Spring Boot**, **Docker**, and **Trino** — integrating fitness data (like Strava-style tracking), authentication, and analytics into a scalable backend architecture.

---

## ⚙️ Tech Stack

- **Java 21+ / 25**
- **Spring Boot 3.5.7**
- **Spring Cloud (Eureka, Gateway, Config Server)**
- **MySQL + PostgreSQL** (for data persistence)
- **Trino** (for federated query access)
- **Docker & Docker Compose**
- **JWT Authentication**
- **Maven 3.9+**
- **pgAdmin / MySQL Workbench** (for DB management)

---

## 🧩 Microservices Overview

| Service | Description | Port |
|----------|--------------|------|
| 🧑‍💻 User Service | Manages users, profiles, and registration | 8081 |
| 🔐 Auth Service | Handles login, JWT tokens, and security | 8082 |
| 🏃 Activity Service | Tracks user fitness data and Strava integration | 8083 |
| 🌐 Gateway Service | Routes all API requests to internal services | 8080 |
| 🧭 Discovery Service | Eureka-based service registry | 8761 |
| ⚙️ Config Service | Centralized configuration management | 8888 |
| 🔍 Trino | Query engine connecting MySQL + PostgreSQL | 8085 |

---

## 🧰 Environment Setup

### Prerequisites
Ensure these tools are installed before running:
- Docker Desktop (latest version)
- Java 21 or newer
- Maven 3.9+
- Git

### Directory Structure
```
the-club/
 ┣━ user-service/
 ┣━ auth-service/
 ┣━ activity-service/
 ┣━ gateway-service/
 ┣━ discovery-service/
 ┣━ config-service/
 ┣━ docker-compose.yml
 ┣━ the_club_services_config.md
 ┗━ README.md
```

---

## 🚀 Build & Run Instructions

### 1️⃣ Package Each Service
Inside each service directory, run:
```bash
mvn clean package -DskipTests
```

### 2️⃣ Build Docker Images
From the project root:
```bash
docker-compose build
```

### 3️⃣ Run Databases First (Optional)
```bash
docker-compose up -d mysql postgres trino
```

### 4️⃣ Start All Services
```bash
docker-compose up -d
```

### 5️⃣ Verify Services
| Service | URL |
|----------|-----|
| User Service | http://localhost:8081 |
| Auth Service | http://localhost:8082 |
| Activity Service | http://localhost:8083 |
| Gateway Service | http://localhost:8080 |
| Discovery (Eureka) | http://localhost:8761 |
| Config Service | http://localhost:8888 |
| Trino Web UI | http://localhost:8085 |

### 6️⃣ Stop Services
```bash
docker-compose down
```

---

## 🧠 Developer Notes

- Use **pgAdmin** or **DBeaver** for DB browsing.
- Each service connects to its respective DB inside Docker.
- You can modify DB configs inside `application.properties`.
- JWT keys and secrets are defined in the Auth Service `.env` or environment variables.

---

## 📦 Future Enhancements

- Add Strava API Integration
- Add Notification Service (email + push)
- Add Analytics Microservice using Trino
- Deploy to Kubernetes (GKE or EKS)

---

## 👨‍💻 Author

**Guru PJ**  
Java Developer | Fitness Enthusiast  
💬 *Private project — internal development reference only.*
