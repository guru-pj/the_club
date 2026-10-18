
# 🏋️‍♂️ The Club — Microservices Fitness Platform

**The Club** is a microservice-based fitness tracking platform built with **Spring Boot**, **Docker**, and **Trino** — integrating fitness data (like Strava-style tracking), authentication, and analytics into a scalable backend architecture.

---

## ⚙️ Tech Stack

- **Java 21**
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

| Service | Description | Port | Database |
|----------|--------------|------|----------|
| 🌐 Gateway Service | Single entry point; routes `/api/v1/**`, blocks `/internal/**` | 8080 | — |
| 🧑‍💻 User Service | Users, profiles, roles, registration | 8081 | MySQL `userdb` |
| 🔐 Auth Service | Login, JWT access + refresh tokens | 8082 | MySQL `authdb` |
| 🏃 Activity Service | Manual activity logging, Strava connect/webhook/sync | 8083 | PostgreSQL `activitydb` |
| 🏟️ Club Service | Clubs, membership, club feed, club leaderboard, owner verification | 8084 | MySQL `clubdb` |
| 📅 Event Service | Events, registration, payments, QR tickets, check-in, points, monthly leaderboard | 8086 | MySQL `eventdb` |
| 🔔 Notification Service | In-app notifications + email (QR ticket attached) | 8087 | MySQL `notificationdb` |
| 🧭 Discovery Service | Eureka service registry | 8761 | — |
| ⚙️ Config Service | Spring Cloud Config (native or git backend) | 8888 | — |
| 🔍 Trino | Query engine connecting MySQL + PostgreSQL | 8085 | — |
| 📬 Mailpit | Local SMTP catcher for confirmation emails | 8025 (UI) | — |
| 🖥️ Web App | React UI for members, club owners and admins (`web-app/`) | 3000 | — |

`common-lib` holds code shared by every service: JWT validation, the UUIDv7 generator, internal API-key auth and problem-detail error handling.
Internal APIs, security model and conventions: [`docs/SERVICE_CONTRACTS.md`](docs/SERVICE_CONTRACTS.md).

---

## 🖥️ Web App

`web-app/` is a React + TypeScript single-page app (Vite) covering every Phase 1 + 2 feature:

| Who | What they can do |
|---|---|
| Member | Sign up / log in, dashboard, browse & join clubs, club feed, log activities, connect Strava, register & pay for events, QR tickets, leaderboards, notifications |
| Club owner | Apply for ownership, create/edit club, post announcements & event recaps (photos, linked Strava activity), create events with registration windows, view registrations, scan QR tickets with the phone camera to check members in |
| Admin | Review owner applications (approve + create club, reject, request more info) |

**With Docker:** `docker compose up -d --build` serves it at http://localhost:3000. nginx proxies `/api` to the gateway.

**For UI development:** start the backend, then:
```bash
cd web-app
npm install
npm run dev          # http://localhost:5173, proxies /api to http://localhost:8080 (override with GATEWAY_URL)
npm test             # unit tests (vitest)
npm run e2e          # browser end-to-end flow (needs Chrome; set CHROME_PATH, ADMIN_EMAIL, ADMIN_PASSWORD)
```
Log in as the bootstrap admin from `.env` (`ADMIN_EMAIL` / `ADMIN_PASSWORD`) to approve club-owner applications.
The camera QR scanner needs HTTPS or `localhost`; on a phone over plain HTTP, use the manual ticket-code field.

---

## ✅ Features (Phase 1 + Phase 2)

All endpoints are called through the gateway at `http://localhost:8080` with `Authorization: Bearer <accessToken>`.

| Feature | Endpoints |
|---|---|
| Signup / login (JWT) | `POST /api/v1/users/register`, `POST /api/v1/auth/login`, `/refresh`, `/logout`, `GET /api/v1/users/me` |
| Become a club owner | `POST /api/v1/owner-verification/apply` → admin `POST /requests/{id}/approve` · `/reject` · `/request-info`, then `POST /api/v1/auth/refresh` to pick up `CLUB_OWNER` |
| Create / join clubs | `POST /api/v1/clubs` (owners), `GET /api/v1/clubs?q=`, `POST /api/v1/clubs/{id}/join`, `GET /api/v1/clubs/me` |
| Log activities | `POST /api/v1/activities`, `GET /api/v1/activities/me`, `PUT`/`DELETE /api/v1/activities/{id}` |
| Club feed | `POST /api/v1/clubs/{id}/posts` (owner; photos, event recaps, linked Strava activity), `GET /api/v1/clubs/{id}/posts` |
| Club leaderboard | `GET /api/v1/clubs/{id}/leaderboard?metric=DISTANCE\|TIME\|SESSIONS&period=WEEK\|MONTH\|YEAR\|ALL` |
| Events + registration window | `POST /api/v1/events` (owner), `PATCH /api/v1/events/{id}/registration-window`, `GET /api/v1/events/club/{clubId}` |
| Register in-app | `POST /api/v1/events/{id}/register`, `DELETE /api/v1/events/{id}/registrations/me`, `GET /api/v1/events/me/registrations` |
| In-app payment | `POST /api/v1/payments/{id}/confirm` (mock or Razorpay) |
| Confirmation + email + QR ticket | sent on confirmation; `GET /api/v1/events/registrations/{id}/ticket` (JSON + base64 QR) or `/ticket.png` |
| QR check-in on event day | `POST /api/v1/events/{id}/check-in` `{ticketCode}` (club owner) |
| Strava | `GET /api/v1/strava/connect`, `/callback`, `/webhook`, `POST /api/v1/strava/sync`; only the club owner's Strava activities can be linked to posts |
| Participation + points/stars | `GET /api/v1/participation/me`, `/users/{id}`, `/clubs/{id}` (10 points per check-in by default, 1 star per 5 events) |
| Monthly leaderboard | `GET /api/v1/leaderboards/monthly?month=YYYY-MM` (top individuals + top clubs) |
| Notifications | `GET /api/v1/notifications/me`, `PATCH /api/v1/notifications/{id}/read` |

