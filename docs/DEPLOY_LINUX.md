# The Club — Linux Deployment Guide

This guide takes a fresh Debian or Ubuntu machine, with nothing installed, to a running The Club deployment. Paste every command into a terminal as-is, in order. Fedora/RHEL notes are included where they differ.

Using a Mac instead? See [DEPLOY_MACOS.md](DEPLOY_MACOS.md).

## Overview

Everything runs in Docker; Java and Maven are needed only to build the services. Steps 1–5 give you a complete running stack. Part 2 turns a server into a public HTTPS site.

|  | Path A: local stack | Path B: production server |
| --- | --- | --- |
| Good for | Demos, testing, development, a LAN-only club | Real users on the internet |
| Machine | Your laptop/desktop, or any Linux box | A cloud VM / VPS (or a home server) with a public IP |
| Steps | 1 → 5 | 1 → 5, then Part 2 |
| Address | http://localhost:3000 | https://club.yourdomain.com |
| Payments | Test mode (mock) | Real Razorpay |
| Emails | Caught by Mailpit at http://localhost:8025 | Delivered through Zoho ZeptoMail |
| Time | About 30 minutes, mostly downloads | About 1.5 hours, plus DNS propagation |

**What runs:** the web app (nginx) on port 3000 → the gateway → six backend services (auth, user, club, activity, event, notification), plus Eureka (service registry), the config server, MySQL 8, PostgreSQL 15 and nightly database backups. In production, Caddy sits in front and handles HTTPS.

> On a production server you can skip Path A's smoke test and go straight from Step 4 to Part 2, but running it once is a good check that the build works.

## Requirements

|  | Minimum | Recommended |
| --- | --- | --- |
| OS | Debian 12, Ubuntu 22.04, Fedora 39 (x86-64 or ARM64) | Ubuntu 24.04 LTS or Debian 12 |
| CPU | 2 cores | 4 vCPU |
| Memory | 8 GB | 16 GB (8 GB is enough for a production server without monitoring) |
| Free disk | 30 GB | 50 GB SSD |
| Internet | About 3 GB of downloads on the first build |  |
| User | A normal user account (not root) that can use `sudo`; Step 1.1 sets this up on Debian |  |

**Accounts you need**

- **GitHub:** an account the repo owner has added as a collaborator on `guru-pj/the_club`. Accept the email invitation first.
- **Path B only:** a server, a domain, a Zoho ZeptoMail account, a Razorpay account and, optionally, a Strava API app. Part 2 lists exactly what to collect.

Check your distribution and architecture:

```bash
grep PRETTY_NAME /etc/os-release; uname -m; free -h | head -2; df -h ~ | tail -1
```

On a cloud server, log in with `ssh <user>@<server-ip>` and run everything below there.

## Step 1 — Install the tools

**1.1 Get `sudo` (Debian only).** Check whether you already have it:

```bash
sudo true && echo "sudo works"
```

If it says `sudo: command not found` or that you are not in the sudoers file, run this once. It asks for the **root** password:

```bash
su -c "apt update && apt install -y sudo && /usr/sbin/usermod -aG sudo $USER"
```

Then **log out and log back in** (for SSH: `exit`, then reconnect) and run `sudo true` again. It should ask for *your* password and print nothing else.

> Logged in as `root` on a fresh cloud server? Create a normal user first: `apt update && apt install -y sudo && adduser club && usermod -aG sudo club`, then log in as `club`. Everything below expects a normal user.

**1.2 Basic tools:**

```bash
sudo apt update
sudo apt install -y git maven curl jq openssl ca-certificates wget gpg
```

**1.3 Java 21 (a JDK, not just a JRE).** This block uses the distribution's OpenJDK 21 when it exists (Ubuntu 22.04/24.04, Debian 13). Otherwise it installs Eclipse Temurin 21 (Debian 12):

```bash
if apt-cache show openjdk-21-jdk >/dev/null 2>&1; then
  sudo apt install -y openjdk-21-jdk
else
  wget -qO - https://packages.adoptium.net/artifactory/api/gpg/key/public | gpg --dearmor | sudo tee /etc/apt/trusted.gpg.d/adoptium.gpg > /dev/null
  echo "deb https://packages.adoptium.net/artifactory/deb $(awk -F= '/^VERSION_CODENAME/{print $2}' /etc/os-release) main" | sudo tee /etc/apt/sources.list.d/adoptium.list
  sudo apt update && sudo apt install -y temurin-21-jdk
fi
```

