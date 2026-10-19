# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

The Club: a fitness-club platform. It is a Maven multi-module of Spring Boot 3.5.7 microservices (Java 21, Spring Cloud 2025.0) plus a React 19 + TypeScript + Vite SPA in `web-app/`. Everything runs under Docker Compose; Kubernetes is supported via the Helm chart in `deploy/helm/the-club`.

## Commands

Backend (from the repo root; never run `mvn` or the scripts with `sudo`, because root-owned `target/` dirs break later builds):

```bash
mvn clean install -DskipTests                 # build all modules (common-lib must be installed before services build alone)
mvn -B -ntp verify                            # what CI runs
mvn -pl event-service -am test                # one service (with its common-lib dependency)
mvn -pl event-service test -Dtest=PaymentServiceTest              # one test class
mvn -pl event-service test -Dtest=PaymentServiceTest#methodName   # one test method
```

Web app (`cd web-app`, Node 22):

```bash
npm ci
npm run dev        # :5173, proxies /api to GATEWAY_URL (default http://localhost:8080)
npm test           # vitest; only src/**/*.test.ts, node environment
npx vitest run src/lib/format.test.ts         # single test file
npx oxlint src     # lint (CI uses this exact form)
npm run build      # tsc -b && vite build
npm run e2e        # browser flow via playwright-core; needs CHROME_PATH, ADMIN_EMAIL, ADMIN_PASSWORD
VITE_API_BASE_URL=https://… npm run android:sync   # native build into the Capacitor project (android/)
npm run macos:build                                 # Tauri app (macOS only, needs Rust)
```

The Android (`web-app/android/`, Capacitor) and macOS (`web-app/src-tauri/`, Tauri) apps wrap this same UI, built with `--mode native` (`isNativeApp` in `src/lib/platform.ts`). They call the gateway cross-origin, so they keep the refresh token in app storage and send it in the request body rather than relying on the cookie. Any change to auth or session handling must work in both modes. CI builds them in `.github/workflows/apps.yml`. See `docs/NATIVE_APPS.md`.

Full stack:

```bash
cp enviroinment_variables.env .env && ./scripts/generate-secrets.sh   # first time; secrets/ is git-ignored
docker compose up -d --build        # also loads docker-compose.override.yml (dev: host ports, Mailpit, Trino)
./scripts/smoke-test.sh             # end-to-end API check, ends with "ALL PASSED" (503s right after startup = still registering in Eureka)
docker compose -f docker-compose.yml -f docker-compose.override.yml -f docker-compose.observability.yml up -d   # + Prometheus/Grafana/Tempo/Loki
```

Production compose uses `docker compose -f docker-compose.yml` only, without the override file.

## Architecture

- **Request path:** browser → nginx (web-app container, `:3000`) → `gateway-service` (`:8080`, routes `/api/v1/**`, returns 404 for `/internal/**`) → services resolved through Eureka (`discovery-service`). Route-to-service mapping and every internal endpoint are documented in `docs/SERVICE_CONTRACTS.md`; keep that file in sync when adding endpoints.
- **Database per service:** user/auth/club/event/notification use separate MySQL databases; activity-service uses PostgreSQL. There are no cross-service foreign keys: another service's IDs are plain `CHAR(36)` columns, and data is fetched over Feign (`<service>/client/*Client.java`). Feign base URLs come from `theclub.clients.<service>.url`; empty means "resolve through Eureka".
- **Auth:** only auth-service holds the RSA private key and issues RS256 access tokens. Every other service verifies JWTs locally via `common-lib` (`JwtAuthenticationFilter`, `JwtService`) and makes no call to auth-service. Controllers get the caller via `@AuthenticationPrincipal AuthenticatedUser`. Roles are `USER`, `CLUB_OWNER` and `ADMIN` (`common-lib` `Roles`). A role change only takes effect after the client calls `/api/v1/auth/refresh`.
- **Internal calls:** `/internal/**` endpoints require the `X-Internal-Key` header (`InternalApiKeyFilter`); it is added automatically to every Feign call.
- **common-lib** is an auto-configured library (`TheClubCommonAutoConfiguration`) that every service depends on. It provides security filters, RFC 7807 problem-detail error handling (`GlobalExceptionHandler`, `ApiException`, `FeignExceptionHandler`), request-ID and observability plumbing, `ProductionSafetyCheck` (under the `prod` profile, refuses to start with placeholder secrets) and `UuidGenerator`. Changes there affect every service.
- **Email is asynchronous via an outbox:** services POST to notification-service `/internal/notifications` with a template name and variables. Notification-service writes the notification and email to an outbox in one transaction, and a background dispatcher sends them (ZeptoMail in prod, SMTP/Mailpit in dev) with retry and backoff. Templates and their required variables are listed in `SERVICE_CONTRACTS.md`.
- **Payments (event-service):** client confirm, the Razorpay webhook and a scheduled `PaymentReconciler` all settle payments through one idempotent, row-locked path. Scheduled jobs use ShedLock so only one replica runs them. The `PaymentGateway` interface has `MockPaymentGateway` and `RazorpayPaymentGateway` implementations.
- **Service package layout:** `com.theclub.<name>service.{controller,service,repository,model,dto,client,config}`. Public and internal controllers are separate classes (e.g. `InternalEventController`).
- **config-service** exists, but services currently ship their own `application.yml` and do not depend on it.

## Conventions

- **IDs:** UUIDv7 from `com.theclub.common.util.UuidGenerator`, mapped as `CHAR(36)` with `@JdbcTypeCode(SqlTypes.CHAR)`.
- **Time:** `LocalDateTime` in `APP_TIME_ZONE` (default `Asia/Kolkata`). Always get "now" from the injected `java.time.Clock` (tests use `MutableClock`), never `LocalDateTime.now()`.
- **Schema is owned by Flyway** (`<service>/src/main/resources/db/migration`, `V<n>__desc.sql`). Hibernate runs `ddl-auto: validate`. Never edit an applied migration; add a new one. Enums are `VARCHAR` columns. SQL must also run on H2 in MySQL/PostgreSQL mode, because tests use H2. See `docs/DATABASE_MIGRATIONS.md`.
- **Tests:** unit tests are `*Test`; Spring MockMvc + H2 tests with profile `test` are `*IntegrationTest`, with downstream Feign clients mocked. `*MigrationIntegrationTest` runs migrations against real MySQL/Postgres via Testcontainers and is skipped when Docker isn't available. Test JWT keys live in each service's `src/test/resources`.
- **Helm copies of scripts:** CI diffs `docker/mysql/initdb/01-service-users.sh`, `docker/postgres/initdb/01-service-user.sh` and `scripts/backup.sh` against their copies in `deploy/helm/the-club/files/`. Update both when changing either.

## Docs

`docs/` contains the authoritative reference: `SERVICE_CONTRACTS.md`, `DATABASE_MIGRATIONS.md`, `PRODUCTION.md`, `KUBERNETES.md`, `OBSERVABILITY.md`, `NATIVE_APPS.md`, `EMAIL_SETUP.md`, `STRAVA_SETUP.md` and `ARCHITECTURE_OVERVIEW.md`. The `*.txt` / `*.md.txt` files in the repo root are early design notes from the initial upload and may not match the current code; prefer `docs/` and the source.
