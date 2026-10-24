# Testing the API with Postman

This guide shows how to exercise The Club's REST API with Postman (or its command-line runner, Newman). The repo ships a ready-made collection and environment:

| File | What it is |
|---|---|
| [`postman/the-club.postman_collection.json`](postman/the-club.postman_collection.json) | Every public endpoint (199 requests in 14 folders), plus a scripted **End-to-end journey** with assertions |
| [`postman/the-club.local.postman_environment.json`](postman/the-club.local.postman_environment.json) | Variables for the local development stack |

[`SERVICE_CONTRACTS.md`](SERVICE_CONTRACTS.md) remains the reference for routing, internal endpoints and email templates; [`ARCHITECTURE.md`](ARCHITECTURE.md) explains how the pieces fit together.

## 1. Before you start

1. Run the development stack (see [`RUN_AND_TEST.md`](RUN_AND_TEST.md)):
   ```bash
   docker compose up -d --build
   ./scripts/smoke-test.sh     # ends with "ALL PASSED"
   ```
   Right after startup the gateway can answer `503` for up to a minute while services register in Eureka.
2. The end-to-end journey needs the development defaults from `enviroinment_variables.env`:
   - `PAYMENT_PROVIDER=mock`, so a payment can be completed without Razorpay (`/payments/{id}/mock-signature`).
   - `REQUIRE_EMAIL_VERIFICATION=false`, so new accounts can log in at once. With `true`, verify each account first using the two **Mailpit** requests in *Auth & account*.
3. Find the admin login: the email is `ADMIN_EMAIL` in `.env`, the password is in `secrets/admin-password` (`cat secrets/admin-password`).

## 2. Import

1. In Postman choose **Import** and select both JSON files from `docs/postman/`.
2. Pick the environment **The Club – local (dev stack)** in the top-right selector.
3. Open the environment and fill in `adminPassword` (and `adminEmail` if yours differs).

| Variable | Default | Meaning |
|---|---|---|
| `baseUrl` | `http://localhost:8080` | The gateway. `http://localhost:3000` (nginx in the web-app container) also works, because every path starts with `/api/`. On a deployed server use `https://<your-host>`. |
| `mailpitUrl` | `http://localhost:8025` | Mailpit's API, used only by the two Mailpit requests |
| `adminEmail`, `adminPassword` | `admin@theclub.local`, empty | The platform admin created at first start |
| `tzOffsetMinutes` | `330` | Offset of `APP_TIME_ZONE` (Asia/Kolkata = +5:30). Event times are local date-times in that zone, so the scripts compute "now + 2 hours" with it. |

Everything else (tokens and ids) lives in **collection variables** that the requests fill in themselves.

## 3. Run the end-to-end journey

The **End-to-end journey** folder walks both journeys in order and checks each step. Every run registers fresh accounts (`owner+<runId>@example.com`, `member+<runId>@example.com`), so it can be repeated.

In Postman: right-click the folder → **Run folder** → **Run**. From a terminal, with Newman:

```bash
npx newman run docs/postman/the-club.postman_collection.json \
  -e docs/postman/the-club.local.postman_environment.json \
  --folder "End-to-end journey" --timeout-request 15000 \
  --env-var "adminEmail=$(grep '^ADMIN_EMAIL=' .env | cut -d= -f2-)" \
  --env-var "adminPassword=$(cat secrets/admin-password)"
```

A healthy stack passes all 41 requests and 58 assertions. The steps:

- 0. Start a run (unique emails)
- 1. Owner: register as club owner
- 2. Owner: log in
- 3. Owner: apply to verify a club
- 4. Owner: no owner powers before approval
- 5. Admin: log in
- 6. Admin: list pending applications
- 7. Admin: approve the application
- 8. Owner: refresh token (picks up CLUB_OWNER role)
- 9. Owner: complete the club profile
- 10. Owner: create a free event (draft)
- 11. Owner: publish the event
- 12. Owner: create a paid event (published)
- 13. Member: register
- 14. Member: log in
- 15. Member: profile setup (city)
- 16. Member: discover popular clubs
- 17. Member: follow the club
- 18. Member: upcoming events feed
- 19. Member: event details
- 20. Member: register for the free event
- 21. Member: duplicate registration is refused
- 22. Member: QR ticket
- 23. Member: register for the paid event
- 24. Member: mock checkout signature (dev only)
- 25. Member: confirm the payment
- 26. Member: my registrations
- 27. Member: notifications
- 28. Owner: fee is locked after payment
- 29. Owner: registrations for the event
- 30. Member: cannot see the registrations list
- 31. Owner: check in the member's ticket
- 32. Owner: same ticket again is refused
- 33. Owner: ticket from another event is refused
- 34. Owner: event stats
- 35. Owner: club participation analytics
- 36. Member: club analytics are owner-only
- 37. Owner: publish an event recap
- 38. Member: club feed shows the recap
- 39. Owner: mark the event completed

