
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
Log in as the bootstrap admin (`ADMIN_EMAIL` from `.env`, password in `secrets/admin-password`) to approve club-owner applications. For local UI development against a plain-HTTP backend, set `AUTH_COOKIE_SECURE=false` and `REQUIRE_EMAIL_VERIFICATION=false` (the dev template already does).
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

## 🧰 Directory Structure
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

## 🚀 Running on a New Machine (from scratch)

These steps run the whole backend plus the web UI with Docker.

### 1. Install prerequisites

**Ubuntu / Debian**
```bash
# Git, Java 21 JDK (the full JDK, not just the runtime) and Maven
sudo apt update
sudo apt install -y git openjdk-21-jdk maven curl jq

# Docker Engine + Compose v2
curl -fsSL https://get.docker.com | sudo sh
sudo usermod -aG docker $USER      # log out and back in so docker works without sudo
```

**macOS / Windows:** install Docker Desktop, Temurin JDK 21 and Maven 3.9+.

Check the installs:
```bash
java -version          # 21.x
javac -version         # must exist; if missing, only a JRE is installed
mvn -v                 # 3.6+
docker compose version
```

### 2. Clone and configure
```bash
git clone https://github.com/guru-pj/the_club.git
cd the_club
cp enviroinment_variables.env .env
./scripts/generate-secrets.sh
```

`.env` holds only non-secret settings. The defaults work for a first local run: test-mode payments, emails caught by Mailpit, email verification off.

`generate-secrets.sh` creates `./secrets/` (gitignored) containing:
- the RS256 token signing key pair
- the internal service key
- database passwords, including a separate least-privilege user per service
- the first admin's password, printed once at the end and kept in `secrets/admin-password`

Each file is mounted only into the containers that need it. `project-setup.sh` runs the generator automatically on first use.

### 3. Build and start
```bash
./project-setup.sh up          # Windows: project-setup.bat up
```
This builds every service with Maven (the first run downloads dependencies for a few minutes), builds the Docker images, starts everything and streams the logs. Press Ctrl+C to stop watching; the containers keep running.

The same thing, step by step:
```bash
mvn clean install -DskipTests           # from the project root (common-lib must build first)
docker compose up -d --build
```

### 4. Verify (allow about a minute for startup)

| What | URL |
|----------|-----|
| **Web app** | http://localhost:3000 |
| Gateway health | http://localhost:8080/actuator/health |
| Discovery (Eureka): all 7 services should be listed | http://localhost:8761 |
| Mailpit (sent emails / QR tickets) | http://localhost:8025 |
| Trino Web UI | http://localhost:8085 |

Then run the end-to-end smoke test (needs `curl` and `jq`). It should end with `ALL PASSED`:
```bash
./scripts/smoke-test.sh      # reads the admin password from secrets/admin-password
```

### Day-to-day commands
```bash
docker compose ps                        # status of each container
docker compose logs -f event-service     # follow one service's log
docker compose down                      # stop everything (data is kept)
docker compose down -v                   # stop and wipe the databases

# after pulling new code
mvn clean install -DskipTests && docker compose up -d --build
```

### Tests
```bash
mvn test                       # unit + MockMvc/H2 integration tests for every service
cd web-app && npm test         # web app unit tests
```

### Troubleshooting

| Symptom | Fix |
|---|---|
| `port is already allocated` | Another program uses one of the ports 3306, 5432, 8080–8088, 8761, 8888, 1025, 8025 or 3000 (often a local MySQL). Stop it or change the port in `.env`. |
| 503 errors for the first 30–60 s | Services are still registering with Eureka. Wait and retry. |
| A service keeps restarting | Check `docker compose logs <service>`. Usually the database wasn't ready yet (it recovers on its own) or `secrets/` is missing; run `./scripts/generate-secrets.sh`. |
| `release version 21 not supported` (Maven) | Only a Java runtime is installed. Install `openjdk-21-jdk`. |
| Machine low on memory | The stack needs about 4–6 GB. Trino is optional: `docker compose stop trino`. |
| Opening the app from other computers | Use `http://<server-ip>:3000`. For Strava, set `STRAVA_REDIRECT_URI` to `http://<server-ip>:3000/strava/callback`. |

---

## 🧠 Developer Notes

- **Going to production:** follow [`docs/PRODUCTION.md`](docs/PRODUCTION.md). It covers TLS, `SPRING_PROFILES_ACTIVE=prod`, secrets, backups, key rotation and the deploy command. In production only the web app is exposed; the gateway listens on localhost behind nginx.
- **Security model:**
  - Access tokens are RS256 JWTs valid for 15 minutes. Only auth-service holds the signing key; public key at `/api/v1/auth/.well-known/jwks.json`.
  - Refresh tokens live in an httpOnly `SameSite=Strict` cookie.
  - Logins lock for 15 minutes after 5 failures. The gateway rate-limits auth endpoints per IP.
  - Accounts get email verification, password reset, password change and "sign out of all devices".
  - Details are in [`docs/SERVICE_CONTRACTS.md`](docs/SERVICE_CONTRACTS.md).
- **Database schema:** managed by Flyway (`db/migration` in each service; Hibernate only validates). See [`docs/DATABASE_MIGRATIONS.md`](docs/DATABASE_MIGRATIONS.md) before changing an entity.
- **Payments:**
  - `PAYMENT_PROVIDER` must be set explicitly. `mock` is development-only and refused under the `prod` profile.
  - With `razorpay`, set the key ID plus the key and webhook secrets, and point the Razorpay webhook at `https://<host>/api/v1/payments/webhooks/razorpay`. The webhook and a 5-minute reconciliation job confirm payments even if the browser closes.
  - Owners can refund a payment, and cancelling a paid event refunds everyone.
- **Backups:** nightly MySQL and PostgreSQL dumps into the `backups` volume (14-day retention). On demand: `scripts/backup.sh`. Restore one database: `scripts/restore.sh`.
- **CI/CD:** `.github/workflows/ci.yml` runs on every push and pull request:
  - backend tests, plus Testcontainers migration tests against real MySQL and PostgreSQL
  - web lint, tests and build
  - on `main`, builds the images, scans them with Trivy and pushes them to GHCR

  CodeQL and Dependabot are enabled as well.
- **Strava:** put `STRAVA_CLIENT_ID` in `.env` and the client secret in `secrets/strava-client-secret`. Set the Strava app's callback domain to match `STRAVA_REDIRECT_URI` (the web app's `/strava/callback` page), and register the webhook `https://<public-host>/api/v1/strava/webhook`.
- **Email:** all mail goes to Mailpit locally. Configure `MAIL_*` in `.env` and `secrets/smtp-password` for a real SMTP server.
- **Proof documents** for owner verification are submitted as URLs. Upload storage (MinIO/S3) is not built yet.
- **Times:** event times are local to `APP_TIME_ZONE` (default `Asia/Kolkata`). Check-in opens 6h before the start and closes 12h after.

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