---

## 🧰 Environment Setup

### Prerequisites
- Docker Desktop (latest version)
- Java 21 **JDK**
- Maven 3.9+
- Git

### Directory Structure
```
the-club/
 ┣━ pom.xml                 (parent; builds every module)
 ┣━ common-lib/
 ┣━ discovery-service/  config-service/  gateway-service/
 ┣━ user-service/  auth-service/  activity-service/
 ┣━ club-service/  event-service/  notification-service/
 ┣━ web-app/                (React UI)
 ┣━ docs/SERVICE_CONTRACTS.md
 ┣━ scripts/smoke-test.sh
 ┣━ docker-compose.yml
 ┗━ enviroinment_variables.env   (template for .env)
```

---

## 🚀 Build & Run Instructions

### Quick start
```bash
./project-setup.sh up          # Windows: project-setup.bat up
```
This copies `enviroinment_variables.env` to `.env` (first run only), builds all modules, builds the images and starts everything.

### Manual steps
```bash
cp enviroinment_variables.env .env      # then edit secrets
mvn clean install -DskipTests           # from the project root (common-lib must build first)
docker compose up -d --build
```

### Verify
| Service | URL |
|----------|-----|
| **Web app** | http://localhost:3000 |
| Gateway (API) | http://localhost:8080 |
| Discovery (Eureka) | http://localhost:8761 |
| Mailpit (sent emails) | http://localhost:8025 |
| Trino Web UI | http://localhost:8085 |

Then run the end-to-end smoke test (needs `curl` and `jq`):
```bash
ADMIN_PASSWORD=<from .env> STRAVA_WEBHOOK_VERIFY_TOKEN=<from .env> ./scripts/smoke-test.sh
```

### Stop Services
```bash
docker compose down
```

### Tests
```bash
mvn test        # unit tests + MockMvc/H2 integration tests for every module
```

---

## 🧠 Developer Notes

- **Secrets:** change `JWT_SECRET` (≥ 32 chars), `INTERNAL_API_KEY` and `ADMIN_PASSWORD` in `.env` for anything beyond local dev.
- **First admin:** `ADMIN_EMAIL` / `ADMIN_PASSWORD` create a platform admin at startup. The admin reviews club-owner applications.
- **Payments:** `PAYMENT_PROVIDER=mock` is for development only. The mock provider exposes `POST /api/v1/payments/{id}/mock-signature`, so anyone can "pay". Set `PAYMENT_PROVIDER=razorpay` plus `RAZORPAY_KEY_ID`/`RAZORPAY_KEY_SECRET` for real payments. The client completes checkout with Razorpay and sends `{providerPaymentId, signature}` to `/confirm`. Refunds are not implemented.
- **Strava:** set `STRAVA_CLIENT_ID`/`STRAVA_CLIENT_SECRET`. Set the Strava app's callback domain to match `STRAVA_REDIRECT_URI` (the web app's `/strava/callback` page). Register the webhook `https://<public-host>/api/v1/strava/webhook` with `STRAVA_WEBHOOK_VERIFY_TOKEN`.
- **Email:** all mail goes to Mailpit locally. Point `MAIL_HOST`/`MAIL_PORT` at a real SMTP server in other environments.
- **Proof documents** for owner verification are submitted as URLs. Upload storage (MinIO/S3) is not built yet.
- **Logout** revokes the refresh token. Access tokens stay valid until they expire (`JWT_EXPIRATION`, default 1h).
- **Times:** event times are local to `APP_TIME_ZONE` (default `Asia/Kolkata`). Check-in opens 6h before the start and closes 12h after.
- **Schema:** schemas are managed with `ddl-auto=update`. Introduce Flyway/Liquibase before production.

---

## 📦 Future Enhancements

- Phase 3 roadmap: cross-club competitions, training plans, full Strava auto-linking to events, club chat, push notifications, memberships
- Add Analytics Microservice using Trino
- Deploy to Kubernetes (GKE or EKS)

---

## 👨‍💻 Author

**Guru PJ**  
Java Developer | Fitness Enthusiast  
💬 *Private project — internal development reference only.*