Step 39 expects `409 NOT_STARTED`: an event can only be completed after it starts, and the journey's events start two hours after the run. To try completion, create an event that starts a few minutes ahead, wait, then call **Events → Owner: mark completed**.

Do not run the journey against production: it creates accounts and clubs, and the mock payment step does not exist there.

## 4. How authentication works in the collection

- Log in with **Auth & account → Log in** (or any login step of the journey). The test script stores `accessToken` and `refreshToken` in `memberToken` / `memberRefreshToken`; owner and admin logins store `ownerToken` and `adminToken`.
- Every request already carries `Authorization: Bearer {{memberToken}}`, `{{ownerToken}}` or `{{adminToken}}` depending on who is allowed to call it (the **As** column below). Public endpoints use *No Auth*.
- Access tokens last 15 minutes (`JWT_EXPIRATION`). Use **Refresh tokens** to get a new pair. Refresh tokens **rotate**: the previous one stops working.
- Roles are inside the access token. After an admin approves a club, the owner must refresh (journey step 8) before owner endpoints accept them.
- Browsers keep the refresh token in an HttpOnly cookie; Postman (like the native apps) sends it in the body instead. Both work.

## 5. Conventions

| Topic | Rule |
|---|---|
| IDs | UUIDv7 strings |
| Times | `LocalDateTime` without offset, in `APP_TIME_ZONE`, e.g. `2026-11-02T06:00:00` |
| Paging | `?page=0&size=20` (max 100); responses are `{items, page, size, totalItems}` |
| Errors | RFC 7807 `application/problem+json`: `{status, title, detail}`, plus `code` for errors a client should branch on (e.g. `EMAIL_NOT_VERIFIED`, `FEE_LOCKED`, `SUBSCRIBERS_ONLY`, `NOT_STARTED`) and `errors` (field → message) for validation failures |
| Request ID | Every response has `X-Request-Id`; quote it when reporting a problem, it appears in the service logs |
| Uploads | `multipart/form-data` with a `file` field: JPEG, PNG or WebP (PDF for owner documents). In the request, choose the file in **Body → form-data**. |
| Money | `price` is a decimal in `currency` (default `INR`); `0` means free |

