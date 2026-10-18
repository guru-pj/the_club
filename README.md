
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

## 🚀 Getting Started (Linux & macOS, from scratch)

Follow these steps in order on a fresh machine. Every command block is copy-pasteable. The whole stack runs in Docker; Java and Maven are only needed to build it.

**At a glance:** install the tools (step 1) → clone (step 2) → generate secrets (step 3) → build and start (step 4) → open http://localhost:3000.

### What you need

| | Minimum | Recommended |
|---|---|---|
| OS | Ubuntu 22.04+ / Debian 12+ / Fedora 39+, or macOS 13 Ventura+ (Apple Silicon or Intel) | Ubuntu 24.04 / macOS 14+ |
| RAM | 8 GB | 16 GB |
| Free disk | 20 GB | 40 GB |
| Network | Needed for the first build (about 3 GB of downloads) | |
| Access | A GitHub account that can read `guru-pj/the_club` | |

| Tool | Version | Why |
|---|---|---|
| Git | any | clone the code |
| Java **JDK** | **21** (a JRE alone won't work) | compile the services |
| Maven | 3.6+ | build the services |
| Docker + Compose v2 | Docker 24+ | run everything |
| bash, openssl, curl, jq | any | secrets generator and smoke test |

**Optional:**

| Tool | For | Install |
|---|---|---|
| Node.js 22 + Chrome | web-app development and the browser test | see [Tests](#tests) |
| kind, kubectl, Helm | running on a local Kubernetes cluster | see [`docs/KUBERNETES.md`](docs/KUBERNETES.md#quick-start-on-your-laptop-kind) (Linux and macOS commands) |

The app alone needs 8 GB of RAM. **Monitoring** (Prometheus/Grafana/Tempo/Loki) adds about 2 GB, and **local Kubernetes** (kind) needs about 10 GB on its own. On macOS, raise Docker Desktop's memory accordingly (Settings → Resources).

> ⚠️ **Never run `mvn`, `./project-setup.sh` or the scripts with `sudo`.** Doing so leaves root-owned files that break later builds. Only the install commands in step 1 use `sudo`.

---

### Step 1A — Install the tools on Linux (Ubuntu / Debian)

**Debian only: if `sudo` says "command not found".** A Debian install with a root password has no `sudo`. Run this once, entering the **root** password when asked, then **log out and back in**:
```bash
su -c "apt update && apt install -y sudo && /usr/sbin/usermod -aG sudo $USER"
```
Check it with `sudo true`. It should ask for *your* password and print nothing else.

```bash
sudo apt update
sudo apt install -y git maven curl jq openssl ca-certificates
```

Install Java 21. On Ubuntu 22.04/24.04 it's in the standard repositories:
```bash
sudo apt install -y openjdk-21-jdk
```
On **Debian 12**, or if the line above reports "Unable to locate package", install Temurin 21 instead:
```bash
sudo apt install -y wget gpg
wget -qO - https://packages.adoptium.net/artifactory/api/gpg/key/public | gpg --dearmor | sudo tee /etc/apt/trusted.gpg.d/adoptium.gpg > /dev/null
echo "deb https://packages.adoptium.net/artifactory/deb $(awk -F= '/^VERSION_CODENAME/{print $2}' /etc/os-release) main" | sudo tee /etc/apt/sources.list.d/adoptium.list
sudo apt update && sudo apt install -y temurin-21-jdk
```

Install Docker Engine and Compose v2, then let your user run Docker without `sudo`:
```bash
curl -fsSL https://get.docker.com | sudo sh
sudo usermod -aG docker $USER
```
**Log out and log back in** (or reboot) so the group change applies to every terminal. To use it immediately in the current terminal only, run:
```bash
newgrp docker
```

<details>
<summary><b>Fedora / RHEL / CentOS Stream</b></summary>

```bash
sudo dnf install -y git maven curl jq openssl java-21-openjdk-devel
curl -fsSL https://get.docker.com | sudo sh
sudo systemctl enable --now docker
sudo usermod -aG docker $USER      # then log out and back in
```
</details>

If you have several Java versions, make 21 the default:
```bash
sudo update-alternatives --config java
sudo update-alternatives --config javac
```

### Step 1B — Install the tools on macOS (Apple Silicon or Intel)

Command Line Tools (provides git). Click **Install** in the dialog that appears:
```bash
xcode-select --install
```

Homebrew:
```bash
/bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)"
```
**Apple Silicon only:** put Homebrew on your PATH:
```bash
echo 'eval "$(/opt/homebrew/bin/brew shellenv)"' >> ~/.zprofile
eval "$(/opt/homebrew/bin/brew shellenv)"
```

Java 21, Maven and the other tools. `JAVA_HOME` makes Maven use Java 21 even if other versions are installed:
```bash
brew install --cask temurin@21
brew install maven jq openssl@3
echo 'export JAVA_HOME=$(/usr/libexec/java_home -v 21)' >> ~/.zshrc
echo 'export PATH="$JAVA_HOME/bin:$PATH"' >> ~/.zshrc
source ~/.zshrc
```

Docker Desktop:
```bash
brew install --cask docker
open -a Docker
```
Wait until the whale icon in the menu bar says **Docker Desktop is running**. Then open **Settings ⚙ → Resources → Advanced** and set:
- **CPUs:** 4 or more
- **Memory:** 6 GB minimum, 8 GB recommended
- **Disk:** 40 GB

Click **Apply & restart**. Keep the project inside your home folder (`/Users/<you>/…`); Docker Desktop shares it by default.

> OrbStack (`brew install --cask orbstack`) or Colima (`brew install colima docker docker-compose && colima start --cpu 4 --memory 8 --disk 40`) also work instead of Docker Desktop.

### Step 1C — Check the installs (Linux and macOS)

```bash
git --version
java -version        # must say 21
javac -version       # must say 21 — if "command not found", only a JRE is installed
mvn -v               # must show "Java version: 21"
docker compose version
docker run --rm hello-world     # must work WITHOUT sudo
openssl version && jq --version
```
If `docker run` says **permission denied** on Linux, you haven't logged out and back in since `usermod` (or run `newgrp docker`). If it says **Cannot connect to the Docker daemon** on macOS, Docker Desktop isn't running yet.

---

### Step 2 — Clone the code

```bash
cd ~
git clone https://github.com/guru-pj/the_club.git
cd the_club
```
The repository is **private**. You need a GitHub account that the owner has added as a collaborator (accept the email invitation first). When git asks:
- **Username:** your GitHub username
- **Password:** a **personal access token**, not your GitHub password. Create one at github.com → Settings → Developer settings → Personal access tokens → *Fine-grained*, with read access to this repository's *Contents*. Never paste a token into chats or files in the repo.

To avoid typing the token on every `git pull`, let git remember it in memory for 8 hours:
```bash
git config --global credential.helper 'cache --timeout=28800'
```
Using SSH instead? After [adding an SSH key to GitHub](https://docs.github.com/en/authentication/connecting-to-github-with-ssh), clone with `git clone git@github.com:guru-pj/the_club.git`.

### Step 3 — Configure

```bash
cp enviroinment_variables.env .env
./scripts/generate-secrets.sh
```
The second command creates `secrets/` (never committed). It holds the signing keys and the internal service key, plus passwords for the databases and the first admin. It prints the admin password once; you can read it again any time with `cat secrets/admin-password`.

The defaults in `.env` are set up for local use: test-mode payments, emails caught by Mailpit, and email verification off.

**Check for port clashes** before starting. The stack uses these ports on your machine:
```bash
bash -c 'for p in 3000 3306 5432 8025 8080 8081 8082 8083 8084 8085 8086 8087 8761 8888; do (echo > /dev/tcp/127.0.0.1/$p) 2>/dev/null && echo "  port $p is already in use"; done; echo "port check done"'
```
If you plan to run the monitoring stack too, it also uses 3001 (Grafana), 9090 (Prometheus) and 9093 (Alertmanager); add them to the list above.

If anything is listed, free the port or move ours in `.env`. A locally installed PostgreSQL (5432) or MySQL (3306) is the usual cause:
```bash
# use 5433 / 3307 on your machine instead (works on Linux and macOS)
sed -i.bak 's/^POSTGRES_PORT=.*/POSTGRES_PORT=5433/; s/^MYSQL_PORT=.*/MYSQL_PORT=3307/' .env && rm .env.bak
```
These variables only change the port on your machine; the containers still talk to each other as before.

### Step 4 — Build and start

```bash
mvn clean install -DskipTests
docker compose up -d --build
```
The first run takes 5–15 minutes because Maven and Docker download their dependencies; later runs take about a minute.

Wait until every service reports healthy:
```bash
echo "Waiting for services to become healthy (up to 10 minutes)..."; end=$((SECONDS+600)); until ! docker compose ps --format '{{.Status}}' | grep -qE 'starting|unhealthy|Restarting'; do [ $SECONDS -gt $end ] && echo "Still not healthy after 10 minutes - see Troubleshooting" && break; sleep 5; done; docker compose ps --format 'table {{.Service}}\t{{.Status}}'
```
Every service should show `Up … (healthy)`. The two `db-backup-*` containers have no health check and just show `Up`. If something shows `Restarting` or `Exited`, see [Troubleshooting](#troubleshooting).

> **Shortcut:** `./project-setup.sh up` does steps 3–4 in one go (copy `.env`, generate secrets, build, start), then streams the logs. Press Ctrl+C to stop watching; the containers keep running.

### Step 5 — Verify and open the app

Run the automated end-to-end check. It signs up users, creates a club and events, handles payment, ticket and check-in, and ends with `ALL PASSED`:
```bash
./scripts/smoke-test.sh
```
If the first steps fail with 503, the services are still finding each other. Wait 30 seconds and run it again.

| Open | URL |
|---|---|
| **The Club** | http://localhost:3000 |
| Emails sent by the app (tickets, password resets) | http://localhost:8025 |
| Service registry: all 7 services should be listed | http://localhost:8761 |

Log in as admin with `admin@theclub.local` and this password:
```bash
cat secrets/admin-password
```
Then follow the [User Guide](#-user-guide) below.

### Optional next steps (Linux and macOS)

| Goal | Guide |
|---|---|
| Send real emails (verification, reset, tickets) through Zoho ZeptoMail | [`docs/EMAIL_SETUP.md`](docs/EMAIL_SETUP.md) |
| Connect real Strava accounts | [`docs/STRAVA_SETUP.md`](docs/STRAVA_SETUP.md) |
| Dashboards, traces, logs and alerts | the monitoring command under [Day-to-day commands](#day-to-day-commands), then [`docs/OBSERVABILITY.md`](docs/OBSERVABILITY.md) |
| Run on Kubernetes, locally with kind or in the cloud | [`docs/KUBERNETES.md`](docs/KUBERNETES.md) |
| Deploy to a production server | [`docs/PRODUCTION.md`](docs/PRODUCTION.md) |

---

### Day-to-day commands

```bash
docker compose ps                        # status of every container
docker compose logs -f user-service      # follow one service's log (Ctrl+C to stop)
docker compose stop                      # stop everything (data is kept)
docker compose start                     # start it again
docker compose down                      # remove containers (data is kept in volumes)
docker compose down -v                   # remove containers AND all data (fresh start)
```

Update to the latest code. The generator only adds new secrets and never changes existing ones; newer versions may need new ones:
```bash
git pull
./scripts/generate-secrets.sh
mvn clean install -DskipTests
docker compose up -d --build
```

Monitoring (Prometheus, Grafana, Tempo, Loki). Grafana runs at http://localhost:3001 with user `admin` and the password from `cat secrets/grafana-admin-password`:
```bash
docker compose -f docker-compose.yml -f docker-compose.override.yml -f docker-compose.observability.yml up -d
```
On macOS, log collection reads Docker's socket at `/var/run/docker.sock`. In Docker Desktop, keep **Settings → Advanced → "Allow the default Docker socket to be used"** enabled; it's on by default. Details: [`docs/OBSERVABILITY.md`](docs/OBSERVABILITY.md).


Low on memory? Trino (analytics) is optional. Stop it:
```bash
docker compose stop trino
```

### Tests

```bash
mvn test                       # unit + integration tests for every service
cd web-app && npm ci && npm test && cd ..     # web app tests (needs Node.js 22)
```
For web-app development (`npm run dev`) and the browser test (`npm run e2e`), install Node.js 22 and Chrome:
- Linux: `curl -fsSL https://deb.nodesource.com/setup_22.x | sudo -E bash - && sudo apt install -y nodejs`
- macOS: `brew install node@22 && brew install --cask google-chrome`

See the Web App section above.

### Troubleshooting

| Problem | Fix |
|---|---|
| `./project-setup.sh: Permission denied` | `chmod +x project-setup.sh scripts/*.sh` |
| `permission denied … docker.sock` (Linux) | Log out and back in after `usermod -aG docker $USER`, or run `newgrp docker` |
| `Cannot connect to the Docker daemon` (macOS) | Start Docker Desktop and wait for "running" |
| `Failed to delete …/target/…` or other permission errors in the build | A previous run used `sudo`. Fix ownership: `sudo chown -R $USER:$USER .` |
| `release version 21 not supported` / `mvn -v` shows another Java | Install JDK 21 and make it the default (step 1). On macOS, check `echo $JAVA_HOME`. |
| `port is already allocated` / `address already in use` | Run the port check in step 3 and change `POSTGRES_PORT` / `MYSQL_PORT` in `.env`, then `docker compose up -d` |
| `Service temporarily unavailable` / 503 right after starting | Services are still registering. Wait 30–60 s and retry. |
| `Bad Gateway` in the web app | Run `docker compose restart web-app`, and `git pull` the latest code, which fixes the cause |
| A service keeps `Restarting` | `docker compose logs --tail=100 <service>`. Usually the database isn't ready yet (it recovers) or `secrets/` is missing (`./scripts/generate-secrets.sh`). |
| Containers exit with code 137 | Out of memory. Give Docker more RAM (macOS: Settings → Resources) or `docker compose stop trino` |
| `git pull` refuses: "local changes would be overwritten" | `git stash` (or `git checkout -- <file>`), then `git pull` |
| `xcrun: error: invalid active developer path` (macOS) | `xcode-select --install` |

### Uninstall

```bash
cd ~/the_club && docker compose down -v --rmi local    # containers, data and built images
cd ~ && rm -rf the_club                                 # code and secrets
```

---

## 🗄️ Database Access

Each service owns its own database:

| Server | Database | Holds |
|---|---|---|
| MySQL | `userdb` | users, roles, verification and reset tokens |
| MySQL | `authdb` | refresh-token sessions, login lockouts |
| MySQL | `clubdb` | clubs, memberships, posts, owner applications |
| MySQL | `eventdb` | events, registrations, payments, points |
| MySQL | `notificationdb` | in-app notifications |
| PostgreSQL | `activitydb` | activities, Strava connections |

Run the commands below from the project folder. If `docker` reports "permission denied", log out and back in once after adding yourself to the `docker` group, or wrap the command in `sg docker -c "..."`.

### From the terminal

**MySQL:**
```bash
docker compose exec mysql sh -c 'mysql -uroot -p"$(cat /run/secrets/mysql-root-password)"'
```
```sql
SHOW DATABASES;
USE userdb;
SHOW TABLES;
SELECT name, email, email_verified FROM users;
USE eventdb;
SELECT title, start_time, status FROM events;
SELECT status, COUNT(*) FROM event_registrations GROUP BY status;
exit
```

**PostgreSQL:**
```bash
docker compose exec postgres psql -U postgres -d activitydb
```
```sql
\dt
SELECT title, type, distance, duration FROM activities ORDER BY start_time DESC LIMIT 10;
\q
```

### From a GUI (DBeaver, MySQL Workbench, pgAdmin)

In the local setup, both databases are reachable on localhost. Install DBeaver with `sudo snap install dbeaver-ce` (or `brew install --cask dbeaver-community` on macOS).

| Setting | MySQL | PostgreSQL |
|---|---|---|
| Host | `localhost` | `localhost` |
| Port | `MYSQL_PORT` from `.env` (default `3306`) | `POSTGRES_PORT` from `.env` (default `5432`) |
| User | `root` | `postgres` |
| Password | `cat secrets/mysql-root-password` | `cat secrets/postgres-password` |
| Database | leave empty to see all | `activitydb` |

For MySQL in DBeaver, set the driver property `allowPublicKeyRetrieval=true`.

> **Port already in use?** If PostgreSQL or MySQL is also installed directly on your machine, change `POSTGRES_PORT` (e.g. `5433`) or `MYSQL_PORT` (e.g. `3307`) in `.env`, then run `docker compose up -d`. This only changes the port on your machine; the services still connect internally as before.

### Good to know
- **Read, don't restructure.** The schema is managed by Flyway migrations (see [`docs/DATABASE_MIGRATIONS.md`](docs/DATABASE_MIGRATIONS.md)). Change tables only through a new migration, and change data through the app where possible.
- **Production:** database ports are not published. Use the `docker compose -f docker-compose.yml exec ...` form of the commands above on the server.
- **Start over with empty databases:** `docker compose down -v && docker compose up -d`. This deletes all data; wait about a minute for the services to become healthy.
- **Backups and restore:** see `scripts/backup.sh` / `scripts/restore.sh` and [`docs/PRODUCTION.md`](docs/PRODUCTION.md).

---

## 📖 User Guide

Open the web app: http://localhost:3000 locally, or your domain in production.

### Admin
1. Log in as the bootstrap admin: `ADMIN_EMAIL` from `.env`, with the password in `secrets/admin-password`.
2. Open the avatar menu → **Owner applications**. Review requests from people who want to run a club.
3. **Approve** them, ticking "Create the club now" to set up their club. You can also **Reject** with a reason, or **Request info** to ask for more proof.

### Club owner
1. **Sign up.** When email verification is on, click the link in the verification email first.
2. **Avatar menu → Become a club owner.** Submit your club's name, city, social links and proof documents, then wait for an admin to approve.
3. **Log in again** after approval. **Clubs** → your club now shows the owner tools:
   - **Feed:** post announcements and event recaps with photo links. You can attach one of your Strava activities.
   - **Create event:** set the date, when registration opens and closes, capacity, price (0 = free) and points for attending.
   - **Members**, **Leaderboard** and **Participation** tabs show who is active.
4. **Event page** (owner panel):
   - See every registration with its payment status.
   - **Refund** a payment.
   - **Cancel event**: everyone who paid is refunded automatically.
5. **On event day:** Event page → **Check-in scanner** → **Start camera** and scan each member's QR ticket, or type the ticket code. Check-in opens 6 hours before the start.

### Member
1. **Sign up** and verify your email.
2. **Clubs** → search for a club → **Join club**.
3. **Activities** → **Log activity** for runs, rides and more, or **Connect with Strava** to import them automatically. They count toward the club's **Leaderboard** (distance, time, sessions).
4. Open an event from the club's **Events** tab → **Register**.
   - For a paid event, complete the payment.
   - You get an in-app notification and a confirmation email with your QR ticket.
5. **Tickets** → open your ticket and show the QR code at the event.
6. Each check-in earns points, and every 5 events attended earns a ⭐. **Leaderboard** shows the month's top members and top clubs.

### Your account
- **Forgot password?** is on the login page. The reset link is valid for 30 minutes.
- **Profile** has:
  - your name, city, bio and photo
  - **Change password**
  - **Sign out of all devices**
  - **Resend verification email** if your email isn't verified
- 🔔 **Notifications** lists club posts, registration confirmations, refunds and owner-application updates.
- After 5 wrong passwords, login is locked for 15 minutes.

> **Local trial:** payments run in test mode (no real money). Emails are caught by Mailpit at http://localhost:8025, and email verification is off.

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
  - Helm lint and Kubernetes schema validation
  - on `main`, builds the images, scans them with Trivy and pushes them to GHCR

  CodeQL and Dependabot are enabled as well.
- **Email:** Mailpit catches all mail locally. In production, mail is sent through **Zoho ZeptoMail** with a durable retry queue, branded templates and bounce handling. Setup: [`docs/EMAIL_SETUP.md`](docs/EMAIL_SETUP.md).
- **Strava:** OAuth connect, durable webhook processing, rate-limit handling, encrypted tokens, incremental sync and webhook subscription management. Create your Strava app with [`docs/STRAVA_SETUP.md`](docs/STRAVA_SETUP.md).
- **Observability:** Prometheus metrics (including business metrics), OpenTelemetry tracing across services, JSON logs with `traceId`/`requestId`, and Grafana dashboards and alerts. See [`docs/OBSERVABILITY.md`](docs/OBSERVABILITY.md).
- **Kubernetes:** a Helm chart in `deploy/helm/the-club` with dev and prod values, HPA, PDBs, NetworkPolicies, ServiceMonitors and hardened pods. Try it locally with `./deploy/kind/up.sh`. Guide: [`docs/KUBERNETES.md`](docs/KUBERNETES.md).
- **Multiple replicas:** scheduled jobs are safe to run on several replicas: payment reconciliation uses ShedLock, while the email queue and Strava worker use row-level claims.
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
