# The Club — Observability

Every service exposes **metrics**, emits **traces** and writes **structured logs**. An optional stack collects
and connects them so you can go from a slow dashboard panel, to the trace of one request, to that request's
log lines.

```
                 ┌──────────── /actuator/prometheus (scraped every 15s, internal network only) ───────────┐
                 │                                                                                        ▼
  browser → nginx (web-app) → gateway → user / auth / club / event / activity / notification      Prometheus ──► Alertmanager
                 │      X-Request-Id + W3C traceparent propagate through every hop                   │  (rules)      (email/Slack)
                 │                                                                                    │
                 ├── OTLP/HTTP spans ──► OpenTelemetry Collector ──► Tempo ◄───────────────┐          │
                 │                                                                          │          │
                 └── stdout (ECS JSON) ──► Docker ──► Grafana Alloy ──► Loki ◄─── Grafana ──┴──────────┘
                                                                                  (dashboards, Explore,
                                                                                   trace ↔ log ↔ metric links)
```

## Starting it

The stack is a separate compose file layered on top of the normal ones. It also switches the services to JSON
logs and turns on trace export; without it, the application runs as before.

```bash
# development (with the dev override: Mailpit, localhost ports)
docker compose -f docker-compose.yml -f docker-compose.override.yml -f docker-compose.observability.yml up -d

# production
docker compose -f docker-compose.yml -f docker-compose.observability.yml up -d
```

`scripts/generate-secrets.sh` creates `secrets/grafana-admin-password`. On an existing install, rerun it; it
only adds missing files.

| UI | URL (localhost only) | Login |
|---|---|---|
| Grafana | http://localhost:3001 (`GRAFANA_PORT`) | `admin` / `cat secrets/grafana-admin-password` |
| Prometheus | http://localhost:9090 (`PROMETHEUS_PORT`) | none (bind to localhost or put behind auth) |
| Alertmanager | http://localhost:9093 (`ALERTMANAGER_PORT`) | none |

On a server, reach these through an SSH tunnel (`ssh -L 3001:localhost:3001 server`) or publish Grafana
through your TLS proxy. Never expose Prometheus or Alertmanager publicly.

## Dashboards (Grafana → Dashboards → "The Club")

| Dashboard | What it shows |
|---|---|
| **The Club — Overview** | Up/down per service, requests/s, 5xx ratio, p50/p95/p99 latency, slowest endpoints, status codes, service-to-service calls, heap, GC, CPU, DB pool (active/pending), recent ERROR/WARN logs. `Service` variable filters everything. |
| **The Club — Business** | Sign-ups, logins by outcome, verifications, password resets, clubs/joins/posts, owner verification, registrations (free/paid), payments by status, reconciliation, check-ins and points, emails sent/failed, outbox backlog, Strava API usage, webhooks and rate-limit quota. |
| **The Club — Gateway** | Requests and upstream p95 latency per route, status codes, 5xx per route, rate-limited (429) requests by bucket. |

Dashboards are generated from `observability/grafana/generate_dashboards.py`. To change one, edit the
script, run `python3 observability/grafana/generate_dashboards.py`, and commit the regenerated JSON. Grafana
reloads it within a minute; UI edits are not saved, because the dashboards are provisioned.

**From a metric to a trace to logs:**
- In Grafana **Explore**, pick Tempo and search by service or duration. Open a span and choose **Logs for
  this span** to see that request's log lines in Loki.
- In Loki results, every JSON line with a `traceId` has a **TraceID** link that opens the trace.

## Metrics

Every service serves `/actuator/prometheus`. It's reachable only inside the container network: nginx proxies
only `/api`, and the gateway never routes `/actuator`. Every series carries `application="<service>"`, and
Prometheus adds `instance` and `service`.

- **HTTP:** `http_server_requests_seconds_*` (histogram with SLO buckets at 100ms, 300ms, 1s and 3s);
  `http_client_requests_seconds_*` for Feign/RestTemplate/WebClient calls.
- **Gateway:** `spring_cloud_gateway_requests_seconds_*{routeId, httpStatusCode}`.
- **JVM and process:** memory, GC, threads, CPU, uptime.
- **Database:** `hikaricp_connections_{active,idle,pending,max}`.
- **Scheduled jobs:** `tasks_scheduled_execution_seconds_*`.

### Business metric naming convention

`theclub_<domain>_<event>_total{low-cardinality tags}`: counters end in `_total`, gauges don't. Tags are
outcomes, types or statuses, **never** ids, emails or other unbounded values. In code, use
`com.theclub.common.observability.BusinessMetrics.increment("auth.logins", "outcome", "success")`, or a
Micrometer `Counter`/`Gauge` named `theclub.<domain>.<event>`.

