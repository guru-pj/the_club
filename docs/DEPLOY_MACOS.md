# The Club — macOS Deployment Guide

This guide takes a brand-new Mac with nothing installed to a running The Club deployment. Every command can be pasted into Terminal as-is, in order.

Using Linux instead? See [DEPLOY_LINUX.md](DEPLOY_LINUX.md).

## Overview

Everything runs in Docker. Java and Maven are needed only to build the services. Steps 1–6 give you a complete running stack. Part 2 is needed only if you want the Mac to serve real users over HTTPS.

|  | Path A: run it on this Mac | Path B: put it live on the internet from this Mac |
| --- | --- | --- |
| Good for | Demos, testing, development, a club on your home network | A small real club, while you don't have a cloud server |
| Steps | 1 → 6 | 1 → 6, then Part 2 |
| Address | http://localhost:3000 | https://club.yourdomain.com |
| Payments | Test mode (mock) | Real Razorpay |
| Emails | Caught by Mailpit at http://localhost:8025 | Delivered through Zoho ZeptoMail |
| Time | About 45 minutes, mostly downloads | About 1.5 hours, plus waiting for DNS |

**What runs:** the web app (nginx) on port 3000 → the gateway → six backend services (auth, user, club, activity, event, notification), plus Eureka (service registry), the config server, MySQL 8, PostgreSQL 15 and nightly database backups.

> For a long-running production site, a Linux cloud server is the better home (see [DEPLOY_LINUX.md](DEPLOY_LINUX.md) and [PRODUCTION.md](PRODUCTION.md)). A Mac works well, but it must stay awake, plugged in and online.

## Requirements

|  | Minimum | Recommended |
| --- | --- | --- |
| Mac | Apple Silicon (M1–M4) or Intel | Apple Silicon |
| macOS | 13 Ventura | 14 Sonoma or newer |
| Memory (RAM) | 8 GB | 16 GB |
| Free disk | 30 GB | 50 GB |
| Internet | About 4 GB of downloads on the first build |  |
| User account | An **Administrator** account (needed by Homebrew) |  |

**Accounts you need**

- **GitHub:** an account the repo owner has added as a collaborator on `guru-pj/the_club`. Accept the email invitation first.
- **Path B only:** a domain name, a Zoho ZeptoMail account, a Razorpay account and, optionally, a Strava API app. Part 2 lists exactly what to collect.

Check your chip and macOS version:  menu → **About This Mac**. "Chip: Apple M…" means Apple Silicon, and "Processor: Intel…" means Intel.

## Step 1 — Install the tools

Open **Terminal**: press ⌘ Space, type `Terminal`, press Return. Paste each block, press Return, and wait for it to finish before pasting the next.

**1.1 Let Terminal accept pasted comments.** macOS's shell (zsh) treats `#` as part of a command unless this is on. Several blocks below contain comments, so run this first:

```bash
setopt interactivecomments
echo 'setopt interactivecomments' >> ~/.zshrc
```

**1.2 Apple Command Line Tools** (provides git). A dialog appears. Click **Install**, accept the licence, and wait for it to finish (5–15 minutes):

```bash
xcode-select --install
```

When it's done, check it:

```bash
git --version
```

**1.3 Homebrew** (the package manager). It asks for your Mac login password; nothing is shown while you type:

```bash
/bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)"
```

**Apple Silicon only:** put Homebrew on your PATH. Intel Macs skip this:

```bash
echo 'eval "$(/opt/homebrew/bin/brew shellenv)"' >> ~/.zprofile
eval "$(/opt/homebrew/bin/brew shellenv)"
```

Check it with `brew --version`.

**1.4 Java 21, Maven, jq and OpenSSL.** Java must be **21**. `JAVA_HOME` makes Maven use it even if Homebrew installs a newer Java alongside:

```bash
brew install --cask temurin@21
brew install maven jq openssl@3
echo 'export JAVA_HOME=$(/usr/libexec/java_home -v 21)' >> ~/.zshrc
echo 'export PATH="$JAVA_HOME/bin:$(brew --prefix openssl@3)/bin:$PATH"' >> ~/.zshrc
source ~/.zshrc
```

**1.5 Docker Desktop.** The cask is called `docker-desktop` on current Homebrew and `docker` on older versions; this line tries both:

```bash
brew install --cask docker-desktop || brew install --cask docker
open -a Docker
```