If several Java versions are installed, pick 21 for both commands:

```bash
sudo update-alternatives --config java
sudo update-alternatives --config javac
```

**1.4 Docker Engine and Compose v2,** then let your user run Docker without `sudo`:

```bash
curl -fsSL https://get.docker.com | sudo sh
sudo systemctl enable --now docker
sudo usermod -aG docker $USER
```

**Log out and log back in** (or reboot) so the group change applies. To use it right away in the current terminal only, run `newgrp docker`.

**Fedora / RHEL / Rocky:** replace 1.2–1.4 with:

```bash
sudo dnf install -y git maven curl jq openssl java-21-openjdk-devel
curl -fsSL https://get.docker.com | sudo sh
sudo systemctl enable --now docker
sudo usermod -aG docker $USER
```

**1.5 Check everything.** Every line must succeed:

```bash
git --version
java -version          # must say 21
javac -version         # must say 21; "command not found" means only a JRE is installed
mvn -v                 # must show "Java version: 21"
docker compose version
docker run --rm hello-world   # must work WITHOUT sudo
openssl version && jq --version
```

If `mvn -v` shows another Java version, set it explicitly and check again:

```bash
echo "export JAVA_HOME=$(dirname $(dirname $(readlink -f $(which javac))))" >> ~/.bashrc && source ~/.bashrc && mvn -v
```

> From here on, never put `sudo` in front of `mvn`, `docker` or the project's scripts. It leaves files owned by root that break later builds.

## Step 2 — Clone the code

The repository is **private**. When git asks for a password, it wants a **personal access token**, not your GitHub password.

**2.1 Create a token** (once): github.com → your photo → **Settings** → **Developer settings** → **Personal access tokens** → **Fine-grained tokens** → **Generate new token**.

- Repository access: **Only select repositories** → `guru-pj/the_club`
- Permissions: **Contents → Read-only**
- Copy it right away; GitHub shows it only once. Never paste a token into a chat, a document or a file in the repo.

**2.2 Let git remember it in memory for 8 hours,** so updates don't ask every time:

```bash
git config --global credential.helper 'cache --timeout=28800'
```

**2.3 Clone.** Enter your GitHub username, then paste the token as the password:

```bash
cd ~
git clone https://github.com/guru-pj/the_club.git
cd the_club
```

