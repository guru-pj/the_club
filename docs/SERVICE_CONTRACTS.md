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

* `auth-service` issues HS256 access tokens signed with `JWT_SECRET` (≥ 32 bytes).
  Claims: `sub` = user id, `email`, `name`, `roles` (e.g. `["USER","CLUB_OWNER"]`), `iss` = `the-club`.
* Every service validates the token itself via `common-lib` (no per-request call to auth-service).
  Controllers receive the caller with `@AuthenticationPrincipal AuthenticatedUser`.
* Roles: `USER` (everyone), `CLUB_OWNER` (granted after owner verification is approved), `ADMIN`.
  After a role change the user calls `POST /api/v1/auth/refresh` to get a token with the new role.
* `/internal/**` requires header `X-Internal-Key: ${INTERNAL_API_KEY}` (added automatically to every Feign call).
* Errors are RFC 7807 problem documents (`application/problem+json`); validation errors carry an `errors` map.

## Internal endpoints

### user-service
| Method | Path | Body | Response |
|---|---|---|---|
| GET  | `/internal/users/by-email/{email}` | — | `{id, name, email, passwordHash, roles[], active}` |
| GET  | `/internal/users/{id}` | — | same as above |
| POST | `/internal/users/summaries` | `{ids: [uuid]}` | `[{id, name, profilePicUrl}]` (unknown ids omitted) |
| POST | `/internal/users/{id}/roles` | `{role: "CLUB_OWNER"}` | user credentials |
| DELETE | `/internal/users/{id}/roles/{role}` | — | user credentials |

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
