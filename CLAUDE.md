# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

The Club: a fitness-club platform. It is a Maven multi-module of Spring Boot 4.0 microservices (Java 21, Spring Cloud 2025.1, Jackson 3) plus a React 19 + TypeScript + Vite SPA in `web-app/`. Everything runs under Docker Compose; Kubernetes is supported via the Helm chart in `deploy/helm/the-club`.

## Commands

Backend (from the repo root; never run `mvn` or the scripts with `sudo`, because root-owned `target/` dirs break later builds):

```bash
mvn clean install -DskipTests                 # build all modules (common-lib must be installed before services build alone)
mvn -B -ntp verify                            # what CI runs
mvn -pl event-service -am test                # one service (with its common-lib dependency)
mvn -pl event-service -am test -Dtest=PaymentServiceTest -Dsurefire.failIfNoSpecifiedTests=false             # one test class
mvn -pl event-service -am test -Dtest='PaymentServiceTest#methodName' -Dsurefire.failIfNoSpecifiedTests=false # one method
```

Always keep `-am` with `-pl`. Without it Maven uses the parent POM and `common-lib` from `~/.m2`. If those are stale (for example after a version bump), Maven only warns "The POM for com.theclub:common-lib ... is invalid" and drops all of common-lib's transitive dependencies.

Web app (`cd web-app`, Node 22):

```bash
npm ci
npm run dev        # :5173, proxies /api to GATEWAY_URL (default http://localhost:8080) and /media to MEDIA_URL (http://localhost:3000)
npm test           # vitest; only src/**/*.test.ts, node environment
npx vitest run src/lib/format.test.ts         # single test file
npx oxlint src     # lint (CI uses this exact form)
npm run build      # tsc -b && vite build
npm run e2e        # browser flow via playwright-core; needs CHROME_PATH, ADMIN_EMAIL, ADMIN_PASSWORD
VITE_API_BASE_URL=https://… npm run android:sync   # native build into the Capacitor project (android/)
VITE_API_BASE_URL=https://… npm run ios:sync       # same for the iOS project (ios/); building it needs a Mac with Xcode
npm run macos:build                                 # Tauri app (macOS only, needs Rust)
```

The Android (`web-app/android/`) and iOS (`web-app/ios/`) Capacitor apps and the macOS Tauri app (`web-app/src-tauri/`) wrap this same UI, built with `--mode native` (`isNativeApp` in `src/lib/platform.ts`). They call the gateway cross-origin, so they keep the refresh token in app storage and send it in the request body rather than relying on the cookie. Any change to auth or session handling must work in both modes. CI builds them in `.github/workflows/apps.yml`. See `docs/NATIVE_APPS.md`.

Full stack:

```bash
cp enviroinment_variables.env .env && ./scripts/generate-secrets.sh   # first time; secrets/ is git-ignored
docker compose up -d --build        # also loads docker-compose.override.yml (dev: host ports, Mailpit, Trino)
./scripts/smoke-test.sh             # end-to-end API check, ends with "ALL PASSED" (503s right after startup = still registering in Eureka)
./scripts/seed-demo.sh              # optional sample clubs, events and members (dev only; prints the logins)
docker compose -f docker-compose.yml -f docker-compose.override.yml -f docker-compose.observability.yml up -d   # + Prometheus/Grafana/Tempo/Loki
```

Production compose uses `docker compose -f docker-compose.yml` only, without the override file.

## Architecture