Common status codes: `401` missing or expired token (refresh), `403` signed in but not allowed (wrong role, not the owner of that club), `404` not found or not visible to you (drafts, other people's activities), `409` the request conflicts with the current state (duplicate registration, ticket already used, fee locked), `413` body too large, `429` rate limited (see below).

## 6. Rate limits

The gateway limits each client IP. Login, register, password reset and email verification share a **strict** bucket: `RATE_LIMIT_STRICT_PER_MINUTE`, 10 per minute by default and 120 in the development override; everything else has 300 per minute. One journey run makes 5 strict calls (2 registrations, 3 logins), so with the production default two runs a minute already hit the limit. A `429` response carries `Retry-After`.

## 7. Webhooks and things you can't fully test by hand

| Endpoint | Why |
|---|---|
| `POST /api/v1/payments/webhooks/razorpay` | Must carry `X-Razorpay-Signature`, an HMAC of the raw body with the webhook secret. Send test events from the Razorpay dashboard. |
| `POST /api/v1/notifications/webhooks/zeptomail` | Needs the token from `secrets/zeptomail-webhook-token` |
| `GET/POST /api/v1/strava/webhook`, `GET /api/v1/strava/callback` | Called by Strava; the callback is reached through the web app's `/strava/callback` page after the Strava consent screen |
| `GET /api/v1/chat/stream` | A long-lived server-sent-events stream; Postman shows events as they arrive |

Internal endpoints (`/internal/**`) are not in the collection: the gateway returns `404` for them on purpose. They are described in [`SERVICE_CONTRACTS.md`](SERVICE_CONTRACTS.md).

## 8. Endpoint reference

One request per endpoint, grouped like the collection folders. **As** is the token the request uses: *member* is any signed-in user, *owner* the owner of the club in `{{clubId}}`, *admin* the platform admin. Placeholders such as `{{clubId}}` are filled by earlier requests (or the journey); set them by hand under the collection's **Variables** tab to point at other records. A request whose variable is still empty goes to a shortened URL (`…/photos/` instead of `…/photos/<id>`) and gets `404` or `405`: run the request that creates the record first, or set the id. When running the whole collection with Newman, pass `--timeout-request 15000` so the chat stream request doesn't hold the run open; Strava requests answer `503` until Strava is configured ([`STRAVA_SETUP.md`](STRAVA_SETUP.md)).

### Auth & account

| Request | Method | Path | As | Notes |
|---|---|---|---|---|
| Register member | `POST` | `/api/v1/users/register` | public |  |
| Register club owner | `POST` | `/api/v1/users/register` | public | clubOwner=true only marks the intent; owner tools need an approved club application. |
| Mailpit: find the latest email (dev) | `GET` | `Mailpit /api/v1/search?query&limit` | public | Development only. Change baseUrl for this request to {{mailpitUrl}} (see the request URL). |
| Mailpit: read it and keep the token (dev) | `GET` | `Mailpit /api/v1/message/{{mailpitMessageId}}` | public | Takes the token from the verification or password-reset link. |
| Verify email | `POST` | `/api/v1/users/verify-email` | public |  |
| Resend verification email | `POST` | `/api/v1/users/verify-email/resend` | public |  |
| Log in | `POST` | `/api/v1/auth/login` | public |  |
| Refresh tokens | `POST` | `/api/v1/auth/refresh` | public | Browsers send the HttpOnly refresh cookie instead of a body. Each refresh rotates the token. |
| Validate access token | `GET` | `/api/v1/auth/validate` | member |  |
| JWKS (public keys) | `GET` | `/api/v1/auth/.well-known/jwks.json` | public |  |
| Log out (this session) | `POST` | `/api/v1/auth/logout` | public |  |
| Log out everywhere | `POST` | `/api/v1/auth/logout-all` | member |  |
| Request password reset | `POST` | `/api/v1/users/password-reset/request` | public |  |
| Confirm password reset | `POST` | `/api/v1/users/password-reset/confirm` | public |  |
| Change password | `PUT` | `/api/v1/users/me/password` | member | Signs out every session; log in again afterwards. |

### Users & profile

| Request | Method | Path | As | Notes |
|---|---|---|---|---|
| My profile | `GET` | `/api/v1/users/me` | member |  |
| Get a user | `GET` | `/api/v1/users/{{memberId}}` | member | Email and account flags only for yourself or an admin. |
| Update profile | `PUT` | `/api/v1/users/{{memberId}}` | member | Partial update. Location is stored at about 100 m precision; clearLocation=true removes it. |
| Upload profile photo | `POST` | `/api/v1/users/me/photo` | member |  |
| Remove profile photo | `DELETE` | `/api/v1/users/me/photo` | member |  |
| Find user by email (admin) | `GET` | `/api/v1/users/email/asha@example.com` | admin |  |
| Deactivate account | `DELETE` | `/api/v1/users/{{memberId}}` | member |  |

### Owner verification

| Request | Method | Path | As | Notes |
|---|---|---|---|---|
| Apply for a club | `POST` | `/api/v1/owner-verification/apply` | owner | One application per club; contact email or phone required. Never auto-approved. |
| My applications | `GET` | `/api/v1/owner-verification/requests/me` | owner |  |
| Get an application | `GET` | `/api/v1/owner-verification/requests/{{verificationId}}` | owner |  |
| Edit an application | `PUT` | `/api/v1/owner-verification/requests/{{verificationId}}` | owner | Allowed while PENDING or NEEDS_INFO. |
| Upload a document | `POST` | `/api/v1/owner-verification/requests/{{verificationId}}/documents` | owner |  |
| Download a document | `GET` | `/api/v1/owner-verification/requests/{{verificationId}}/documents/{{documentId}}` | owner |  |
| Delete a document | `DELETE` | `/api/v1/owner-verification/requests/{{verificationId}}/documents/{{documentId}}` | owner |  |
| Add proof links | `POST` | `/api/v1/owner-verification/requests/{{verificationId}}/upload-proof` | owner |  |
| Resubmit after changes were requested | `POST` | `/api/v1/owner-verification/requests/{{verificationId}}/resubmit` | owner | Only from NEEDS_INFO; returns the application to PENDING. |
| Admin: list applications | `GET` | `/api/v1/owner-verification/requests?status&page&size` | admin |  |
| Admin: application history | `GET` | `/api/v1/owner-verification/requests/{{verificationId}}/history` | admin |  |
| Admin: request changes | `POST` | `/api/v1/owner-verification/requests/{{verificationId}}/request-info` | admin |  |
| Admin: approve | `POST` | `/api/v1/owner-verification/requests/{{verificationId}}/approve` | admin | Optional body {"clubPayload": {...}} corrects the club details (e.g. the official name) before the club is created. |
| Admin: reject | `POST` | `/api/v1/owner-verification/requests/{{verificationId}}/reject` | admin |  |

### Clubs

| Request | Method | Path | As | Notes |
|---|---|---|---|---|
| Search clubs | `GET` | `/api/v1/clubs?q&city&page&size` | member |  |
| Nearby clubs | `GET` | `/api/v1/clubs/nearby?lat&lng&radiusKm&limit` | member |  |
| Popular clubs | `GET` | `/api/v1/clubs/popular?limit` | member |  |
| Get a club | `GET` | `/api/v1/clubs/{{clubId}}` | member |  |
| Clubs I belong to | `GET` | `/api/v1/clubs/me` | member |  |
| Clubs I follow | `GET` | `/api/v1/clubs/me/following` | member |  |
| Clubs owned by a user | `GET` | `/api/v1/clubs/owner/{{ownerId}}` | member |  |
| Clubs a user belongs to | `GET` | `/api/v1/clubs/user/{{memberId}}` | member |  |
| Follow | `POST` | `/api/v1/clubs/{{clubId}}/follow` | member |  |
| Unfollow | `DELETE` | `/api/v1/clubs/{{clubId}}/follow` | member |  |
| Subscribe (join) | `POST` | `/api/v1/clubs/{{clubId}}/join` | member | Only where the club has subscriptions enabled (else 409 SUBSCRIPTIONS_DISABLED). |
| Leave | `DELETE` | `/api/v1/clubs/{{clubId}}/members/me` | member |  |
| Members | `GET` | `/api/v1/clubs/{{clubId}}/members?page&size` | member |  |
| Club leaderboard | `GET` | `/api/v1/clubs/{{clubId}}/leaderboard?metric&period` | member | metric: DISTANCE \| TIME \| SESSIONS; period: WEEK \| MONTH \| YEAR \| ALL. |
| Owner: update club profile | `PUT` | `/api/v1/clubs/{{clubId}}` | owner | The verified name can't be changed by the owner. |
| Owner: upload logo | `POST` | `/api/v1/clubs/{{clubId}}/logo` | owner |  |
| Owner: upload cover photo | `POST` | `/api/v1/clubs/{{clubId}}/cover` | owner |  |
| Owner: remove cover photo | `DELETE` | `/api/v1/clubs/{{clubId}}/cover` | owner |  |
| Photos | `GET` | `/api/v1/clubs/{{clubId}}/photos` | member |  |
| Owner: add a photo | `POST` | `/api/v1/clubs/{{clubId}}/photos` | owner |  |
| Owner: delete a photo | `DELETE` | `/api/v1/clubs/{{clubId}}/photos/{{photoId}}` | owner |  |
| Owner: remove a member | `DELETE` | `/api/v1/clubs/{{clubId}}/members/{{memberId}}` | owner |  |
| Admin: create a club directly | `POST` | `/api/v1/clubs` | admin | Admins only; owners get clubs through verification. |

### Posts & comments

| Request | Method | Path | As | Notes |
|---|---|---|---|---|
| Club feed | `GET` | `/api/v1/clubs/{{clubId}}/posts?page&size` | member |  |
| Owner: announcement | `POST` | `/api/v1/clubs/{{clubId}}/posts` | owner |  |
| Owner: event recap | `POST` | `/api/v1/clubs/{{clubId}}/posts` | owner |  |
| Upload a post photo | `POST` | `/api/v1/clubs/{{clubId}}/posts/photos` | owner | Returns a URL to put in photoUrls. |
| Owner: reshare a post or event | `POST` | `/api/v1/clubs/{{clubId}}/posts/reshare` | owner |  |
| Get a post | `GET` | `/api/v1/clubs/{{clubId}}/posts/{{postId}}` | member |  |
| Public preview of a post | `GET` | `/api/v1/clubs/{{clubId}}/posts/{{postId}}/preview` | public |  |
| Edit a post | `PUT` | `/api/v1/clubs/{{clubId}}/posts/{{postId}}` | owner |  |
| Like | `PUT` | `/api/v1/clubs/{{clubId}}/posts/{{postId}}/like` | member |  |
| Unlike | `DELETE` | `/api/v1/clubs/{{clubId}}/posts/{{postId}}/like` | member |  |
| Comments | `GET` | `/api/v1/clubs/{{clubId}}/posts/{{postId}}/comments` | member |  |
| Comment | `POST` | `/api/v1/clubs/{{clubId}}/posts/{{postId}}/comments` | member | Followers and subscribers can comment. |
| Delete a comment | `DELETE` | `/api/v1/clubs/{{clubId}}/posts/{{postId}}/comments/{{commentId}}` | member |  |
| Delete a post | `DELETE` | `/api/v1/clubs/{{clubId}}/posts/{{postId}}` | owner |  |

### Reviews

| Request | Method | Path | As | Notes |
|---|---|---|---|---|
| Reviews | `GET` | `/api/v1/clubs/{{clubId}}/reviews?page&size` | member |  |
| Write or replace my review | `PUT` | `/api/v1/clubs/{{clubId}}/reviews/me` | member | Subscribers and people who checked in at one of the club's events; never the owner. |
| Delete my review | `DELETE` | `/api/v1/clubs/{{clubId}}/reviews/me` | member |  |
| Owner: reply | `PUT` | `/api/v1/clubs/{{clubId}}/reviews/{{reviewId}}/reply` | owner |  |
| Owner: delete reply | `DELETE` | `/api/v1/clubs/{{clubId}}/reviews/{{reviewId}}/reply` | owner |  |
| Admin: remove a review | `DELETE` | `/api/v1/clubs/{{clubId}}/reviews/{{reviewId}}` | admin |  |

### Chat

| Request | Method | Path | As | Notes |
|---|---|---|---|---|
| Inbox | `GET` | `/api/v1/chat/inbox` | member |  |
| Club chat conversation | `GET` | `/api/v1/chat/clubs/{{clubId}}` | member | Subscribers and the owner only. |
| Start a direct conversation | `POST` | `/api/v1/chat/direct` | member |  |
| Get a conversation | `GET` | `/api/v1/chat/conversations/{{conversationId}}` | member |  |
| Messages | `GET` | `/api/v1/chat/conversations/{{conversationId}}/messages?limit` | member |  |
| Send a message | `POST` | `/api/v1/chat/conversations/{{conversationId}}/messages` | member |  |
| Mark as read | `POST` | `/api/v1/chat/conversations/{{conversationId}}/read` | member |  |
| Upload a chat photo | `POST` | `/api/v1/chat/photos` | member |  |
| Delete a message | `DELETE` | `/api/v1/chat/messages/{{messageId}}` | member |  |
| Report a message | `POST` | `/api/v1/chat/messages/{{messageId}}/report` | member |  |
| Blocked users | `GET` | `/api/v1/chat/blocks` | member |  |
| Block a user | `PUT` | `/api/v1/chat/blocks/{{ownerId}}` | member |  |
| Unblock a user | `DELETE` | `/api/v1/chat/blocks/{{ownerId}}` | member |  |
| Owner: muted users | `GET` | `/api/v1/chat/clubs/{{clubId}}/mutes` | owner |  |
| Owner: mute a user | `PUT` | `/api/v1/chat/clubs/{{clubId}}/mutes/{{memberId}}` | owner |  |
| Owner: unmute a user | `DELETE` | `/api/v1/chat/clubs/{{clubId}}/mutes/{{memberId}}` | owner |  |
| Admin: reports | `GET` | `/api/v1/chat/reports?status&page&size` | admin |  |
| Admin: resolve a report | `POST` | `/api/v1/chat/reports/{{reportId}}/resolve` | admin |  |
| Live updates (server-sent events) | `GET` | `/api/v1/chat/stream` | member | A long-lived text/event-stream; Postman shows events as they arrive. Stop the request to close it. |

### Events

| Request | Method | Path | As | Notes |
|---|---|---|---|---|
| Defaults for new events | `GET` | `/api/v1/events/defaults` | owner |  |
| Owner: create event | `POST` | `/api/v1/events` | owner | Times are LocalDateTime in APP_TIME_ZONE. price 0 = free; capacity null = unlimited; publish=false keeps a draft. |
| Owner: publish | `POST` | `/api/v1/events/{{eventId}}/publish` | owner |  |
| Get event | `GET` | `/api/v1/events/{{eventId}}` | member |  |
| Owner: update event | `PUT` | `/api/v1/events/{{eventId}}` | owner | Partial update. The fee is locked once members have paid (409 FEE_LOCKED). |
| Owner: change registration window | `PATCH` | `/api/v1/events/{{eventId}}/registration-window` | owner |  |
| Owner: upload cover image | `POST` | `/api/v1/events/{{eventId}}/image` | owner |  |
| Owner: remove cover image | `DELETE` | `/api/v1/events/{{eventId}}/image` | owner |  |
| Upcoming events feed | `GET` | `/api/v1/events/upcoming?lat&lng&radiusKm&limit` | member | Clubs you follow or subscribe to, plus nearby clubs (by lat/lng, or by city when there are no coordinates). |
| Upcoming events by city | `GET` | `/api/v1/events/upcoming?city&limit` | member |  |
| Club events | `GET` | `/api/v1/events/club/{{clubId}}?upcoming&page&size` | member |  |
| Owner: event stats | `GET` | `/api/v1/events/{{eventId}}/stats` | owner |  |
| Owner: club event stats | `GET` | `/api/v1/events/club/{{clubId}}/stats` | owner |  |
| Owner: mark completed | `POST` | `/api/v1/events/{{eventId}}/complete` | owner | Only after the event has started. |
| Owner: cancel (refunds paid members) | `POST` | `/api/v1/events/{{eventId}}/cancel` | owner |  |

### Registrations, tickets & check-in

| Request | Method | Path | As | Notes |
|---|---|---|---|---|
| Register | `POST` | `/api/v1/events/{{eventId}}/register` | member | Free: CONFIRMED at once. Paid: PENDING_PAYMENT with a checkout (seat held 15 minutes). |
| My registrations | `GET` | `/api/v1/events/me/registrations` | member |  |
| A user's registrations | `GET` | `/api/v1/events/user/{{memberId}}` | member | Yourself or an admin. |
| Cancel my registration | `DELETE` | `/api/v1/events/{{eventId}}/registrations/me` | member | Free or unpaid registrations, before the start. Paid ones are refunded by the owner. |
| Ticket (JSON with QR) | `GET` | `/api/v1/events/registrations/{{registrationId}}/ticket` | member |  |
| Ticket QR (PNG) | `GET` | `/api/v1/events/registrations/{{registrationId}}/ticket.png` | member |  |
| Owner: registrations | `GET` | `/api/v1/events/{{eventId}}/registrations` | owner |  |
| Owner: check in | `POST` | `/api/v1/events/{{eventId}}/check-in` | owner | Opens 6 h before the start, closes 12 h after. 409 when already used; 404 for a ticket of another event. |

### Payments

| Request | Method | Path | As | Notes |
|---|---|---|---|---|
| Get payment | `GET` | `/api/v1/payments/{{paymentId}}` | member |  |
| Mock signature (dev only) | `POST` | `/api/v1/payments/{{paymentId}}/mock-signature` | member |  |
| Confirm payment | `POST` | `/api/v1/payments/{{paymentId}}/confirm` | member |  |
| Owner: refund | `POST` | `/api/v1/payments/{{paymentId}}/refund` | owner |  |
| Razorpay webhook | `POST` | `/api/v1/payments/webhooks/razorpay` | public | Called by Razorpay. Without a valid X-Razorpay-Signature it returns 400; use Razorpay's dashboard to send test webhooks. |

### Participation & leaderboards

| Request | Method | Path | As | Notes |
|---|---|---|---|---|
| My participation | `GET` | `/api/v1/participation/me?clubId` | member |  |
| A user's participation | `GET` | `/api/v1/participation/users/{{memberId}}` | member | Yourself or an admin. |
| Owner: club participation | `GET` | `/api/v1/participation/clubs/{{clubId}}` | owner |  |
| Monthly leaderboard | `GET` | `/api/v1/leaderboards/monthly?limit` | member | month=YYYY-MM, default this month. |

### Activities & Strava

| Request | Method | Path | As | Notes |
|---|---|---|---|---|
| Log an activity | `POST` | `/api/v1/activities` | member | type: RUN \| RIDE \| WALK \| HIKE \| SWIM \| WORKOUT \| YOGA \| OTHER. distance in km, duration in seconds. |
| My activities | `GET` | `/api/v1/activities/me?page&size` | member |  |
| A user's activities | `GET` | `/api/v1/activities/user/{{memberId}}` | member | Yourself or an admin (403 for anyone else). |
| Get an activity | `GET` | `/api/v1/activities/{{activityId}}` | member | 404 unless it is yours (or you are an admin). |
| Update an activity | `PUT` | `/api/v1/activities/{{activityId}}` | member |  |
| Delete an activity | `DELETE` | `/api/v1/activities/{{activityId}}` | member |  |
| Strava: connect URL | `GET` | `/api/v1/strava/connect` | member | Open the returned authorizeUrl in a browser. 503 when Strava isn't configured. |
| Strava: connection | `GET` | `/api/v1/strava/connection` | member |  |
| Strava: settings | `PUT` | `/api/v1/strava/connection/settings` | member |  |
| Strava: sync now | `POST` | `/api/v1/strava/sync` | member |  |
| Strava: imported activities | `GET` | `/api/v1/strava/activities/{{memberId}}` | member |  |
| Strava: delete imported activities | `DELETE` | `/api/v1/strava/activities` | member |  |
| Strava: disconnect | `DELETE` | `/api/v1/strava/connection` | member |  |
| Admin: Strava push subscription | `GET` | `/api/v1/strava/admin/subscription` | admin |  |
| Admin: create Strava push subscription | `POST` | `/api/v1/strava/admin/subscription` | admin | Registers STRAVA_WEBHOOK_CALLBACK_URL with Strava; the URL must be reachable from the internet. |
| Admin: delete Strava push subscription | `DELETE` | `/api/v1/strava/admin/subscription/{{stravaSubscriptionId}}` | admin |  |
| Strava: OAuth callback | `GET` | `/api/v1/strava/callback?code&state&scope` | public | Normally reached through the web app's /strava/callback page after the Strava consent screen; not useful to call by hand. |
| Strava webhook: subscription challenge | `GET` | `/api/v1/strava/webhook?hub.mode&hub.verify_token&hub.challenge` | public | Strava calls this when a subscription is created; echoes hub.challenge when the verify token matches. |
| Strava webhook: event | `POST` | `/api/v1/strava/webhook` | public | Called by Strava. Events for unknown athletes are acknowledged and ignored. |

### Notifications

| Request | Method | Path | As | Notes |
|---|---|---|---|---|
| My notifications | `GET` | `/api/v1/notifications/me?page&size` | member |  |
| Unread count | `GET` | `/api/v1/notifications/me/unread-count` | member |  |
| A user's notifications | `GET` | `/api/v1/notifications/user/{{memberId}}?page&size` | member | Yourself or an admin. |
| Mark one read | `PATCH` | `/api/v1/notifications/{{notificationId}}/read` | member |  |
| Mark all read | `POST` | `/api/v1/notifications/me/read-all` | member |  |
| Admin: send a notification | `POST` | `/api/v1/notifications` | admin |  |
| Admin: failed emails | `GET` | `/api/v1/notifications/admin/email-outbox?status&page&size` | admin |  |
| Admin: retry an email | `POST` | `/api/v1/notifications/admin/email-outbox/{{outboxId}}/retry` | admin |  |
| ZeptoMail bounce webhook | `POST` | `/api/v1/notifications/webhooks/zeptomail?token` | public | Called by ZeptoMail; the token is in secrets/zeptomail-webhook-token. |

## 9. Keeping the collection current

When you add or change an endpoint, update the matching request in Postman, **export** the collection (Collection v2.1) over `docs/postman/the-club.postman_collection.json`, and add a line to the reference table above. Run the end-to-end journey with Newman before opening the pull request.