| Metric | Tags | Source |
|---|---|---|
| `theclub_auth_logins_total` | `outcome` = success, bad_credentials, locked, unverified | auth-service |
| `theclub_auth_refreshes_total` | `outcome` = success, rejected | auth-service |
| `theclub_auth_logouts_total`, `theclub_auth_sessions_revoked_total` | none | auth-service |
| `theclub_users_registrations_total`, `theclub_users_email_verifications_total`, `theclub_users_password_changes_total` | none | user-service |
| `theclub_users_password_resets_total` | `stage` = requested, completed | user-service |
| `theclub_clubs_created_total`, `theclub_clubs_joins_total` | none | club-service |
| `theclub_clubs_posts_total` | `type`, `strava_linked` | club-service |
| `theclub_clubs_owner_verifications_total` | `action` = applied, approved, rejected, info_requested, proof_uploaded, club_created | club-service |
| `theclub_events_registrations_total` | `type` = free, paid | event-service |
| `theclub_events_checkins_total`, `theclub_events_points_awarded_total` | none | event-service |
| `theclub_payments_created_total` | `provider` | event-service |
| `theclub_payments_transitions_total` | `status` (succeeded, failed, expired, refund_pending, refunded), `provider` | event-service |
| `theclub_payments_reconciliation_runs_total`, `theclub_payments_reconciled_total` | `outcome` = settled, expired, refund_retried, error | event-service |
| `theclub_emails_sent_total` | `provider`, `template` | notification-service |
| `theclub_emails_failed_total` | `provider`, `reason` = retry, permanent, exhausted, suppressed | notification-service |
| `theclub_email_outbox_pending` (gauge) | none | notification-service |
| `theclub_strava_api_calls_total` | `endpoint`, `outcome` = success, retry, rate_limited, client_error, server_error, network_error | activity-service |
| `theclub_strava_webhook_events_total` | `aspect`, `outcome` | activity-service |
| `theclub_strava_ratelimit_usage` / `_limit` (gauges) | `kind` = overall, read; `window` = 15min, daily | activity-service |
| `theclub_gateway_rate_limited_total` | `bucket` = strict, general | gateway-service |

## Traces

- Micrometer Tracing with the OpenTelemetry bridge, exported over OTLP/HTTP to the collector, which forwards
  them to Tempo.
- Context propagates through the gateway, Feign calls (`feign-micrometer`), `@Async` tasks (common-lib's
  `ContextPropagatingTaskDecorator`) and `@Scheduled` jobs.
- Not traced, to keep traces about users: `/actuator` (scrapes, probes), Eureka heartbeats and Spring
  Security's internal per-filter spans.

| Setting | Env var | Default |
|---|---|---|
| Export on/off | `TRACING_ENABLED` | `false`; `true` when the observability compose file is used |
| Collector endpoint | `OTEL_EXPORTER_OTLP_ENDPOINT` | `http://localhost:4318/v1/traces` (compose: `http://otel-collector:4318/v1/traces`) |
| Sampling | `TRACING_SAMPLING_PROBABILITY` | `1.0` (all requests) |

**Sampling guidance:** use `1.0` in development and staging. In production start at `0.1` (10% of requests)
and raise it if you need more detail; a fully sampled busy system costs a lot of storage. Logs still carry
`traceId` for unsampled requests, so correlation by id keeps working.

With `TRACING_ENABLED=false` nothing is exported and services never fail because a collector is missing.

## Logs

Locally, logs are plain text. With the observability file (or `LOGGING_STRUCTURED_FORMAT_CONSOLE=ecs`), each line
is one ECS JSON object:

| Field | Meaning |
|---|---|
| `@timestamp`, `log.level`, `log.logger`, `message`, `error.*` | standard ECS |
| `service.name`, `service.version` | which service |
| `traceId`, `spanId` | trace correlation (click through to Tempo) |
| `requestId` | the `X-Request-Id` of the user's request, the same value in every service it touched |

**Request IDs:**
- The gateway assigns `X-Request-Id`, or keeps a valid one sent by the client (1–64 chars of
  `[A-Za-z0-9._:-]`).
- Every service logs it, forwards it on Feign calls and returns it in the response header.
- Every error response body includes it as `"requestId"`. When a user reports an error, ask for that id and
  search Loki for it:

```
{project=~".+"} |= "the-request-id"
```

**Secrets in logs:** passwords, tokens, refresh tokens, API keys, Razorpay/Strava/ZeptoMail credentials and
email contents must never be logged. Log ids and outcomes instead, as the existing code does (for example,
"Payment X CREATED -> SUCCEEDED"). Code review should reject any log statement that prints a request body,
an `Authorization`/`Cookie` header or a token variable.