Docker Desktop opens. Accept the terms, choose **Use recommended settings**, and skip sign-in; an account isn't needed. Wait until the whale icon in the menu bar says **Docker Desktop is running**.

> Prefer something lighter? OrbStack (`brew install --cask orbstack`) also works. Use it instead of Docker Desktop, not alongside it.

**1.6 Check everything.** Each line must succeed:

```bash
git --version
java -version          # must say 21
javac -version         # must say 21
mvn -v                 # must show "Java version: 21"
docker compose version
docker run --rm hello-world
openssl version && jq --version
```

If `mvn -v` shows another Java version, open a new Terminal window (or run `source ~/.zshrc`) and check again.

## Step 2 — Configure Docker Desktop

The stack needs more memory than Docker Desktop's default. Click the whale icon → **Settings** (⚙).

| Setting | Where | Value |
| --- | --- | --- |
| CPU limit | Resources → Advanced | 4 or more |
| Memory limit | Resources → Advanced | 8 GB (6 GB minimum); 10 GB if you'll add monitoring |
| Disk usage limit | Resources → Advanced | 50 GB |
| Start when you sign in | General | On (needed for Path B, so the app restarts after a reboot) |
| Allow the default Docker socket | Advanced | On (the default; used by log collection in monitoring) |

Click **Apply & restart** and wait for **Docker Desktop is running** again.

Keep the project inside your home folder (`/Users/<you>/…`). Docker Desktop shares it with containers by default.

## Step 3 — Clone the code

The repository is **private**. When git asks for a password, it wants a **personal access token**, not your GitHub password.

**3.1 Create a token** (once): github.com → your photo → **Settings** → **Developer settings** → **Personal access tokens** → **Fine-grained tokens** → **Generate new token**.

- Repository access: **Only select repositories** → `guru-pj/the_club`
- Permissions: **Contents → Read-only**
- Copy the token right away; GitHub shows it only once. Never paste it into a chat, a document or a file in the repo.

**3.2 Let macOS Keychain remember it**, so you type it once:

```bash
git config --global credential.helper osxkeychain
```

**3.3 Clone.** Enter your GitHub username, then paste the token as the password:

```bash
cd ~
git clone https://github.com/guru-pj/the_club.git
cd the_club
```