Using SSH keys instead? After [adding an SSH key to GitHub](https://docs.github.com/en/authentication/connecting-to-github-with-ssh), clone with `git clone git@github.com:guru-pj/the_club.git`.

Every command from here on runs inside `~/the_club`.

## Step 3 — Configure

**3.1 Create the settings file and the secrets:**

```bash
cp enviroinment_variables.env .env
./scripts/generate-secrets.sh
```

The second command creates the `secrets/` folder. It holds the signing keys, the internal service key, database passwords and the first admin's password, and is never committed to git. The admin password is printed once; see it again any time with `cat secrets/admin-password`.

> Back up `secrets/` to a password manager or secret store. If you lose it, every session ends and the database passwords must be reset.

The defaults in `.env` are for Path A: test-mode payments, emails caught by Mailpit, no email verification.

**3.2 Check that the ports are free.** The stack uses these ports on the machine:

```bash
for p in 3000 3306 5432 8025 8080 8081 8082 8083 8084 8085 8086 8087 8761 8888; do (echo > /dev/tcp/127.0.0.1/$p) 2>/dev/null && echo "port $p is in use"; done; echo "port check done"
```

If only "port check done" appears, carry on. A locally installed PostgreSQL (5432) or MySQL/MariaDB (3306) is the usual clash. Move ours to other host ports; the containers still talk to each other as before:

```bash
sed -i 's/^POSTGRES_PORT=.*/POSTGRES_PORT=5433/; s/^MYSQL_PORT=.*/MYSQL_PORT=3307/' .env
```

For any other port, see what's using it with `sudo ss -ltnp | grep :<port>`.

## Step 4 — Build and start

**4.1 Build the services** (5–10 minutes the first time while Maven downloads libraries). It must end with **BUILD SUCCESS**:

```bash
mvn clean install -DskipTests
```

**4.2 Start the stack** (5–10 minutes the first time while Docker downloads images; about a minute later on):

```bash
docker compose up -d --build
```

**4.3 Wait until every service is healthy:**

```bash
echo "Waiting for services (up to 10 minutes)..."; end=$((SECONDS+600)); until ! docker compose ps --format '{{.Status}}' | grep -qE 'starting|unhealthy|Restarting'; do [ $SECONDS -gt $end ] && echo "Still not healthy after 10 minutes - see Troubleshooting" && break; sleep 5; done; docker compose ps --format 'table {{.Service}}\t{{.Status}}'
```

Every service should show `Up … (healthy)`. The two `db-backup-*` containers have no health check and show only `Up`. Anything on `Restarting` or `Exited`? See Troubleshooting.

> **Shortcut for next time:** `./project-setup.sh up` runs Steps 3.1 and 4 in one go, then streams the logs. Press Ctrl+C to stop watching; the stack keeps running.

## Step 5 — Verify and open the app

**5.1 Run the end-to-end check.** It signs up users, creates a club and an event, pays, issues a ticket and checks in. It must end with `ALL PASSED`:

```bash
./scripts/smoke-test.sh
```

If the first steps fail with 503, the services are still finding each other. Wait 30 seconds and run it again.

**5.2 Open the app** in a browser on the same machine:

| What | Address |
| --- | --- |
| The Club | http://localhost:3000 |
| Emails the app sends (verification, reset, tickets) | http://localhost:8025 |
| Service registry: all 7 services listed | http://localhost:8761 |

On a remote server without a browser, tunnel the ports from your own computer: `ssh -L 3000:localhost:3000 -L 8025:localhost:8025 <user>@<server-ip>`, then open the same addresses locally.

**5.3 Log in as admin** with `admin@theclub.local` and the password this prints:

```bash
cat secrets/admin-password
```

**Path A is done.** Other devices on your network can open `http://<machine-ip>:3000`; find the IP with `hostname -I`. Only the web app is reachable from other machines: databases, services and Mailpit listen on `127.0.0.1` only. Docker opens published ports by itself, even with ufw enabled, so no firewall rule is needed.

## Part 2 — Production on a server (Path B)

Part 2 turns the server into a public HTTPS site with real emails, real payments and a certificate that renews itself. Finish Steps 1–4 on the server first.

### P1. Collect these first

| # | What | Where | You end up with |
| --- | --- | --- | --- |
| P1.1 | A server with 4 vCPU, 8 GB RAM, 40 GB disk and a public IP. In its cloud firewall / security group, open **TCP 22, 80, 443** only. | Any cloud or VPS (AWS Lightsail/EC2, DigitalOcean, Hetzner, Azure, GCP) | SSH access as a sudo user |
| P1.2 | A domain with an **A record** pointing to the server's IP, e.g. `club.example.com` | Your DNS provider | `<your-host>` |
| P1.3 | **Zoho ZeptoMail:** a verified sending domain (SPF/DKIM records) and a Send Mail token | [EMAIL_SETUP.md](EMAIL_SETUP.md) | Token and a From address on your domain |
| P1.4 | **Razorpay:** live API keys, and a webhook to `https://<your-host>/api/v1/payments/webhooks/razorpay` for `payment.captured`, `payment.failed`, `order.paid`, `refund.processed` | Razorpay Dashboard → Settings | Key ID, key secret, webhook secret |
| P1.5 | *Optional:* a **Strava** API app with Authorization Callback Domain = `<your-host>` | [STRAVA_SETUP.md](STRAVA_SETUP.md) | Client ID and client secret |
| P1.6 | An admin mailbox you read | — | `ADMIN_EMAIL` |

Check DNS before continuing. It must print the server's IP:

```bash
getent hosts club.example.com
```

### P2. Store the third-party secrets

Each line asks you to paste one value. Nothing is shown on screen or saved in your shell history:

```bash
cd ~/the_club
read -rsp "ZeptoMail Send Mail token: " v && printf %s "$v" > secrets/zeptomail-token; echo
read -rsp "Razorpay key secret: " v && printf %s "$v" > secrets/razorpay-key-secret; echo
read -rsp "Razorpay webhook secret: " v && printf %s "$v" > secrets/razorpay-webhook-secret; echo
unset v
```

Using Strava? Also run:

```bash
read -rsp "Strava client secret: " v && printf %s "$v" > secrets/strava-client-secret; echo; unset v
```

### P3. Switch the settings to production

Edit the first four lines to your values, then paste the whole block:

```bash
CLUB_HOST=club.example.com
ADMIN=admin@example.com
FROM="The Club <no-reply@example.com>"
RAZORPAY_ID=rzp_live_xxxxxxxxxxxx

set_env() { if grep -q "^$1=" .env; then sed -i "s|^$1=.*|$1=$2|" .env; else echo "$1=$2" >> .env; fi; }
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
set_env REAL_IP_FROM 172.16.0.0/12
grep -E "^(SPRING_PROFILES_ACTIVE|APP_BASE_URL|PAYMENT_PROVIDER|MAIL_PROVIDER|MAIL_FROM|WEB_APP_PORT|REAL_IP_FROM)=" .env
```

- **Strava:** also run `set_env STRAVA_CLIENT_ID <your id>`.
- **ZeptoMail account outside India:** change `ZEPTOMAIL_API_URL`; see [EMAIL_SETUP.md](EMAIL_SETUP.md).
- `WEB_APP_PORT=127.0.0.1:3000` means only Caddy on this server can reach the app. `REAL_IP_FROM` trusts Docker's bridge network, where Caddy's requests arrive from, so rate limiting sees each visitor's real IP.

### P4. Restart the stack in production mode

Production uses `docker-compose.yml` alone. That leaves out the development extras: Mailpit, Trino, and the database and service ports on localhost. Start fresh so test data from Step 5 doesn't go live; `down -v` **deletes all data**:

```bash
docker compose down -v
mvn clean install -DskipTests
docker compose -f docker-compose.yml up -d --build --remove-orphans
echo "alias dcp='docker compose -f docker-compose.yml'" >> ~/.bashrc && source ~/.bashrc
```

From now on, use `dcp` instead of `docker compose` (for example `dcp ps`, `dcp logs -f auth-service`). Wait for healthy:

```bash
end=$((SECONDS+600)); until ! docker compose -f docker-compose.yml ps --format '{{.Status}}' | grep -qE 'starting|unhealthy|Restarting'; do [ $SECONDS -gt $end ] && echo "Not healthy after 10 min" && break; sleep 5; done; docker compose -f docker-compose.yml ps --format 'table {{.Service}}\t{{.Status}}'
```

In production mode a service **refuses to start** while a setting is still a placeholder, such as a missing ZeptoMail token or Razorpay secret. If one keeps restarting, its log names what's missing: `dcp logs --tail=50 <service>`.

### P5. Firewall

Allow SSH first, so you don't lock yourself out, then web traffic:

```bash
sudo apt install -y ufw
sudo ufw allow OpenSSH
sudo ufw allow 80/tcp
sudo ufw allow 443/tcp
sudo ufw --force enable
sudo ufw status
```

Docker writes its own firewall rules, so ufw does **not** protect container ports. The real protection is that, in production mode, only the web app is published, and only on `127.0.0.1` (P3). Check that nothing else listens publicly:

```bash
sudo ss -ltnp | grep -vE '127\.0\.0\.|\[::1\]'
```

Only `sshd` (port 22) should be listed under the header line now; Caddy's ports 80 and 443 join it after P6.

### P6. HTTPS with Caddy

Caddy gets a free Let's Encrypt certificate and renews it automatically. Run this in the same terminal as P3, so `$CLUB_HOST` is still set:

```bash
sudo apt install -y debian-keyring debian-archive-keyring apt-transport-https
curl -1sLf 'https://dl.cloudsmith.io/public/caddy/stable/gpg.key' | sudo gpg --dearmor -o /usr/share/keyrings/caddy-stable-archive-keyring.gpg
curl -1sLf 'https://dl.cloudsmith.io/public/caddy/stable/debian.deb.txt' | sudo tee /etc/apt/sources.list.d/caddy-stable.list
sudo apt update && sudo apt install -y caddy
printf '%s {\n    reverse_proxy 127.0.0.1:3000\n}\n' "$CLUB_HOST" | sudo tee /etc/caddy/Caddyfile
sudo systemctl reload caddy
```

Fedora: `sudo dnf install -y 'dnf-command(copr)' && sudo dnf copr enable -y @caddy/caddy && sudo dnf install -y caddy && sudo systemctl enable --now caddy`, then write the Caddyfile as above.

Watch the certificate being issued with `sudo journalctl -u caddy -f`. Look for `certificate obtained successfully`, then press Ctrl+C.

### P7. Verify

```bash
curl -sI "https://$CLUB_HOST" | head -1
curl -s "https://$CLUB_HOST/api/v1/auth/.well-known/jwks.json" | head -c 80; echo
```

The first line must say `HTTP/2 200` and the second must start with `{"keys":[`. Then:

1. In a browser, open `https://<your-host>` and register with a real mailbox. The verification email must arrive.
2. Check that real visitor IPs reach the app: run `dcp logs --tail=5 web-app`. The first field of each request line should be your own public IP, not `172.x.x.x`.
3. Log in as your `ADMIN_EMAIL` with the password from `cat secrets/admin-password`. Change it at account menu → Account → Security → Change password.
4. Create a test event, pay a small real amount, then refund it from the event page.

(`./scripts/smoke-test.sh` is for Path A only: it needs mock payments.)

### P8. Harden the server

| Do | Command / where | Why |
| --- | --- | --- |
| Automatic security updates | `sudo apt install -y unattended-upgrades && sudo dpkg-reconfigure -plow unattended-upgrades` | OS patches without manual work |
| SSH keys only | After confirming key login works: set `PasswordAuthentication no` in `/etc/ssh/sshd_config`, then `sudo systemctl reload ssh` | Stops password guessing |
| HSTS | Uncomment `Strict-Transport-Security` in `web-app/security-headers.conf`, then `dcp up -d --build web-app` | Browsers always use HTTPS |
| Survives reboots | Already set: Docker and Caddy are systemd services and containers use `restart: unless-stopped`. Test once with `sudo reboot`, then `dcp ps`. | No manual restart after maintenance |
| Off-site backups | See Operations | A disk failure doesn't lose data |
| Back up `secrets/` | Password manager or secret store | Needed to restore |

### P9. Optional extras

- **Strava webhook:** follow section 5 of [STRAVA_SETUP.md](STRAVA_SETUP.md) using `https://<your-host>`.
- **Monitoring:** see Operations.
- **Kubernetes** instead of a single server: [KUBERNETES.md](KUBERNETES.md) (Helm chart).

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

The secrets generator only adds secrets that new versions need; existing ones never change. Then look for new settings your `.env` lacks. Lines starting with `<` are missing; copy them from `enviroinment_variables.env`:

```bash
diff <(grep -oE '^[A-Z_]+=' enviroinment_variables.env | sort) <(grep -oE '^[A-Z_]+=' .env | sort)
```

### Backups

The databases are dumped every night at 02:30 into a Docker volume and kept for 14 days. To back up now and list the dumps:

```bash
./scripts/backup.sh
docker compose exec db-backup-mysql ls -lh /backups/mysql /backups/postgres
```

That volume is on the same disk as the databases. **On Path B, copy it off the server every night.** For example, to an S3 bucket (put the AWS keys in `~/.aws-backup.env` as `AWS_ACCESS_KEY_ID=…` and `AWS_SECRET_ACCESS_KEY=…`, then `chmod 600` it):

```bash
(crontab -l 2>/dev/null; echo "0 4 * * * docker run --rm --env-file $HOME/.aws-backup.env -v the_club_backups:/backups:ro amazon/aws-cli s3 sync /backups s3://<bucket>/the-club/") | crontab -
crontab -l
```

Or to another machine over SSH: `0 4 * * * docker run --rm -v the_club_backups:/b:ro -v $HOME/club-backups:/out alpine cp -r /b/. /out/ && rsync -a $HOME/club-backups/ backup@other-host:club/`.

To restore one database, stop its service first:

```bash
docker compose stop user-service
./scripts/restore.sh userdb mysql/userdb-<timestamp>.sql.gz
docker compose start user-service
```

Restore into a test machine now and then. A backup you have never restored isn't proven to work.

### Monitoring (optional)

This adds Prometheus, Grafana, Tempo, Loki and alerting, and needs about 2 GB more RAM. Path A:

```bash
docker compose -f docker-compose.yml -f docker-compose.override.yml -f docker-compose.observability.yml up -d
```

Path B:

```bash
dcp -f docker-compose.observability.yml up -d
```

Grafana runs at http://localhost:3001. On a server, reach it through SSH from your computer: `ssh -L 3001:127.0.0.1:3001 <user>@<server>`. The user is `admin` and the password is printed by `cat secrets/grafana-admin-password`. Dashboards and alerts are described in [OBSERVABILITY.md](OBSERVABILITY.md).

### Database access

Open a database shell (Path B: `dcp` instead of `docker compose`):

```bash
docker compose exec mysql sh -c 'mysql -uroot -p"$(cat /run/secrets/mysql-root-password)"'
docker compose exec postgres psql -U postgres -d activitydb
```

MySQL holds `userdb`, `authdb`, `clubdb`, `eventdb` and `notificationdb`; PostgreSQL holds `activitydb`. On Path A, GUI tools such as DBeaver can also connect to `localhost:3306` (user `root`) and `localhost:5432` (user `postgres`), with the passwords in `secrets/mysql-root-password` and `secrets/postgres-password`.

## Troubleshooting

| Problem | Fix |
| --- | --- |
| `sudo: command not found` / not in sudoers (Debian) | Step 1.1, then log out and back in |
| `Unable to locate package openjdk-21-jdk` | Use the Step 1.3 block; it falls back to Temurin 21 |
| `permission denied while trying to connect to the Docker daemon socket` | Log out and back in after `usermod -aG docker $USER`, or run `newgrp docker` |
| `Cannot connect to the Docker daemon` | `sudo systemctl enable --now docker` |
| `release version 21 not supported`, or `mvn -v` shows another Java | `sudo update-alternatives --config java` and `--config javac`, or set `JAVA_HOME` (Step 1.5) |
| `Failed to delete …/target/…` or other permission errors in the build | A command ran with `sudo`. Fix ownership: `sudo chown -R $USER:$USER ~/the_club` |
| `./project-setup.sh: Permission denied` | `chmod +x project-setup.sh scripts/*.sh` |
| `port is already allocated` / `address already in use` | Rerun the port check in Step 3.2, change the port in `.env`, then `docker compose up -d` |
| Containers exit with code 137 / the machine is very slow | Out of memory. Add RAM or swap (`sudo fallocate -l 4G /swapfile && sudo chmod 600 /swapfile && sudo mkswap /swapfile && sudo swapon /swapfile`), or stop analytics on Path A with `docker compose stop trino` |
| 503 / "Service temporarily unavailable" right after starting | Services are still registering. Wait 30–60 seconds. |
| `Bad Gateway` in the browser | `docker compose restart web-app` |
| A service keeps `Restarting` | `docker compose logs --tail=100 <service>`. On Path B the log names the missing production setting (P2/P3). |
| Verification emails never arrive (Path B) | `dcp logs --tail=50 notification-service`. A ZeptoMail `401`/`403` means a wrong token: fix `secrets/zeptomail-token`, then `dcp up -d notification-service`. Queued emails go out once it's fixed. |
| Caddy can't get a certificate | DNS must point at the server (`getent hosts <your-host>`), and ports 80 and 443 must be open in both ufw and the cloud firewall. Check `sudo journalctl -u caddy --since "10 min ago"`. |
| All users share one rate limit / everyone gets 429 on login | `REAL_IP_FROM` is missing or wrong. Set it as in P3, then `dcp up -d web-app`. |
| `git pull` refuses: "local changes would be overwritten" | `git stash`, then `git pull` |

## Uninstall

This removes the containers, all data, the built images, the code and the secrets. Copy `secrets/` and the backups elsewhere first if you might come back:

```bash
cd ~/the_club && docker compose down -v --rmi local
crontab -l 2>/dev/null | grep -v the_club_backups | crontab -
cd ~ && rm -rf ~/the_club
```

Path B: also stop Caddy with `sudo systemctl disable --now caddy`. The tools can stay, or be removed with `sudo apt remove caddy docker-ce docker-ce-cli containerd.io maven` plus your Java package (`openjdk-21-jdk` or `temurin-21-jdk`).