Alloy collects every container on the Docker host: The Club's services, databases, nginx and the
observability stack. Use the `project` and `service` labels to filter.

## Alerts

Rules live in `observability/prometheus/alerts.yml`. Validate them with:

```bash
docker run --rm -v "$PWD/observability/prometheus:/etc/prometheus:ro" --entrypoint promtool \
  prom/prometheus:v2.55.1 check config /etc/prometheus/prometheus.yml
```

| Alert | Fires when | First things to check |
|---|---|---|
| ServiceDown (critical) | a service can't be scraped for 2m | `docker compose ps`; `docker compose logs --tail=200 <service>`; OOM (exit 137) or DB unreachable |
| HighErrorRate (critical) | more than 5% 5xx for 5m | Overview → slowest endpoints/status codes; Loki `level="ERROR"` for that service; a downstream dependency |
| HighLatencyP95 | p95 above 2s for 10m | DB pool pending, GC time, slow downstream (Strava/Razorpay/SMTP); traces sorted by duration in Tempo |
| JvmHeapHigh | heap above 90% for 10m | memory leak or undersized limit; raise `deploy.resources.limits.memory` |
| DatabasePoolExhausted | threads waiting for DB connections for 5m | slow queries, long transactions, `DB_POOL_MAX_SIZE` |
| PaymentReconciliationErrors | reconciliation errors in 15m | event-service logs for "Reconciliation of payment"; Razorpay status and credentials |
| PaymentFailuresSpike | more than 10 failed payments in 15m | Razorpay dashboard; signature or webhook-secret mismatch |
| EmailOutboxBacklog | more than 100 queued emails for 15m | provider credentials (`secrets/zeptomail-token`), provider status, notification-service logs |
| EmailDeliveryFailing | permanent/exhausted email failures | bounces or suppressions, sender domain verification |
| LoginLockoutSpike | many locked-account attempts | credential stuffing: check gateway 429s and source IPs in nginx logs |
| GatewayRateLimitingSpike (info) | many 429s | abusive client, or limits too low for legitimate traffic (`RATE_LIMIT_*`) |
| StravaRateLimited (info) / StravaRateLimitNearlyUsed | Strava quota hit or above 90% | imports resume when the window resets; request a higher quota from Strava |

### Alert delivery

`observability/alertmanager/alertmanager.yml` ships with a receiver that sends nothing: alerts are visible in
Alertmanager and Grafana only. For production, uncomment the email (for example, ZeptoMail SMTP) or Slack
example, put the credential in `secrets/` and mount it into the `alertmanager` service. Test it with:

```bash
docker compose exec alertmanager amtool alert add test severity=warning --alertmanager.url=http://localhost:9093
```

## Health probes and operations

| Endpoint | Use |
|---|---|
| `/actuator/health/liveness` | is the JVM alive (Kubernetes liveness; restart if failing) |
| `/actuator/health/readiness` | can it serve traffic: includes the database for DB-backed services (Kubernetes readiness, Docker health checks) |

Other settings that matter in operation:
- Services shut down gracefully on SIGTERM: in-flight requests get up to 20s.
- Feign calls time out after 3s to connect and 10s to read.
- Each service has at most `DB_POOL_MAX_SIZE` (default 10) database connections.

## Retention

| Data | Retention | Setting |
|---|---|---|
| Metrics | 15 days | `PROMETHEUS_RETENTION` |
| Traces | 72 hours | `observability/tempo/tempo.yaml` (`block_retention`) |
| Logs | 7 days | `observability/loki/loki.yaml` (`retention_period`) |

Data lives in the `prometheus_data`, `tempo_data` and `loki_data` volumes. Size the disk accordingly; a few GB
is enough at small scale.

## Production notes

- **Managed alternative:** point the collector's exporter and Prometheus `remote_write` at Grafana Cloud,
  Amazon Managed Prometheus, Datadog or similar, and drop the local Tempo/Loki/Prometheus containers. The
  services need no changes.
- **Kubernetes:** the Helm chart (`deploy/helm`) uses the same probes, `/actuator/prometheus` endpoints and
  environment variables (`TRACING_ENABLED`, `OTEL_EXPORTER_OTLP_ENDPOINT`, `LOGGING_STRUCTURED_FORMAT_CONSOLE`).
  See its README for Prometheus Operator scraping.
- **Scale:** the single-node Tempo/Loki/Prometheus here suit one Docker host. For more, use their
  scalable/object-storage modes or a managed service.
