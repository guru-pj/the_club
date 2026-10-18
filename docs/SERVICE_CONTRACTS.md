# The Club — Service Contracts (Phase 1 + Phase 2)

How the services fit together. Public endpoints are reached through the gateway (`:8080`);
`/internal/**` endpoints are service-to-service only and are never routed by the gateway.

## Services, ports, databases

| Service              | Port | Database                 | Owns                                                                   |
|----------------------|------|--------------------------|------------------------------------------------------------------------|
| gateway-service      | 8080 | —                        | Routing, CORS, blocks `/internal/**`                                    |
| user-service         | 8081 | MySQL `userdb`           | Users, profiles, roles                                                  |
| auth-service         | 8082 | MySQL `authdb`           | Login, JWT access + refresh tokens                                      |
| activity-service     | 8083 | PostgreSQL `activitydb`  | Manual activities, Strava connection + webhook, activity stats          |
| club-service         | 8084 | MySQL `clubdb`           | Clubs, memberships, feed posts, club leaderboard, owner verification    |
| event-service        | 8086 | MySQL `eventdb`          | Events, registration, payments, QR tickets, check-in, points, monthly leaderboard |
| notification-service | 8087 | MySQL `notificationdb`   | In-app notifications, email delivery                                    |
| discovery-service    | 8761 | —                        | Eureka registry                                                         |
| config-service       | 8888 | —                        | Spring Cloud Config (native or git backend)                             |

(8085 is taken by Trino on the host.)

## Security model

* `auth-service` issues **RS256** access tokens signed with its RSA private key (`JWT_PRIVATE_KEY`, PKCS#8 PEM;
  only auth-service has it). Every other service only holds the public key (`JWT_PUBLIC_KEY`, X.509 PEM) and
  verifies tokens itself via `common-lib` (no per-request call to auth-service). Key values are inline PEM or a
  `file:`/`classpath:` location; the JWT header carries `kid` (`JWT_KEY_ID`, default `k1`).
  Key rotation: deploy the new pair and set the old public key as `JWT_PREVIOUS_PUBLIC_KEY` until old tokens expire.
  The current keys are published at `GET /api/v1/auth/.well-known/jwks.json`.
  Claims: `sub` = user id, `email`, `name`, `roles` (e.g. `["USER","CLUB_OWNER"]`), `iss` = `the-club`.
* Access tokens live 15 minutes (`JWT_EXPIRATION`). Refresh tokens (opaque, 14 days, stored hashed) are rotated on
  every refresh and are also set as the cookie `theclub_refresh` (HttpOnly, SameSite=Strict, Path=/api/v1/auth,
  Secure unless `AUTH_COOKIE_SECURE=false`). `/refresh` and `/logout` read the token from the JSON body or the cookie.
  `POST /api/v1/auth/logout-all` revokes every session of the caller; password reset/change revokes them too.
* Login is locked for 15 minutes after 5 failed attempts within 15 minutes per email (429 + `Retry-After`).
  When `REQUIRE_EMAIL_VERIFICATION=true` (default) unverified users get 403 with problem property
  `code: "EMAIL_NOT_VERIFIED"`.
* Account emails (verification, password reset) contain single-use links to the web app (`APP_BASE_URL`);
  only SHA-256 hashes of those tokens are stored. Passwords: 8–72 characters, not the email, not a common password.
* Controllers receive the caller with `@AuthenticationPrincipal AuthenticatedUser`.
* Roles: `USER` (everyone), `CLUB_OWNER` (granted after owner verification is approved), `ADMIN`.
  After a role change the user calls `POST /api/v1/auth/refresh` to get a token with the new role.
* `/internal/**` requires header `X-Internal-Key: ${INTERNAL_API_KEY}` (added automatically to every Feign call).
* With the `prod` profile, services refuse to start if `INTERNAL_API_KEY` is shorter than 32 characters or still a
  `change-me` placeholder, or if `ADMIN_PASSWORD` is a placeholder.
* Errors are RFC 7807 problem documents (`application/problem+json`); validation errors carry an `errors` map,
  and some errors carry a machine-readable `code`.

## Internal endpoints

### user-service
| Method | Path | Body | Response |
|---|---|---|---|
| GET  | `/internal/users/by-email/{email}` | — | `{id, name, email, passwordHash, roles[], active, emailVerified}` |
| GET  | `/internal/users/{id}` | — | same as above |
| POST | `/internal/users/summaries` | `{ids: [uuid]}` | `[{id, name, profilePicUrl}]` (unknown ids omitted) |
| POST | `/internal/users/{id}/roles` | `{role: "CLUB_OWNER"}` | user credentials |
| DELETE | `/internal/users/{id}/roles/{role}` | — | user credentials |

### auth-service
| Method | Path | Body | Response |
|---|---|---|---|
| DELETE | `/internal/auth/sessions/{userId}` | — | `204`; revokes every refresh token of the user (used by user-service after a password reset/change) |

