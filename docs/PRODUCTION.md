# The Club — Production Deployment Guide

This guide covers running The Club on a single Docker host: a VM or bare-metal server with Docker Engine and Compose v2. The [roadmap](#10-roadmap-beyond-a-single-host) lists what changes for multi-host or Kubernetes deployments.

For a guided walkthrough from a machine with nothing installed, see [DEPLOY_LINUX.md](DEPLOY_LINUX.md) or [DEPLOY_MACOS.md](DEPLOY_MACOS.md). This guide is the reference behind them.

---

## 0. From zero to live: the complete checklist

This section takes a **fresh Debian 12 / Ubuntu 22.04+ server** to a live HTTPS deployment. Part A is what only you can do: create accounts, get keys, set DNS. Part B is copy-paste commands. The numbered sections after it explain each part in depth.

### Part A: accounts and values you must prepare first

| # | What | Where | You'll end up with |
|---|---|---|---|
| A1 | A Linux server: 4 vCPU, 8 GB RAM, 40 GB disk, public IP, ports 80 and 443 open in its firewall / security group | Any cloud or VPS | SSH access as a user with `sudo` |
| A2 | A domain name with an **A record** pointing to the server's IP (e.g. `club.example.com`) | Your DNS provider | `<your-host>` |
| A3 | Access to the private GitHub repo plus a personal access token | Ask the repo owner; github.com → Settings → Developer settings | A token for `git clone` |
| A4 | **Zoho ZeptoMail** account, a verified sending domain (SPF/DKIM DNS records) and a Send Mail token | [`EMAIL_SETUP.md`](EMAIL_SETUP.md) | Token and a `From` address on your domain |
| A5 | **Razorpay** account with live API keys, plus a webhook for `https://<your-host>/api/v1/payments/webhooks/razorpay` | Razorpay Dashboard → Settings → API Keys / Webhooks | Key ID, key secret and webhook secret |
| A6 | *(optional)* **Strava** API application | [`STRAVA_SETUP.md`](STRAVA_SETUP.md) | Client ID and client secret |
| A7 | An admin mailbox you control | n/a | `ADMIN_EMAIL` |

### Part B: commands (run on the server, in order)

**B1. Install the tools.** Follow the README's [Step 1A](../README.md#step-1a--install-the-tools-on-linux-ubuntu--debian), including the Debian "no `sudo`" note, then log out and back in. Check:
```bash
java -version && javac -version && mvn -v && docker compose version && docker run --rm hello-world
```

**B2. Clone and generate secrets.** git asks for your GitHub username and the token from A3:
```bash
cd ~ && git clone https://github.com/guru-pj/the_club.git && cd the_club
cp enviroinment_variables.env .env
./scripts/generate-secrets.sh
```
Save the printed admin password, and back up the whole `secrets/` folder to a password manager or secret store.

**B3. Put your third-party secrets in place.** Each `read -rs` asks you to paste a value. Nothing is echoed or saved in your shell history:
```bash
read -rsp "ZeptoMail Send Mail token: " v && printf %s "$v" > secrets/zeptomail-token; echo
read -rsp "Razorpay key secret: " v && printf %s "$v" > secrets/razorpay-key-secret; echo
read -rsp "Razorpay webhook secret: " v && printf %s "$v" > secrets/razorpay-webhook-secret; echo
# optional, only if you use Strava:
read -rsp "Strava client secret: " v && printf %s "$v" > secrets/strava-client-secret; echo
unset v
```

**B4. Switch `.env` to production.** Edit the first three lines to match your values, then paste the whole block:
```bash
HOST=club.example.com                 # A2: your domain
ADMIN=admin@example.com               # A7: your admin mailbox
FROM="The Club <no-reply@example.com>"  # A4: an address on your verified ZeptoMail domain
RAZORPAY_ID=rzp_live_xxxxxxxxxxxx     # A5: Razorpay key ID (not secret)

set_env() { if grep -q "^$1=" .env; then sed -i.bak "s|^$1=.*|$1=$2|" .env && rm -f .env.bak; else echo "$1=$2" >> .env; fi; }
set_env SPRING_PROFILES_ACTIVE prod
set_env APP_BASE_URL "https://$HOST"
set_env CORS_ALLOWED_ORIGINS "https://$HOST"
set_env AUTH_COOKIE_SECURE true
set_env REQUIRE_EMAIL_VERIFICATION true
set_env ADMIN_EMAIL "$ADMIN"
set_env MAIL_PROVIDER zeptomail
set_env MAIL_FROM "$FROM"
set_env PAYMENT_PROVIDER razorpay
set_env RAZORPAY_KEY_ID "$RAZORPAY_ID"
set_env STRAVA_REDIRECT_URI "https://$HOST/strava/callback"
set_env STRAVA_WEBHOOK_CALLBACK_URL "https://$HOST/api/v1/strava/webhook"
set_env WEB_APP_PORT 127.0.0.1:3000
set_env REAL_IP_FROM 172.16.0.0/12
grep -E "^(SPRING_PROFILES_ACTIVE|APP_BASE_URL|PAYMENT_PROVIDER|MAIL_PROVIDER|WEB_APP_PORT|REAL_IP_FROM)=" .env
```
Using Strava? Also set `STRAVA_CLIENT_ID` in `.env`. ZeptoMail outside India? Change `ZEPTOMAIL_API_URL`; see [`EMAIL_SETUP.md`](EMAIL_SETUP.md).

**B5. HTTPS with Caddy.** Caddy obtains and renews Let's Encrypt certificates automatically:
```bash
sudo apt install -y debian-keyring debian-archive-keyring apt-transport-https curl gpg
curl -1sLf 'https://dl.cloudsmith.io/public/caddy/stable/gpg.key' | sudo gpg --dearmor -o /usr/share/keyrings/caddy-stable-archive-keyring.gpg
curl -1sLf 'https://dl.cloudsmith.io/public/caddy/stable/debian.deb.txt' | sudo tee /etc/apt/sources.list.d/caddy-stable.list
sudo apt update && sudo apt install -y caddy
printf '%s {\n    reverse_proxy 127.0.0.1:3000\n}\n' "$HOST" | sudo tee /etc/caddy/Caddyfile
sudo systemctl reload caddy
```

**B6. Build and start.** Pass `-f docker-compose.yml` to use the production file only, without the dev extras:
```bash
mvn clean install -DskipTests
docker compose -f docker-compose.yml up -d --build
echo "Waiting for healthy services..."; end=$((SECONDS+600)); until ! docker compose -f docker-compose.yml ps --format '{{.Status}}' | grep -qE 'starting|unhealthy|Restarting'; do [ $SECONDS -gt $end ] && echo "Not healthy after 10 min - check: docker compose -f docker-compose.yml logs" && break; sleep 5; done; docker compose -f docker-compose.yml ps --format 'table {{.Service}}\t{{.Status}}'
```
With `SPRING_PROFILES_ACTIVE=prod`, a service **refuses to start** if something is still a placeholder, for example mock payments, a missing ZeptoMail token or a missing Razorpay secret. If one keeps restarting, its log says exactly what's missing: `docker compose -f docker-compose.yml logs --tail=50 <service>`.

**B7. Verify.**
```bash
curl -sI "https://$HOST" | head -1                                         # HTTP/2 200
curl -s "https://$HOST/api/v1/auth/.well-known/jwks.json" | head -c 80; echo  # {"keys":[...
```
Then in a browser:
1. Open `https://<your-host>` and register with a real mailbox. The verification email should arrive via ZeptoMail.
2. Log in as `ADMIN_EMAIL` with the password from `cat secrets/admin-password`.
3. Make a small real payment for a test event and refund it from the event page.

(`scripts/smoke-test.sh` is for development stacks only, because it needs mock payments.)

**B8. Turn on the remaining production features.**
- **Monitoring:** see [`OBSERVABILITY.md`](OBSERVABILITY.md). Run `docker compose -f docker-compose.yml -f docker-compose.observability.yml up -d`, then open Grafana through an SSH tunnel: `ssh -L 3001:127.0.0.1:3001 <server>`.
- **HSTS:** once HTTPS works, uncomment the `Strict-Transport-Security` line in `web-app/security-headers.conf` and run `docker compose -f docker-compose.yml up -d --build web-app`.
- **Strava webhook subscription** (if you use Strava): [`STRAVA_SETUP.md`](STRAVA_SETUP.md).
- **Offsite backups:** copy the `backups` volume off the server regularly (§7).

**Updating later:**
```bash
cd ~/the_club && git pull && ./scripts/generate-secrets.sh && mvn clean install -DskipTests && docker compose -f docker-compose.yml up -d --build
```
`generate-secrets.sh` only adds secrets that new versions need; existing ones are never changed. After pulling, compare `.env` with `enviroinment_variables.env` for new settings: `diff <(grep -oE '^[A-Z_]+=' enviroinment_variables.env | sort) <(grep -oE '^[A-Z_]+=' .env | sort)`. Lines starting with `<` are missing from your `.env`.

---

## 1. Architecture at the edge

```
Internet ──HTTPS──▶ TLS proxy / load balancer ──HTTP──▶ web-app (nginx, :WEB_APP_PORT)
                                                          ├─ /        static SPA (security headers, CSP)
                                                          └─ /api/*   ──▶ gateway-service (:8080, internal)
                                                                           rate limits, body limit, routing
                                                                           └─▶ user / auth / activity / club / event / notification
```

- **Network exposure:**
  - Only `web-app` is published to the network.
  - `gateway-service` is published on `127.0.0.1` only, for smoke tests and operations from the host.
  - Databases, Eureka and the services are unreachable from outside the Compose network.
- **Blocked internal APIs:** the gateway never routes `/internal/**`, which are the service-to-service endpoints protected by the internal API key.
- **Client IPs:** nginx sets `X-Real-IP` / `X-Forwarded-For`, and the gateway trusts them (`GATEWAY_TRUST_FORWARDED_FOR=true`) for per-client rate limiting. This is safe only because the gateway is not reachable from outside.

## 2. Prerequisites

- Linux host with at least 6 GB RAM, Docker Engine 24+ and the Compose v2 plugin.
- JDK 21 and Maven 3.9+ to build the jars. CI builds can push images to GHCR instead; see section 9.
- `openssl` and `bash` (used by `scripts/generate-secrets.sh`).
- A DNS name and a TLS certificate. For TLS options, see section 4.

## 3. Secrets and configuration

### 3.1 Generate secrets
```bash
./scripts/generate-secrets.sh
```
This creates `./secrets/`. The directory is mode 700 and gitignored, and each file is mounted read-only only into the containers that need it.

| File | Used by | Purpose |
|---|---|---|
| `theclub.jwt.private-key` | auth-service | RS256 signing key (PKCS#8). Never leaves auth-service. |
| `theclub.jwt.public-key` | every service | Verifies access tokens |
| `theclub.security.internal-api-key` | every service | Authenticates `/internal/**` calls |
| `mysql-root-password`, `postgres-password` | DB containers, backups | Database administrators |
| `db-<service>-password` | one service each | Least-privilege DB user (`theclub_<service>`) that can only access its own database |
| `admin-password` | user-service | First platform admin; printed once by the generator |
| `zeptomail-token`, `smtp-password`, `razorpay-key-secret`, `razorpay-webhook-secret`, `strava-client-secret` | notification / event / activity | Created empty. Fill in the ones you use (§0 B3). |
| `zeptomail-webhook-token`, `strava-token-encryption-key`, `grafana-admin-password` | notification / activity / grafana | Generated. Bounce-webhook secret, Strava token encryption key, Grafana login. |

Spring loads these through `SPRING_CONFIG_IMPORT=optional:configtree:/run/secrets/`. Each file is mounted under the name of the placeholder the service resolves (for example `/run/secrets/JWT_PUBLIC_KEY` for `${JWT_PUBLIC_KEY}`), so no secret ever appears in environment variables, `docker inspect` output or `.env`.

**Managed secret stores** (Vault, AWS Secrets Manager, GCP Secret Manager): render the same file names into `./secrets/` at deploy time, for example with the Vault Agent or a CI step, or mount them as Kubernetes Secrets. Nothing else changes.

> Store a copy of `secrets/` somewhere safe (a password manager or secret store). If it is lost, every session is invalidated and the database user passwords have to be reset.

### 3.2 Configure `.env`
```bash
cp enviroinment_variables.env .env
```
For production, change these values from the development defaults:

| Variable | Production value |
|---|---|
| `SPRING_PROFILES_ACTIVE` | `prod` (enables fail-fast checks: no placeholder secrets, no mock payments) |
| `APP_BASE_URL` | `https://<your-host>` (used in password-reset and verification emails) |
| `AUTH_COOKIE_SECURE` | `true` (the refresh cookie is sent over HTTPS only) |
| `REQUIRE_EMAIL_VERIFICATION` | `true` |
| `PAYMENT_PROVIDER` | `razorpay`, plus `RAZORPAY_KEY_ID` and the two Razorpay secret files |
| `MAIL_PROVIDER` | `zeptomail`. The token goes in `secrets/zeptomail-token`; see [`EMAIL_SETUP.md`](EMAIL_SETUP.md). `log` is refused in prod. |
| `MAIL_FROM` | `Name <address@your-verified-domain>` |
| `MAIL_HOST` / `MAIL_PORT` / `MAIL_USERNAME` / `MAIL_STARTTLS` | Only if `MAIL_PROVIDER=smtp` (any SMTP provider); the password goes in `secrets/smtp-password` |
| `WEB_APP_PORT` / `REAL_IP_FROM` | `127.0.0.1:3000` / `172.16.0.0/12` when a TLS proxy such as Caddy runs on the same host (§4) |
| `ADMIN_EMAIL` | A real mailbox you control |
| `CORS_ALLOWED_ORIGINS` | `https://<your-host>`, or leave as is: the web app is same-origin |
| `STRAVA_*` | If you use Strava: `STRAVA_REDIRECT_URI=https://<your-host>/strava/callback` |

### 3.3 Third-party callbacks
- **Razorpay webhook:** create it in the Razorpay dashboard pointing at `https://<your-host>/api/v1/payments/webhooks/razorpay`, with events `payment.captured`, `payment.failed`, `order.paid` and `refund.processed`. Its secret goes in `secrets/razorpay-webhook-secret`.
- **Strava:** set the app's *Authorization Callback Domain* to your host, and register the webhook at `https://<your-host>/api/v1/strava/webhook` using `STRAVA_WEBHOOK_VERIFY_TOKEN`.

## 4. TLS

Terminate HTTPS in front of the `web-app` container and forward plain HTTP to `WEB_APP_PORT`. Any of these works:

- **Cloud load balancer** (AWS ALB, GCP HTTPS LB, Azure App Gateway) with a managed certificate.
- **Caddy** on the host, which gets certificates automatically. Example `Caddyfile`:
  ```
  theclub.example.com {
      reverse_proxy 127.0.0.1:3000
  }
  ```
  Set `WEB_APP_PORT=127.0.0.1:3000` in `.env` so that only Caddy can reach the container.
- **nginx or Traefik** with Let's Encrypt.

If the TLS proxy adds `X-Forwarded-For` (Caddy, nginx, Traefik and cloud load balancers do), set **`REAL_IP_FROM`** in `.env` to the proxy's address range, comma-separated if there are several:
- for a proxy on the same host, use `172.16.0.0/12` (the Docker bridge);
- for a load balancer, use its subnet.

Then recreate the web app with `docker compose -f docker-compose.yml up -d web-app`. Without this, every client appears to come from the proxy's IP, and **all users share one rate-limit bucket**: 10 login attempts per minute for the whole site.

Once the site is HTTPS-only, enable HSTS by uncommenting the `Strict-Transport-Security` line in `web-app/security-headers.conf`.

## 5. Deploy

```bash
mvn clean install -DskipTests                       # build the service jars
docker compose -f docker-compose.yml up -d --build  # production: base file only, no dev override
```

> `docker compose up` without `-f` also loads `docker-compose.override.yml`. That file is for development: it publishes database and service ports on localhost and adds Mailpit and Trino. **Always pass `-f docker-compose.yml` in production.**

**First start:**
- MySQL and PostgreSQL run `docker/*/initdb/` to create one database and one least-privilege user per service.
- Startup takes 1–2 minutes. Every Spring service has a container healthcheck on `/actuator/health`, and dependent services wait for databases and Eureka to be healthy.

**Upgrading an installation created before per-service database users existed:** the init scripts only run on an empty volume, so run them once by hand. They are idempotent.
```bash
docker compose -f docker-compose.yml exec mysql bash /docker-entrypoint-initdb.d/01-service-users.sh
docker compose -f docker-compose.yml exec postgres bash /docker-entrypoint-initdb.d/01-service-user.sh
```

**Updating:**
```bash
git pull
mvn clean install -DskipTests
docker compose -f docker-compose.yml up -d --build
```

## 6. Health and operations

```bash
docker compose -f docker-compose.yml ps                  # every service should be "healthy"
docker compose -f docker-compose.yml logs -f auth-service
curl -s http://127.0.0.1:8080/actuator/health            # gateway, from the host
```

- **Container logs:** json-file driver, capped at 5 × 20 MB per container.
- **Memory:** each container has a memory limit; JVM heaps size themselves to 75% of it (`-XX:MaxRAMPercentage=75`).
- **Smoke test:** works against a development stack (it needs `PAYMENT_PROVIDER=mock` and `REQUIRE_EMAIL_VERIFICATION=false`), so run it in staging, not production: `./scripts/smoke-test.sh`.

### Edge protections (gateway)

| Protection | Default | Setting |
|---|---|---|
| Strict rate limit: login, register, password reset, email verification (POST) | 10 / minute / IP | `RATE_LIMIT_STRICT_PER_MINUTE` |
| General rate limit: every `/api/**` call | 300 / minute / IP | `RATE_LIMIT_GENERAL_PER_MINUTE` |
| Request body limit | 1 MB (413) | `GATEWAY_MAX_BODY_BYTES` |
| Security headers | `nosniff`, `Referrer-Policy: no-referrer`, `Cache-Control: no-store` on auth responses | — |

Rate-limited responses are `429` with a `Retry-After` header. Token refresh is deliberately excluded from the strict bucket: the web app calls it on every page load, and guessing a 256-bit refresh token is not feasible. Per-account login lockout is enforced separately by auth-service.

## 6a. Uploaded images

Profile pictures, club logos and photos, and event images go to an S3-compatible bucket.

- **Default (single host):** the bundled `seaweedfs` container, with data in the `media_data` volume. `scripts/generate-secrets.sh` creates its key (`secrets/media-s3-secret-key`) and access rules (`secrets/seaweedfs-s3.json`): only the services can write, and anyone can read. The web app's nginx serves the bucket read-only at `/media/`, so nothing else is exposed.
- **Managed storage (AWS S3, Cloudflare R2, …):**
  1. Create a bucket that allows public reads of its objects, or put a CDN in front of it.
  2. Set these in `.env`:
     ```bash
     MEDIA_S3_ENDPOINT=https://<account>.r2.cloudflarestorage.com   # or https://s3.<region>.amazonaws.com
     MEDIA_S3_BUCKET=<bucket>
     MEDIA_S3_ACCESS_KEY=<access key id>
     MEDIA_S3_REGION=auto                                          # R2; your AWS region otherwise
     MEDIA_S3_PATH_STYLE=false
     MEDIA_PUBLIC_BASE_URL=https://media.example.com               # the bucket's public URL or CDN
     ```
  3. Put the provider's secret key in `secrets/media-s3-secret-key`.
  4. Restart user-, club- and event-service. The `seaweedfs` container can then be removed.
- **Limits:** JPEG, PNG and WebP only (checked by content, so SVG and renamed files are refused), up to `MEDIA_MAX_SIZE` (5 MB).
- **Backups:** the database backups don't include images. With SeaweedFS, copy the `media_data` volume off the host as well, for example `docker run --rm -v the_club_media_data:/data:ro -v "$PWD":/out alpine tar czf /out/media-$(date +%F).tgz -C /data .`. Managed object storage has its own versioning and replication.

## 7. Backups and restore

- **Schedule:** `db-backup-mysql` and `db-backup-postgres` dump every database nightly at `BACKUP_SCHEDULE` (default 02:30 in `APP_TIME_ZONE`).
- **Storage and retention:** dumps go into the `backups` volume and are kept for `BACKUP_RETENTION_DAYS` (default 14).
- **Consistency:** MySQL dumps use `--single-transaction`, which takes a consistent snapshot without blocking writes.

```bash
./scripts/backup.sh                                     # back up now
docker compose -f docker-compose.yml exec db-backup-mysql ls -lh /backups/mysql /backups/postgres
./scripts/restore.sh userdb mysql/userdb-20261018T023000Z.sql.gz   # asks for confirmation
```

Stop the owning service before restoring, for example `docker compose -f docker-compose.yml stop user-service`, and start it again afterwards.

**Off-site copies are required for real disaster recovery.** The volume lives on the same disk as the databases. Copy `/backups` to object storage on a schedule, for example a cron job on the host:
```bash
docker run --rm -v the_club_backups:/backups:ro -e AWS_ACCESS_KEY_ID -e AWS_SECRET_ACCESS_KEY \
  amazon/aws-cli s3 sync /backups s3://<bucket>/the-club/ --storage-class STANDARD_IA
```
Test restores regularly; a backup you have never restored is not a backup. Managed databases (RDS, Cloud SQL) with point-in-time recovery are the next step up.

## 8. Rotating the JWT signing key

Access tokens live for 15 minutes; refresh tokens are opaque and stored by auth-service, so rotating the signing key does not log anyone out.

1. Keep the current public key: `cp secrets/theclub.jwt.public-key secrets/theclub.jwt.previous-public-key`
2. Generate a new pair:
   ```bash
   openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 | openssl pkcs8 -topk8 -nocrypt -out secrets/theclub.jwt.private-key
   openssl pkey -in secrets/theclub.jwt.private-key -pubout -out secrets/theclub.jwt.public-key
   chmod 644 secrets/theclub.jwt.*
   ```
3. Set `JWT_KEY_ID=k2` (any new value) in `.env`.
4. Mount the previous key so that tokens signed with the old key keep verifying. Create `docker-compose.rotation.yml`:
   ```yaml
   x-prev: &prev
     secrets:
       - { source: jwt_previous_public_key, target: JWT_PREVIOUS_PUBLIC_KEY }
   services:
     auth-service: *prev
     user-service: *prev
     activity-service: *prev
     club-service: *prev
     event-service: *prev
     notification-service: *prev
   secrets:
     jwt_previous_public_key:
       file: ./secrets/theclub.jwt.previous-public-key
   ```
   Compose merges these with each service's existing secrets.
5. Restart auth-service first, then the other services:
   ```bash
   C="docker compose -f docker-compose.yml -f docker-compose.rotation.yml"
   $C up -d auth-service && $C up -d user-service activity-service club-service event-service notification-service
   ```
6. After at least one access-token lifetime (15 minutes; wait an hour to be safe), drop the rotation file and restart the services the same way without it.

To rotate the internal API key, replace `secrets/theclub.security.internal-api-key` and restart all services together. Calls between services fail during the restart window.

## 9. CI/CD

All CI/CD lives in `.github/`.

| Workflow | Trigger | What it does |
|---|---|---|
| `ci.yml` → backend | push to main, pull requests | `mvn verify` on JDK 21 (all unit and integration tests) |
| `ci.yml` → web | push to main, pull requests | `oxlint`, unit tests, production build |
| `ci.yml` → images | push to main, after both pass | Builds each service image and the web image, scans them with Trivy (fails on fixable CRITICAL vulnerabilities), pushes `ghcr.io/<owner>/the-club-<service>:<sha>` and `:latest` |
| `ci.yml` → dependency-scan | push to main, pull requests | Trivy scan of `pom.xml` and the lockfile, reported to GitHub code scanning |
| `codeql.yml` | push to main, pull requests, weekly | CodeQL `security-extended` for Java and TypeScript |
| `dependabot.yml` | weekly | Maven, npm, GitHub Actions and Docker base-image updates (minor/patch grouped) |

To deploy from the registry instead of building on the host, add `image: ghcr.io/<owner>/the-club-<service>:<sha>` to each service (for example in a `docker-compose.images.yml` override) and run `docker compose -f docker-compose.yml -f docker-compose.images.yml pull && ... up -d`.

Recommended repository settings:
- Protect `main`: require the `Backend`, `Web app` and `CodeQL` checks and at least one review.
- Enable secret scanning and push protection.

## 10. Production checklist

- [ ] `secrets/` generated, backed up, and the Razorpay, SMTP and Strava secret files filled in
- [ ] `SPRING_PROFILES_ACTIVE=prod`
- [ ] `PAYMENT_PROVIDER=razorpay`, `RAZORPAY_KEY_ID` set, and the webhook created at `https://<host>/api/v1/payments/webhooks/razorpay`
- [ ] `AUTH_COOKIE_SECURE=true` and `REQUIRE_EMAIL_VERIFICATION=true`
- [ ] Real SMTP settings (`MAIL_*`) with SPF/DKIM configured for `MAIL_FROM`'s domain
- [ ] `APP_BASE_URL=https://<host>`, and `CORS_ALLOWED_ORIGINS` limited to your origins
- [ ] TLS terminated in front of `web-app`, real-IP configured in nginx if the proxy adds `X-Forwarded-For`, HSTS enabled
- [ ] Deployed with `docker compose -f docker-compose.yml` (no dev override)
- [ ] Off-site backup copy scheduled, and a restore tested
- [ ] Uploaded images: `media_data` copied off-site too, or managed S3 storage configured (§6a)
- [ ] Admin password changed after first login, `ADMIN_EMAIL` set to a real mailbox
- [ ] Branch protection and required CI checks on `main`

## 11. Roadmap beyond a single host

- **Kubernetes:** a Helm chart with Deployments of at least 2 replicas, readiness and liveness probes on `/actuator/health/{readiness,liveness}`, HorizontalPodAutoscalers, and Secrets mounted at `/run/secrets`. Kubernetes Services can replace Eureka.
- **Rate limiting across replicas:** the gateway's limiter is in-memory and per instance. With several gateways, switch to Spring Cloud Gateway's Redis `RequestRateLimiter`.
- **Service discovery:** Eureka runs as a single node here. Run a peer-replicated pair, or use platform discovery.
- **Observability:** OpenTelemetry tracing across services, Prometheus metrics (`/actuator/prometheus`) with Grafana dashboards and alerts, and centralised logs (Loki or ELK).
- **Messaging:** move notifications and emails to a broker (Kafka or RabbitMQ) with an outbox, so a crash never loses them.
- **Data:** managed MySQL and PostgreSQL with high availability and point-in-time recovery.