- **Request path:** browser → nginx (web-app container, `:3000`) → `gateway-service` (`:8080`, routes `/api/v1/**`, returns 404 for `/internal/**`) → services resolved through Eureka (`discovery-service`). Route-to-service mapping and every internal endpoint are documented in `docs/SERVICE_CONTRACTS.md`; keep that file in sync when adding endpoints.
- **Database per service:** user/auth/club/event/notification use separate MySQL databases; activity-service uses PostgreSQL. There are no cross-service foreign keys: another service's IDs are plain `CHAR(36)` columns, and data is fetched over Feign (`<service>/client/*Client.java`). Feign base URLs come from `theclub.clients.<service>.url`; empty means "resolve through Eureka".
- **Auth:** only auth-service holds the RSA private key and issues RS256 access tokens. Every other service verifies JWTs locally via `common-lib` (`JwtAuthenticationFilter`, `JwtService`) and makes no call to auth-service. Controllers get the caller via `@AuthenticationPrincipal AuthenticatedUser`. Roles are `USER`, `CLUB_OWNER` and `ADMIN` (`common-lib` `Roles`). A role change only takes effect after the client calls `/api/v1/auth/refresh`.
- **Internal calls:** `/internal/**` endpoints require the `X-Internal-Key` header (`InternalApiKeyFilter`); it is added automatically to every Feign call.
- **common-lib** is an auto-configured library (`TheClubCommonAutoConfiguration`) that every service depends on. It provides security filters, RFC 7807 problem-detail error handling (`GlobalExceptionHandler`, `ApiException`, `FeignExceptionHandler`), request-ID and observability plumbing, `ProductionSafetyCheck` (under the `prod` profile, refuses to start with placeholder secrets) and `UuidGenerator`. Changes there affect every service.
- **Uploaded images:** `common-lib`'s `MediaStore` (`com.theclub.common.media`) is auto-configured in services that include the storage SDKs: user-, club- and event-service. `BlobMediaStore` checks the content (JPEG/PNG/WebP magic bytes, size) and picks random keys; `MEDIA_PROVIDER` selects `S3MediaStore` (any S3-compatible store), `GcsMediaStore` or `AzureMediaStore`. Blank keys mean the cloud's workload identity, and `MEDIA_PRIVATE_BUCKET` optionally separates private documents. The Google and Azure SDKs are optional dependencies of common-lib, so services that store files declare them too, and their types are only loaded by the nested `Gcs`/`Azure` holders in `MediaAutoConfiguration`. Entities store the public URL (`/media/…` by default). In Docker Compose the bucket is the `seaweedfs` container (MinIO's images are no longer published), and the web app's nginx proxies `/media/` read-only to it. The SDK client sends only the checksums S3 requires; SeaweedFS rejects the SDK's default extra checksums. In the web app, always render stored images through `mediaUrl()`: the native apps need the server's address in front of `/media/…`. Controllers mock `MediaStore` in tests; `S3MediaStoreTest`, `GcsMediaStoreTest` and `AzureMediaStoreTest` run against SeaweedFS, fake-gcs-server and Azurite (Testcontainers).
- **Follow vs subscribe:** a follow (`club_follows`) only gets updates. A subscription is a membership (`club_memberships`, the old "join"), which gives leaderboards, subscriber-only events and reviews. Club posts and new-event announcements go to `ClubService.audienceIds` (both groups). Events are open to anyone signed in unless `subscribersOnly`; events created before this existed were migrated to subscribers-only.
- **Club ownership is verified per club:** an application (`VerificationRequest`) claims one club; approval creates it (`ClubService.createFor`, `verifiedAt` set), links it (`club_id`) and grants `CLUB_OWNER`. `POST /api/v1/clubs` is admin-only, and owners can't rename a club (its name is the verified one). Every owner check is "owns this club" (`ClubService.requireOwnerOrAdmin`, event-service `ClubAccess`), never the role alone. Proof documents go through `MediaStore.storePrivate` under `private/` in the same bucket; nginx refuses `/media/private/`. Events start as drafts from the web app (`publish: false`), are published, and are completed after they start.
- **Feed and chat (club-service):** subscribers post too; the owner also reshares other clubs' posts and events (`PostType.RESHARE`, pointing at the original post, or at an event with a snapshot). `PostViews` adds authors, like/comment counts and shared content to a page of posts in batched queries. Chat has one group conversation per club (subscribers and owner only) and direct conversations between two people who share a club, stored in MySQL; message IDs are UUIDv7, so ordering by ID is ordering by time. Live delivery is Server-Sent Events (`GET /api/v1/chat/stream`, read with `fetch` so the token can be sent): `ChatHub` holds this replica's connections, and `ChatRelay` polls the database every 2s for messages written on other replicas. With `CHAT_FANOUT=redis`, `RedisChatFanout` publishes each message on Redis channel `theclub:chat` instead (`LocalChatFanout` otherwise), and the relay remains a slower safety net (`CHAT_RELAY_INTERVAL`). common-lib permits `DispatcherType.ASYNC`, because the dispatch that ends a stream carries no token; without it Tomcat drops the connection with "Access Denied". `ChatStreamServerTest` covers this on a real server, which MockMvc can't.
- **Discovery:** "nearby" is a bounding-box SQL query plus an exact great-circle distance (`Geo`) in Java, which is portable to H2. User locations are rounded to ~100 m and only returned to the user. event-service builds the upcoming feed from club-service's internal `related` and `nearby` endpoints.
- **Email is asynchronous via an outbox:** services POST to notification-service `/internal/notifications` with a template name and variables. Notification-service writes the notification and email to an outbox in one transaction, and a background dispatcher sends them (ZeptoMail in prod, SMTP/Mailpit in dev) with retry and backoff. Templates and their required variables are listed in `SERVICE_CONTRACTS.md`.
- **Payments (event-service):** client confirm, the Razorpay webhook and a scheduled `PaymentReconciler` all settle payments through one idempotent, row-locked path. Scheduled jobs use ShedLock so only one replica runs them. The `PaymentGateway` interface has `MockPaymentGateway` and `RazorpayPaymentGateway` implementations.
- **Service package layout:** `com.theclub.<name>service.{controller,service,repository,model,dto,client,config}`. Public and internal controllers are separate classes (e.g. `InternalEventController`).
- **Redis is optional** (`REDIS_ENABLED`, Helm `redis.enabled`): the gateway's `RedisClientLimiter` (Lua token bucket, `RATE_LIMIT_STORE=redis`) shares rate limits across replicas and fails open; club-service uses it for chat fan-out. Compose doesn't run Redis.
- **Cloud deployment:** the Helm chart has `values-aws.yaml`, `values-gcp.yaml` and `values-azure.yaml` layered on `values-prod.yaml`, External Secrets (`secrets.external`), off-site backups (`backups.offsite`, rclone; Compose profile `offsite`) and `extraObjects`. CI lints and kubeconforms each combination. See `docs/CLOUD_DEPLOYMENT.md`.
- **config-service** exists, but services currently ship their own `application.yml` and do not depend on it.

## Conventions

- **IDs:** UUIDv7 from `com.theclub.common.util.UuidGenerator`, mapped as `CHAR(36)` with `@JdbcTypeCode(SqlTypes.CHAR)`.
- **Time:** `LocalDateTime` in `APP_TIME_ZONE` (default `Asia/Kolkata`). Always get "now" from the injected `java.time.Clock` (tests use `MutableClock`), never `LocalDateTime.now()`. Entities' own `@PrePersist` timestamps use the JVM zone, so Docker Compose and Helm start every JVM with `-Duser.timezone=$APP_TIME_ZONE`; the containers run in UTC, and without this those timestamps were 5½ h behind.
- **Schema is owned by Flyway** (`<service>/src/main/resources/db/migration`, `V<n>__desc.sql`). Hibernate runs `ddl-auto: validate`. Never edit an applied migration; add a new one. Enums are `VARCHAR` columns. SQL must also run on H2 in MySQL/PostgreSQL mode, because tests use H2. See `docs/DATABASE_MIGRATIONS.md`.
- **Spring Boot 4 splits auto-configuration into modules.** A missing one fails silently or at startup, not at compile time:
  - `spring-boot-starter-flyway`: without it, migrations simply don't run.
  - `spring-boot-starter-restclient`: provides `RestClient.Builder`.
  - `spring-boot-starter-opentelemetry` (in `common-lib` and the gateway): without it, tracing exports nothing.
  - the `*-test` modules: MockMvc, WebTestClient, `TestRestTemplate`, metrics and tracing.

  Properties renamed in Boot 4 are no longer read at all. Check a module's `META-INF/spring-configuration-metadata.json` instead of trusting old names. Tracing uses `management.tracing.export.enabled` and `management.opentelemetry.tracing.export.otlp.endpoint` (fed by `TRACING_ENABLED` and `OTEL_EXPORTER_OTLP_ENDPOINT`). `TracingExportTest` and `GatewayTracingExportTest` prove spans reach a collector.
- **Jackson 3:** `ObjectMapper`/`JsonNode` come from `tools.jackson.databind`, and `JacksonException` is unchecked. Annotations stay in `com.fasterxml.jackson.annotation`. Every service sets `spring.jackson.use-jackson2-defaults: true`, so API JSON behaves as it did under Jackson 2; for example, missing primitive fields aren't rejected.
- **Tests:** unit tests are `*Test`; Spring MockMvc + H2 tests with profile `test` are `*IntegrationTest`, with downstream Feign clients mocked. `*MigrationIntegrationTest` runs migrations against real MySQL/Postgres via Testcontainers and is skipped when Docker isn't available. Test JWT keys live in each service's `src/test/resources`.
- **Helm copies of scripts:** CI diffs `docker/mysql/initdb/01-service-users.sh`, `docker/postgres/initdb/01-service-user.sh` and `scripts/backup.sh` against their copies in `deploy/helm/the-club/files/`. Update both when changing either.

- **Web app redirects after sign-in** all go through `startPage()` (`lib/startPage.ts`): new accounts (`profileComplete === false`) go to `/welcome`. Two guards redirecting to different places loop while React Router keeps the old page mounted during a lazy route load.

## Docs

`docs/` contains the authoritative reference: `RUN_AND_TEST.md` (updating a copy, testing in the browser and the apps), `CLOUD_DEPLOYMENT.md` (AWS/GCP/Azure), `SERVICE_CONTRACTS.md`, `DATABASE_MIGRATIONS.md`, `PRODUCTION.md`, `KUBERNETES.md`, `OBSERVABILITY.md`, `NATIVE_APPS.md`, `EMAIL_SETUP.md`, `STRAVA_SETUP.md` and `ARCHITECTURE_OVERVIEW.md`. The `*.txt` / `*.md.txt` files in the repo root are early design notes from the initial upload and may not match the current code; prefer `docs/` and the source.