Using SSH keys instead? After [adding an SSH key to GitHub](https://docs.github.com/en/authentication/connecting-to-github-with-ssh), clone with `git clone git@github.com:guru-pj/the_club.git`.

Every command from here on runs inside `~/the_club`. If you open a new Terminal window, run `cd ~/the_club` first.

## Step 4 — Configure

**4.1 Create the settings file and the secrets:**

```bash
cp enviroinment_variables.env .env
./scripts/generate-secrets.sh
```

The second command creates the `secrets/` folder: signing keys, the internal service key, database passwords and the first admin's password. It is never committed to git. It prints the admin password once; you can see it again any time with `cat secrets/admin-password`.

> Back up the `secrets/` folder, for example to a password manager or an encrypted disk. If you lose it, every login session ends and the database passwords must be reset.

The defaults in `.env` are for Path A: test-mode payments, emails caught by Mailpit, no email verification.

**4.2 Check that the ports are free.** The stack uses these ports on your Mac:

```bash
for p in 3000 3306 5432 8025 8080 8081 8082 8083 8084 8085 8086 8087 8761 8888; do lsof -nP -iTCP:$p -sTCP:LISTEN >/dev/null && echo "port $p is in use"; done; echo "port check done"
```

If only "port check done" appears, carry on. A PostgreSQL (5432) or MySQL (3306) you installed earlier (Postgres.app, `brew services`) is the usual clash. Move ours to other ports on the Mac. The containers still talk to each other as before:

```bash
sed -i '' 's/^POSTGRES_PORT=.*/POSTGRES_PORT=5433/; s/^MYSQL_PORT=.*/MYSQL_PORT=3307/' .env
```

For any other port, find what's using it with `lsof -nP -iTCP:<port> -sTCP:LISTEN` and quit that app.

## Step 5 — Build and start

> Never put `sudo` in front of `mvn`, `docker` or the scripts. It leaves files owned by root that break later builds.

**5.1 Build the services** (5–10 minutes the first time while Maven downloads its libraries):

```bash
mvn clean install -DskipTests
```

It must end with **BUILD SUCCESS**.

**5.2 Start the stack** (5–10 minutes the first time while Docker downloads images; about a minute later on):

```bash
docker compose up -d --build
```

**5.3 Wait until every service is healthy:**

```bash
echo "Waiting for services (up to 10 minutes)..."; end=$((SECONDS+600)); until ! docker compose ps --format '{{.Status}}' | grep -qE 'starting|unhealthy|Restarting'; do [ $SECONDS -gt $end ] && echo "Still not healthy after 10 minutes - see Troubleshooting" && break; sleep 5; done; docker compose ps --format 'table {{.Service}}\t{{.Status}}'
```

Every service should show `Up … (healthy)`. The two `db-backup-*` containers have no health check and show only `Up`. A service stuck on `Restarting` or `Exited`? See Troubleshooting.

> **Shortcut for next time:** `./project-setup.sh up` runs Steps 4.1 and 5 in one go and then streams the logs. Press Ctrl+C to stop watching; the stack keeps running.

## Step 6 — Verify and open the app

**6.1 Run the end-to-end check.** It signs up users, creates a club and an event, pays, issues a ticket and checks in. It must end with `ALL PASSED`:

```bash
./scripts/smoke-test.sh
```

If the first steps fail with 503, the services are still finding each other. Wait 30 seconds and run it again.

**6.2 Open the app:**

```bash
open http://localhost:3000
```

| What | Address |
| --- | --- |
| The Club | http://localhost:3000 |
| Emails the app sends (verification, reset, tickets) | http://localhost:8025 |
| Service registry: all 7 services listed | http://localhost:8761 |

**6.3 Log in as admin** with `admin@theclub.local` and the password this prints:

```bash
cat secrets/admin-password
```

**Path A is done.** Other devices on your Wi-Fi can open `http://<your-mac-ip>:3000`; find the IP with `ipconfig getifaddr en0`. macOS may ask once to allow incoming connections for Docker; click **Allow**.

## Part 2 — Go live from this Mac (Path B)

Part 2 turns the running stack into a public HTTPS site: real emails, real payments, and a certificate that renews itself. Finish Steps 1–6 first.

### P1. Collect these first

| # | What | Where | You end up with |
| --- | --- | --- | --- |
| P1.1 | A **fixed local IP** for the Mac | Router admin page → DHCP reservation for the Mac | e.g. `192.168.1.50` |
| P1.2 | A **public IP** that reaches your router. Compare `curl -s https://ifconfig.me` with the WAN IP on the router's status page; they must match. If they differ, your ISP uses CGNAT and port forwarding won't work, so use a Cloudflare Tunnel instead of P5–P6. | Router status page | Your public IP |
| P1.3 | A domain with an **A record** pointing to that public IP, e.g. `club.example.com`. If your home IP changes, use your DNS provider's dynamic DNS. | Your DNS provider | `<your-host>` |
| P1.4 | **Zoho ZeptoMail:** a verified sending domain (SPF/DKIM records) and a Send Mail token | [EMAIL_SETUP.md](EMAIL_SETUP.md) | Token and a From address on your domain |
| P1.5 | **Razorpay:** live API keys and a webhook to `https://<your-host>/api/v1/payments/webhooks/razorpay` for `payment.captured`, `payment.failed`, `order.paid`, `refund.processed` | Razorpay Dashboard → Settings | Key ID, key secret, webhook secret |
| P1.6 | *Optional:* a **Strava** API app with Authorization Callback Domain = `<your-host>` | [STRAVA_SETUP.md](STRAVA_SETUP.md) | Client ID and client secret |
| P1.7 | An admin mailbox you read | — | `ADMIN_EMAIL` |

### P2. Store the third-party secrets

Each line asks you to paste one value. Nothing is shown on screen or kept in your shell history. This is zsh syntax, Terminal's default shell:

```bash
cd ~/the_club
read -rs "v?ZeptoMail Send Mail token: " && printf %s "$v" > secrets/zeptomail-token; echo
read -rs "v?Razorpay key secret: " && printf %s "$v" > secrets/razorpay-key-secret; echo
read -rs "v?Razorpay webhook secret: " && printf %s "$v" > secrets/razorpay-webhook-secret; echo
unset v
```

Using Strava? Also run:

```bash
read -rs "v?Strava client secret: " && printf %s "$v" > secrets/strava-client-secret; echo; unset v
```

### P3. Switch the settings to production

Edit the first four lines to your values, then paste the whole block. The variable is `CLUB_HOST`, not `HOST`, because zsh already uses `HOST` for the Mac's own name:

```bash
CLUB_HOST=club.example.com
ADMIN=admin@example.com
FROM="The Club <no-reply@example.com>"
RAZORPAY_ID=rzp_live_xxxxxxxxxxxx

set_env() { if grep -q "^$1=" .env; then sed -i '' "s|^$1=.*|$1=$2|" .env; else echo "$1=$2" >> .env; fi; }
set_env SPRING_PROFILES_ACTIVE prod
set_env APP_BASE_URL "https://$CLUB_HOST"
set_env CORS_ALLOWED_ORIGINS "https://$CLUB_HOST"
set_env AUTH_COOKIE_SECURE true
set_env REQUIRE_EMAIL_VERIFICATION true
set_env ADMIN_EMAIL "$ADMIN"
set_env MAIL_PROVIDER zeptomail
set_env MAIL_FROM "$FROM"
set_env PAYMENT_PROVIDER razorpay
set_env RAZORPAY_KEY_ID "$RAZORPAY_ID"
set_env STRAVA_REDIRECT_URI "https://$CLUB_HOST/strava/callback"
set_env STRAVA_WEBHOOK_CALLBACK_URL "https://$CLUB_HOST/api/v1/strava/webhook"
set_env STRAVA_WEBHOOK_VERIFY_TOKEN "$(openssl rand -hex 24)"
set_env WEB_APP_PORT 127.0.0.1:3000
set_env REAL_IP_FROM 172.16.0.0/12,192.168.65.0/24
grep -E "^(SPRING_PROFILES_ACTIVE|APP_BASE_URL|PAYMENT_PROVIDER|MAIL_PROVIDER|MAIL_FROM|WEB_APP_PORT|REAL_IP_FROM)=" .env
```

- **Strava:** also set the client ID with `set_env STRAVA_CLIENT_ID <your id>`.
- **ZeptoMail account outside India:** change `ZEPTOMAIL_API_URL`; see [EMAIL_SETUP.md](EMAIL_SETUP.md).
- `WEB_APP_PORT=127.0.0.1:3000` means only Caddy on this Mac can reach the app. `REAL_IP_FROM` lists Docker Desktop's internal ranges, so rate limiting sees each visitor's real IP; P7 checks this.

### P4. Restart the stack in production mode

Production uses `docker-compose.yml` alone, without the development extras (Mailpit, published database ports). Start fresh so test users and clubs from Path A don't go live; `down -v` **deletes all data**:

```bash
docker compose down -v
mvn clean install -DskipTests
docker compose -f docker-compose.yml up -d --build --remove-orphans
echo "alias dcp='docker compose -f docker-compose.yml'" >> ~/.zshrc && source ~/.zshrc
```

From now on, use `dcp` instead of `docker compose` (for example `dcp ps`, `dcp logs -f auth-service`). Wait for healthy:

```bash
end=$((SECONDS+600)); until ! dcp ps --format '{{.Status}}' | grep -qE 'starting|unhealthy|Restarting'; do [ $SECONDS -gt $end ] && echo "Not healthy after 10 min" && break; sleep 5; done; dcp ps --format 'table {{.Service}}\t{{.Status}}'
```

In production mode a service **refuses to start** while a setting is still a placeholder, such as a missing ZeptoMail token or Razorpay secret. If one keeps restarting, its log names what's missing: `dcp logs --tail=50 <service>`.

### P5. HTTPS with Caddy

Caddy gets a free Let's Encrypt certificate and renews it automatically. Run this in the same Terminal window as P3, so `$CLUB_HOST` is still set:

```bash
brew install caddy
printf '%s {\n    reverse_proxy 127.0.0.1:3000\n}\n' "$CLUB_HOST" > "$(brew --prefix)/etc/Caddyfile"
cat "$(brew --prefix)/etc/Caddyfile"
brew services start caddy
```

If the macOS firewall is on (System Settings → Network → Firewall), allow incoming connections for `caddy` when asked.

### P6. Open the router

On your router's admin page, forward **TCP 80** and **TCP 443** to the Mac's fixed IP from P1.1. Port 80 is needed for certificate issuance and the HTTP→HTTPS redirect. Forward nothing else; databases and services must stay private.

Caddy fetches the certificate within a minute once the ports are open. Watch it with `tail -f "$(brew --prefix)/var/log/caddy.log"`; if that file doesn't exist, run `brew services info caddy` to find the log.

### P7. Verify

Run these on the Mac. Many home routers can't reach their own public address from inside the network; if these time out but the phone test below works, everything is fine:

```bash
curl -sI "https://$CLUB_HOST" | head -1
curl -s "https://$CLUB_HOST/api/v1/auth/.well-known/jwks.json" | head -c 80; echo
```

The first line must say `HTTP/2 200` and the second must start with `{"keys":[`. Then:

1. On your phone, using mobile data rather than Wi-Fi, open `https://<your-host>` and register with a real mailbox. The verification email must arrive.
2. Check the web app sees real visitor IPs: run `dcp logs --tail=5 web-app`. The first field of each line should be the phone's public IP. If it shows an internal address such as `172.x` or `192.168.65.x` that's outside `REAL_IP_FROM`, add its range there and run `dcp up -d web-app`.
3. Log in as your `ADMIN_EMAIL` with the password from `cat secrets/admin-password`, then change it (account menu → Account → Security → Change password).
4. Create a test event, pay a small real amount, then refund it from the event page.

### P8. Keep the Mac serving

A sleeping or logged-out Mac takes the site down. Run once:

```bash
sudo pmset -a sleep 0 disksleep 0 displaysleep 10 womp 1 autorestart 1
pmset -g | grep -E ' sleep|autorestart'
```

This means no system sleep and the screen off after 10 minutes, wake on network, and restart after a power cut.

| Setting | Where | Why |
| --- | --- | --- |
| Automatic login as your user | System Settings → Users & Groups | Docker Desktop and Caddy start only after you log in. FileVault must be off for this, so weigh that trade-off; otherwise log in by hand after every reboot. |
| Start Docker Desktop when you sign in | Docker Desktop → Settings → General | Brings the stack back; containers restart on their own |
| macOS updates: download only, install manually | System Settings → General → Software Update → Automatic updates | Avoids surprise restarts |
| Laptop: keep it plugged in with the lid open | — | A closed lid sleeps the Mac unless an external display is attached |

After any reboot, check with `dcp ps` and `curl -sI https://<your-host>`.

### P9. Finish up

- **HSTS:** once HTTPS works, uncomment the `Strict-Transport-Security` line in `web-app/security-headers.conf`, then run `dcp up -d --build web-app`.
- **Strava webhook** (if used): follow section 5 of [STRAVA_SETUP.md](STRAVA_SETUP.md) using `https://<your-host>`.
- **Off-site backups:** see Operations below.
- **Monitoring:** see Operations below.

## Operations

Run these from `~/the_club`. On Path B, write `dcp` wherever these say `docker compose`.

### Daily commands

```bash
docker compose ps                       # status of every container
docker compose logs -f user-service     # follow one service's log; Ctrl+C stops following
docker compose stop                     # stop everything, data kept
docker compose start                    # start again
docker compose down                     # remove containers, data kept
```

`docker compose down -v` also **deletes all data**. Use it only for a deliberate fresh start.

### Updating to new code

```bash
cd ~/the_club
git pull
./scripts/generate-secrets.sh
mvn clean install -DskipTests
docker compose up -d --build
```

The secrets generator only adds secrets that new versions need; existing ones are never changed. Then check for new settings in the template that your `.env` lacks. Lines starting with `<` are missing; copy them over from `enviroinment_variables.env`:

```bash
diff <(grep -oE '^[A-Z_]+=' enviroinment_variables.env | sort) <(grep -oE '^[A-Z_]+=' .env | sort)
```

### Backups

The databases are dumped every night at 02:30 into a Docker volume and kept for 14 days. To back up now and list the dumps:

```bash
./scripts/backup.sh
docker compose exec db-backup-mysql ls -lh /backups/mysql /backups/postgres
```

That volume sits on the same disk as the databases. **For Path B, also copy it off the volume every night** into a folder that Time Machine or a cloud drive backs up:

```bash
mkdir -p ~/TheClubBackups
(crontab -l 2>/dev/null; echo "0 4 * * * /usr/local/bin/docker run --rm -v the_club_backups:/backups:ro -v $HOME/TheClubBackups:/out alpine cp -r /backups/. /out/") | crontab -
crontab -l
```

Also keep a copy of `~/the_club/secrets/` in your password manager. To restore one database, stop its service first:

```bash
docker compose stop user-service
./scripts/restore.sh userdb mysql/userdb-<timestamp>.sql.gz
docker compose start user-service
```

### Monitoring (optional)

This adds Prometheus, Grafana, Tempo, Loki and alerting, and needs about 2 GB more memory for Docker. Path A:

```bash
docker compose -f docker-compose.yml -f docker-compose.override.yml -f docker-compose.observability.yml up -d
```

Path B:

```bash
dcp -f docker-compose.observability.yml up -d
```

Open Grafana at http://localhost:3001 on the Mac. The user is `admin` and the password is printed by `cat secrets/grafana-admin-password`. Dashboards and alerts are described in [OBSERVABILITY.md](OBSERVABILITY.md).

### Database access

On Path A only, the databases are reachable from the Mac. Use DBeaver, TablePlus or the `mysql`/`psql` clients with:

| Database | Host:port | User | Password |
| --- | --- | --- | --- |
| MySQL (users, auth, clubs, events, notifications) | `localhost:3306` | `root` | `cat secrets/mysql-root-password` |
| PostgreSQL (activities) | `localhost:5432` | `postgres` | `cat secrets/postgres-password` |

Use your new ports if you changed them in Step 4.2. On Path B the ports are closed on purpose; open a database shell through Docker instead:

```bash
dcp exec mysql sh -c 'mysql -uroot -p"$(cat /run/secrets/mysql-root-password)"'
dcp exec postgres psql -U postgres -d activitydb
```

## Troubleshooting

| Problem | Fix |
| --- | --- |
| `command not found: #` or `zsh: no matches found` when pasting | Run `setopt interactivecomments` (Step 1.1), then paste again |
| `xcrun: error: invalid active developer path` | `xcode-select --install` (Step 1.2); this also happens after some macOS upgrades |
| `brew: command not found` on Apple Silicon | Run the two PATH lines in Step 1.3, then open a new Terminal window |
| `release version 21 not supported`, or `mvn -v` shows another Java | `echo $JAVA_HOME` must end in `temurin-21.jdk/...`. Rerun the `JAVA_HOME` lines in Step 1.4 and open a new window. |
| `Cannot connect to the Docker daemon` | Docker Desktop isn't running: `open -a Docker`, then wait for "running" |
| `permission denied` / `Operation not permitted` on files in `~/the_club` | A command was run with `sudo`. Fix ownership: `sudo chown -R $(whoami) ~/the_club` |
| `./project-setup.sh: Permission denied` | `chmod +x project-setup.sh scripts/*.sh` |
| `port is already allocated` / `address already in use` | Rerun the port check in Step 4.2, change the port in `.env`, then `docker compose up -d` |
| Containers exit with code 137, or the Mac is very slow | Out of memory: raise Docker Desktop's memory (Step 2), or stop analytics with `docker compose stop trino` (Path A) |
| 503 / "Service temporarily unavailable" right after starting | Services are still registering. Wait 30–60 seconds. |
| `Bad Gateway` in the browser | `docker compose restart web-app` |
| A service keeps `Restarting` | `docker compose logs --tail=100 <service>`. On Path B the log names the missing production setting (P2/P3). |
| Verification emails never arrive (Path B) | `dcp logs --tail=50 notification-service`. A ZeptoMail `401`/`403` means a wrong token: fix `secrets/zeptomail-token`, then `dcp up -d notification-service`. Queued emails are sent once it's fixed. |
| Caddy has no certificate / the site isn't reachable from outside | Check that DNS points to your public IP (`dig +short <your-host>`), that ports 80 and 443 are forwarded (P6), and that your ISP doesn't use CGNAT (P1.2). Read the Caddy log (P6). |
| Site down after a reboot | Log in to the Mac (or enable automatic login), wait for Docker Desktop, then check `dcp ps` and `brew services list` |
| Docker Desktop's disk image is full | `docker system prune` removes stopped containers and old images; your data volumes are kept |

## Uninstall

This removes the containers, all data, the built images, the code and the secrets. Copy `secrets/` and the backups somewhere else first if you might come back:

```bash
cd ~/the_club && docker compose down -v --rmi local
brew services stop caddy 2>/dev/null; crontab -l 2>/dev/null | grep -v the_club_backups | crontab -
cd ~ && rm -rf ~/the_club
```

The tools themselves (Docker Desktop, Java, Maven, Caddy) can stay, or be removed with `brew uninstall --cask docker-desktop temurin@21` and `brew uninstall maven jq caddy`.