### activity-service
| Method | Path | Body | Response |
|---|---|---|---|
| POST | `/internal/activities/stats` | `{userIds: [uuid], from: LocalDateTime?, to: LocalDateTime?}` | `[{userId, totalDistanceKm, totalDurationSeconds, sessions}]` — only users with ≥ 1 activity in range (by `startTime`, `from` inclusive, `to` exclusive) |
| GET  | `/internal/activities/{id}` | — | `{id, userId, title, type, distance, duration, calories, startTime, endTime, source, externalId}`; 404 if missing |

### club-service
| Method | Path | Body | Response |
|---|---|---|---|
| GET  | `/internal/clubs/{clubId}` | — | `{id, name, ownerId, memberCount}`; 404 if missing |
| GET  | `/internal/clubs/{clubId}/members/{userId}` | — | `{member: boolean, role: "OWNER"/"MEMBER"/null}` |
| POST | `/internal/clubs/summaries` | `{ids: [uuid]}` | `[{id, name, logoUrl}]` |

### event-service
| Method | Path | Body | Response |
|---|---|---|---|
| GET | `/internal/events/{eventId}` | — | `{id, clubId, title, startTime, status}`; 404 if missing |

### notification-service
| Method | Path | Body | Response |
|---|---|---|---|
| POST | `/internal/notifications` | `{userId, type, message, email?: {to, subject, body, attachments?: [{filename, contentType, base64Content, inline: boolean, contentId?}]}}` | `202 {id}` |

Email is best-effort: delivery failures are logged, the in-app notification is still stored.

## Gateway routes

| Path | Service |
|---|---|
| `/api/v1/users/**` | user-service |
| `/api/v1/auth/**` | auth-service |
| `/api/v1/activities/**`, `/api/v1/strava/**` | activity-service |
| `/api/v1/clubs/**`, `/api/v1/owner-verification/**` | club-service |
| `/api/v1/events/**`, `/api/v1/payments/**`, `/api/v1/participation/**`, `/api/v1/leaderboards/**` | event-service |
| `/api/v1/notifications/**` | notification-service |
| `/internal/**` | blocked (404) |

## Conventions

* IDs: UUIDv7 from `com.theclub.common.util.UuidGenerator`, stored as `CHAR(36)` (`@JdbcTypeCode(SqlTypes.CHAR)`).
* Times: `LocalDateTime` in the platform zone (`APP_TIME_ZONE`, default `Asia/Kolkata`); "now" always comes from the injected `java.time.Clock`.
* Feign client base URLs can be pinned with `theclub.clients.<service>.url` (env `USER_SERVICE_URL`, …); empty means resolve through Eureka.
* Tests: unit tests `*Test`, Spring MockMvc + H2 tests `*IntegrationTest` with profile `test`; downstream Feign clients are mocked.

## Payments

event-service settles a paid registration in one of three ways. All three use the same idempotent, row-locked path:

1. **Client confirm:** `POST /api/v1/payments/{id}/confirm` with the checkout signature.
2. **Razorpay webhook:** `POST /api/v1/payments/webhooks/razorpay`. It is public and authenticated by `X-Razorpay-Signature`, an HMAC-SHA256 of the raw body keyed with `RAZORPAY_WEBHOOK_SECRET`. Deliveries are de-duplicated by `X-Razorpay-Event-Id`. Handled events: `payment.captured`, `order.paid`, `payment.failed`, `refund.processed`.
3. **Reconciliation job** (every `PAYMENT_RECONCILE_INTERVAL`, default 5 min):
   * Asks the provider about `CREATED` payments older than 2 min and settles the captured ones.
   * Expires the rest once they pass `PAYMENT_PENDING_HOLD` (15 min). The payment becomes `EXPIRED` and the registration `CANCELLED`, which frees the spot.
   * Retries `REFUND_PENDING` refunds.

A capture that arrives late, after `FAILED` or `EXPIRED`, is still honoured. The registration is reconfirmed if there is room. Otherwise the money is refunded automatically, and the same happens if the event was cancelled or the registration was already paid.

| Payment status | Meaning |
|---|---|
| `CREATED` | Order created, awaiting payment |
| `SUCCEEDED` | Paid; registration confirmed |
| `FAILED` | A payment attempt failed (can still settle) |
| `EXPIRED` | Unpaid past the hold; registration released |
| `REFUND_PENDING` | Refund requested; provider call failed, will retry |
| `REFUNDED` | Money returned; registration cancelled |

Other changes:

* **Refunds:** `POST /api/v1/payments/{id}/refund {reason?}` is for the club owner or an admin. It only works on `SUCCEEDED` payments, and returns 409 for checked-in registrations. Cancelling a paid event refunds every successful payment automatically.
* `GET /api/v1/events/{id}/registrations` rows include `paymentId` and `paymentStatus`.
* `PaymentResponse` includes `refundedAt` and `refundReason`.
* `PAYMENT_PROVIDER` is required (`razorpay`, or `mock` for development). The mock provider is refused when the `prod` profile is active.
