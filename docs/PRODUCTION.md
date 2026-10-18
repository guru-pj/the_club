# The Club — Production Deployment Guide

This guide covers running The Club on a single Docker host: a VM or bare-metal server with Docker Engine and Compose v2. The [roadmap](#10-roadmap-beyond-a-single-host) lists what changes for multi-host or Kubernetes deployments.

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
| `smtp-password`, `razorpay-key-secret`, `razorpay-webhook-secret`, `strava-client-secret` | notification / event / activity | Created empty. Fill them in. |

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
| `MAIL_HOST` / `MAIL_PORT` / `MAIL_USERNAME` / `MAIL_STARTTLS` / `MAIL_FROM` | Your SMTP provider; the password goes in `secrets/smtp-password` |
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

If the TLS proxy adds `X-Forwarded-For`, uncomment the `set_real_ip_from` / `real_ip_header` lines in `web-app/nginx.conf` with the proxy's address range. Otherwise every client appears to come from the proxy's IP and they all share one rate-limit bucket.

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
- [ ] Admin password changed after first login, `ADMIN_EMAIL` set to a real mailbox
- [ ] Branch protection and required CI checks on `main`

## 11. Roadmap beyond a single host

- **Kubernetes:** a Helm chart with Deployments of at least 2 replicas, readiness and liveness probes on `/actuator/health/{readiness,liveness}`, HorizontalPodAutoscalers, and Secrets mounted at `/run/secrets`. Kubernetes Services can replace Eureka.
- **Rate limiting across replicas:** the gateway's limiter is in-memory and per instance. With several gateways, switch to Spring Cloud Gateway's Redis `RequestRateLimiter`.
- **Service discovery:** Eureka runs as a single node here. Run a peer-replicated pair, or use platform discovery.
- **Observability:** OpenTelemetry tracing across services, Prometheus metrics (`/actuator/prometheus`) with Grafana dashboards and alerts, and centralised logs (Loki or ELK).
- **Messaging:** move notifications and emails to a broker (Kafka or RabbitMQ) with an outbox, so a crash never loses them.
- **Data:** managed MySQL and PostgreSQL with high availability and point-in-time recovery.
