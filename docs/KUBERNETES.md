# The Club on Kubernetes

The Helm chart in [`deploy/helm/the-club`](../deploy/helm/the-club) runs the whole platform on Kubernetes:
the seven Spring services, the web app, optional in-cluster databases, backups, a mail catcher for development,
NetworkPolicies, autoscaling and Prometheus scraping. Docker Compose (`docker-compose.yml`) keeps working
unchanged for single-server deployments and local development.

- [How it maps from Docker Compose](#how-it-maps-from-docker-compose)
- [Quick start on your laptop (kind)](#quick-start-on-your-laptop-kind)
- [Production install](#production-install)
- [Configuration reference](#configuration-reference)
- [Upgrades and rollbacks](#upgrades-and-rollbacks)
- [Scaling and multiple replicas](#scaling-and-multiple-replicas)
- [Observability](#observability)
- [Troubleshooting](#troubleshooting)

---

## How it maps from Docker Compose

| Docker Compose | Kubernetes (this chart) |
|---|---|
| One container per service | One Deployment + ClusterIP Service per service (`<release>-user`, `<release>-gateway`, …) |
| Eureka (`discovery-service`) finds services | Kubernetes Services do. Eureka is off (`EUREKA_CLIENT_ENABLED=false`); Feign clients and gateway routes use the Service URLs (`USER_SERVICE_URL`, `USER_SERVICE_URI`, …). `discovery-service`/`config-service` are not deployed by default. |
| `web-app` publishes port 3000 | An Ingress (or Gateway API HTTPRoute) exposes only the web app; nginx in it proxies `/api` to the gateway. |
| `.env` | One ConfigMap (`<release>-env`) rendered from `config.*` values |
| `./secrets/*` files mounted per container under `/run/secrets/<VARIABLE>` | One Secret whose **keys are the same file names**; each pod mounts only the keys it needs under the same `/run/secrets/<VARIABLE>` paths, read via `SPRING_CONFIG_IMPORT=optional:configtree:/run/secrets/` — identical behaviour |
| `mysql` / `postgres` containers + `docker/*/initdb` | Managed databases in production, or `databases.embedded.enabled` StatefulSets running the **same** init scripts (one database + least-privilege user per service) |
| `db-backup-*` containers | `backups.enabled`: one small scheduler Deployment per engine running the same `scripts/backup.sh schedule` into its own PVC |
| `mailpit` (override file) | `mailpit.enabled` |
| Healthchecks | startup / liveness (`/actuator/health/liveness`) / readiness (`/actuator/health/readiness`, includes the database) probes |

Workload hardening applied everywhere: non-root users, read-only root filesystem (writable `/tmp` emptyDir),
all Linux capabilities dropped, `RuntimeDefault` seccomp, no service-account token, graceful shutdown
(`preStop` + Spring graceful shutdown), zero-downtime rolling updates (`maxUnavailable: 0`), and a default-deny
NetworkPolicy with explicit allow rules for exactly the calls each service makes.

---

## Quick start on your laptop (kind)

You get the full stack on a local single-node cluster at http://localhost:8088.

**Prerequisites** (in addition to the README's Java/Maven/Docker setup): kind, kubectl and Helm.

```bash
# Linux (amd64)
mkdir -p ~/.local/bin && cd ~/.local/bin
curl -sSfLo kind https://kind.sigs.k8s.io/dl/latest/kind-linux-amd64 && chmod +x kind
curl -sSfLo kubectl "https://dl.k8s.io/release/$(curl -sSfL https://dl.k8s.io/release/stable.txt)/bin/linux/amd64/kubectl" && chmod +x kubectl
curl -sSfL https://raw.githubusercontent.com/helm/helm/main/scripts/get-helm-3 | bash
cd - && export PATH="$HOME/.local/bin:$PATH"

# macOS
brew install kind kubectl helm
```

**Start it** (from the project root; give Docker at least 8 GB of memory):
```bash
mvn clean install -DskipTests
./deploy/kind/up.sh
```
`up.sh` does the following:
1. Builds the images.
2. Creates the kind cluster `theclub`.
3. Installs the Traefik ingress controller.
4. Loads all images into the cluster.
5. Creates the `the-club-secrets` Secret from `./secrets` (generating it if missing).
6. Installs the chart with `values-dev.yaml`: in-cluster MySQL/PostgreSQL, Mailpit, mock payments, nightly backups.

It prints the admin login at the end.

| What | How |
|---|---|
| App | http://localhost:8088 |
| Admin password | `cat secrets/admin-password` |
| Emails | `kubectl -n the-club port-forward svc/the-club-mailpit 8025:8025` → http://localhost:8025 |
| Pods | `kubectl -n the-club get pods` |
| Logs | `kubectl -n the-club logs deploy/the-club-user -f` |
| Smoke test | see below |

```bash
kubectl -n the-club port-forward svc/the-club-gateway 18080:8080 &
GATEWAY_URL=http://localhost:18080 STRAVA_WEBHOOK_VERIFY_TOKEN=dev-strava-verify-token ./scripts/smoke-test.sh
```

Rebuild after code changes with `mvn clean install -DskipTests && ./deploy/kind/up.sh`; the script is
idempotent and Helm rolls the pods. Remove everything with `./deploy/kind/down.sh`.

`up.sh` options: `KIND_HTTP_PORT=9090` (host port), `SKIP_BUILD=1` (reuse images), `SECRETS_DIR=…`.

---

## Production install

### 1. Prerequisites
- **Kubernetes:** 1.27+ (managed EKS / GKE / AKS or similar), with a default StorageClass.
- **An ingress controller,** e.g. Traefik, HAProxy, NGINX Gateway Fabric, or your cloud's load-balancer controller. The community `ingress-nginx` project was retired in 2026, so pick a maintained one. Alternatively, use a Gateway API implementation with `httpRoute.enabled`.
- **cert-manager** with a ClusterIssuer (e.g. Let's Encrypt) for TLS, or a certificate on your load balancer.
- **Managed MySQL 8 and PostgreSQL 15,** reachable from the cluster.
- **CI images in GHCR:** `.github/workflows/ci.yml` publishes `ghcr.io/<owner>/the-club-<service>:<sha>` on every push to `main`. If the packages are private, create a pull secret:
  ```bash
  kubectl -n the-club create secret docker-registry ghcr-pull --docker-server=ghcr.io \
    --docker-username=<github-user> --docker-password=<token with read:packages>
  ```
  Then set `global.imagePullSecrets: [{name: ghcr-pull}]`.

### 2. Databases (one database and least-privilege user per service)
Generate the passwords first (`./scripts/generate-secrets.sh`), then run this against the managed instances,
substituting the contents of the matching `secrets/db-*-password` files:

```sql
-- MySQL 8 (as the admin user)
CREATE DATABASE userdb         CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE DATABASE authdb         CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE DATABASE clubdb         CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE DATABASE eventdb        CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE DATABASE notificationdb CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE USER 'theclub_user'@'%'         IDENTIFIED BY '<secrets/db-user-password>';
CREATE USER 'theclub_auth'@'%'         IDENTIFIED BY '<secrets/db-auth-password>';
CREATE USER 'theclub_club'@'%'         IDENTIFIED BY '<secrets/db-club-password>';
CREATE USER 'theclub_event'@'%'        IDENTIFIED BY '<secrets/db-event-password>';
CREATE USER 'theclub_notification'@'%' IDENTIFIED BY '<secrets/db-notification-password>';
GRANT ALL PRIVILEGES ON userdb.*         TO 'theclub_user'@'%';
GRANT ALL PRIVILEGES ON authdb.*         TO 'theclub_auth'@'%';
GRANT ALL PRIVILEGES ON clubdb.*         TO 'theclub_club'@'%';
GRANT ALL PRIVILEGES ON eventdb.*        TO 'theclub_event'@'%';
GRANT ALL PRIVILEGES ON notificationdb.* TO 'theclub_notification'@'%';
```
```sql
-- PostgreSQL 15 (as the admin user)
CREATE ROLE theclub_activity LOGIN PASSWORD '<secrets/db-activity-password>';
CREATE DATABASE activitydb OWNER theclub_activity;
REVOKE ALL ON DATABASE activitydb FROM PUBLIC;
-- then, connected to activitydb:
ALTER SCHEMA public OWNER TO theclub_activity;
```
Tables are created by each service's Flyway migrations on first start (see
[DATABASE_MIGRATIONS.md](DATABASE_MIGRATIONS.md)). Several replicas starting at once are safe, because Flyway takes a
database lock. Use your provider's automated backups and point-in-time recovery; the chart's backup schedulers are
only for the embedded databases.

### 3. Secrets
The Secret's keys are the file names from `scripts/generate-secrets.sh`:

| Key | Used by | Required |
|---|---|---|
| `theclub.jwt.private-key` | auth | yes |
| `theclub.jwt.public-key` | all services | yes |
| `theclub.security.internal-api-key` | all services | yes |
| `db-user-password`, `db-auth-password`, `db-club-password`, `db-event-password`, `db-notification-password`, `db-activity-password` | the matching service | yes |
| `admin-password` | user (first admin) | yes |
| `strava-token-encryption-key` | activity (encrypts stored Strava tokens) | yes |
| `zeptomail-webhook-token` | notification (bounce webhook) | yes |
| `zeptomail-token` | notification (ZeptoMail API) | for `MAIL_PROVIDER=zeptomail` |
| `smtp-password` | notification (SMTP) | for `MAIL_PROVIDER=smtp` |
| `razorpay-key-secret`, `razorpay-webhook-secret` | event | for `PAYMENT_PROVIDER=razorpay` |
| `strava-client-secret` | activity | for Strava |
| `mysql-root-password`, `postgres-password` | embedded databases and backups only | dev |

Simplest approach (fill in the third-party files in `secrets/` first):
```bash
kubectl create namespace the-club
kubectl -n the-club create secret generic the-club-secrets --from-file=secrets/
```
With a secret manager, use the **External Secrets Operator** so nothing is stored in git or Helm history:
```yaml
apiVersion: external-secrets.io/v1
kind: ExternalSecret
metadata: {name: the-club-secrets, namespace: the-club}
spec:
  refreshInterval: 1h
  secretStoreRef: {name: aws-secrets-manager, kind: ClusterSecretStore}
  target: {name: the-club-secrets}
  dataFrom:
    - extract: {key: prod/the-club}      # a JSON object whose keys are the names in the table above
```
Sealed Secrets (`kubeseal`) also works: seal the output of the `kubectl create secret … --dry-run=client -o yaml`
command above. After rotating a secret, restart the affected Deployments
(`kubectl -n the-club rollout restart deploy`).

### 4. Install
Copy `values-prod.yaml`, replace every `CHANGE-ME` (host, emails, Razorpay key ID, database hosts, controller
namespace, database CIDRs), then run:
```bash
helm upgrade --install the-club deploy/helm/the-club -n the-club \
  -f my-values-prod.yaml --set global.imageTag=<git commit sha> --wait --timeout 15m
kubectl -n the-club get pods
```
Point DNS for your host at the ingress controller's load balancer. cert-manager issues the certificate named in
`ingress.tls.secretName`.

### 5. Third-party callbacks
| Provider | Configure |
|---|---|
| Razorpay | Webhook `https://<host>/api/v1/payments/webhooks/razorpay` (secret = `razorpay-webhook-secret`) |
| Strava | Callback domain `<host>`; webhook `https://<host>/api/v1/strava/webhook` (`config.strava.webhookCallbackUrl`) |
| ZeptoMail | Bounce webhook `https://<host>/api/v1/notifications/webhooks/zeptomail` (token = `zeptomail-webhook-token`); see [EMAIL_SETUP.md](EMAIL_SETUP.md) |

---

## Configuration reference

The most-used values (all of them are commented in [`values.yaml`](../deploy/helm/the-club/values.yaml)):

| Value | Meaning |
|---|---|
| `global.imageRegistry`, `global.imageTag`, `global.imagePullSecrets` | Where images come from; pin `imageTag` to a commit SHA |
| `config.springProfilesActive` | `prod` enables fail-fast checks (no placeholder secrets, no mock payments) |
| `config.appBaseUrl`, `config.corsAllowedOrigins` | Public URL of the app |
| `config.auth.cookieSecure`, `config.auth.requireEmailVerification` | Both `true` in production |
| `config.payments.provider`, `config.payments.razorpayKeyId` | `razorpay` in production |
| `config.mail.*` | `provider` (`zeptomail`/`smtp`/`log`), sender, ZeptoMail region URL, SMTP host |
| `config.strava.*` | Client ID, redirect and webhook URLs, sync window |
| `config.gateway.rateLimit*PerMinute` | Per gateway **pod** (see scaling) |
| `config.extraEnv` | Any extra environment variable for every service |
| `secrets.name` / `secrets.create` + `secrets.data` | Existing Secret name, or create one from values (dev only) |
| `databases.mysql.*`, `databases.postgres.*` | Managed database host, port, JDBC params (TLS) |
| `databases.embedded.enabled` | In-cluster MySQL + PostgreSQL (development/evaluation) |
| `backups.*` | Nightly dumps of the embedded databases (`schedule: "02:30"`, `retentionDays`, per-engine `storage`). On demand: `kubectl -n the-club exec deploy/the-club-backup-mysql -- bash /opt/theclub/scripts/backup.sh run` |
| `serviceDefaults.*` / `services.<name>.*` | Replicas, resources, autoscaling, probes, scheduling per service |
| `webApp.*` | Web app replicas, resources, `realIpFrom` (trusted proxy ranges), DNS resolver |
| `ingress.*` / `httpRoute.*` | How the web app is exposed |
| `networkPolicy.*` | Controller namespace, Prometheus namespace, database/OTLP/internet CIDRs and ports |
| `observability.*` | JSON logs, OTLP tracing, ServiceMonitors / scrape annotations |
| `services.discovery.enabled` | Run Eureka instead of Kubernetes Service discovery (not recommended) |

---

## Upgrades and rollbacks

```bash
helm upgrade the-club deploy/helm/the-club -n the-club -f my-values-prod.yaml --set global.imageTag=<new sha> --wait
helm -n the-club history the-club
helm -n the-club rollback the-club <revision> --wait
```
- **Rolling updates are zero-downtime.** New pods must pass readiness, including a database check, before old ones stop, and `preStop` plus graceful shutdown drain in-flight requests.
- **Migrations are forward-only.** New Flyway migrations run when the new pods start. A rollback deploys the old images against the already-migrated schema, so keep migrations backward-compatible (add columns and tables first, remove them in a later release). See [DATABASE_MIGRATIONS.md](DATABASE_MIGRATIONS.md).
- **Configuration changes restart pods automatically.** Changes to `config.*` alter the ConfigMap checksum annotation, which triggers a rollout. Secret changes need `kubectl rollout restart`.

---

## Scaling and multiple replicas

- **Autoscaling:** turn on `serviceDefaults.autoscaling.enabled`, or set it per service. It scales on CPU (70%), and PodDisruptionBudgets plus topology spread keep replicas available during node maintenance. Autoscaling requires metrics-server.
- **Background jobs are safe with several replicas:**
  - Payment reconciliation (event-service) runs on one replica at a time, using a ShedLock row in `eventdb.shedlock`.
  - The email outbox (notification-service) claims rows with `SELECT … FOR UPDATE SKIP LOCKED`.
  - The Strava webhook worker (activity-service) leases rows with a conditional `UPDATE`.
  - Creating the bootstrap admin tolerates two user-service pods racing.
- **Rate limits count per gateway pod.** The limiter is in memory, so the effective limit per client IP is the configured value × the number of gateway pods. Size `config.gateway.rateLimitStrictPerMinute` accordingly; a shared Redis-backed limiter is on the roadmap.
- **Real client IPs:** nginx in the web app trusts `X-Forwarded-For` from `webApp.realIpFrom` (private ranges by default), so rate limits see real client IPs behind the ingress controller. Narrow it to your controller or load-balancer range.

---

## Observability

- **Metrics:** every Spring service exposes `/actuator/prometheus`. This is internal only: nginx proxies just `/api`, and the gateway never routes `/actuator`.
  - With the Prometheus Operator (kube-prometheus-stack), set `observability.metrics.serviceMonitor.enabled=true`. Add `observability.metrics.serviceMonitor.labels` if your Prometheus selects on a label (e.g. `release: kube-prometheus-stack`), and set `networkPolicy.prometheusNamespace=monitoring`.
  - Annotation-based scrapers use the `prometheus.io/*` pod annotations.
- **Tracing:** set `observability.tracing.enabled=true` and `observability.tracing.otlpEndpoint=http://<collector>:4318/v1/traces`.
- **Logs:** `observability.logFormat=json` (the default) emits one ECS JSON object per line with trace IDs, ready for Loki or Elasticsearch.
- **Dashboards and alerts:** the ones shipped for Docker Compose (`docker-compose.observability.yml`) can be imported into your Grafana and Prometheus.

---

## Troubleshooting

| Symptom | Check / fix |
|---|---|
| Pod `CrashLoopBackOff` | `kubectl -n the-club logs <pod> --previous`. Usual causes are a missing Secret key, a wrong DB host or password, or `prod` profile checks (placeholder secret, mock payments). One restart while the embedded database initialises is normal. |
| `CreateContainerConfigError` / `secret … not found` | The Secret is missing or lacks a required key (table above). Run `kubectl -n the-club describe pod <pod>`. |
| Pod Running but not Ready | Readiness includes the database: `kubectl -n the-club port-forward deploy/the-club-user 18081:8081` then `curl localhost:18081/actuator/health/readiness`, or check the logs for connection errors. |
| `ImagePullBackOff` | Wrong `global.imageRegistry`/`imageTag`, or the packages are private and `global.imagePullSecrets` is missing. |
| Requests hang / time out between services | A NetworkPolicy is blocking the flow. Check that every service listing the target in `services.<name>.calls` reaches it. Managed databases outside `networkPolicy.databaseCidrs` are blocked. Set `networkPolicy.enabled=false` briefly to confirm. |
| `502 Bad Gateway` from the web app | The gateway isn't Ready yet, or `webApp.resolver` doesn't match your cluster DNS Service. |
| Every user is rate-limited together | `webApp.realIpFrom` doesn't include the ingress controller's address range. |
| kind: images fail to load / pull | `up.sh` pre-pulls on the host and loads archives into the node. Make sure Docker can pull, and has 8 GB+ memory. |
