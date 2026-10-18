# Strava Integration — Setup Guide

This guide connects The Club to Strava, so members can link their Strava account and have their activities
imported automatically. You'll create a Strava API application (free), configure The Club with its credentials,
and register a webhook so new activities arrive within seconds.

**What members get**
- **Connect with Strava** on the Activities page, using Strava's own login and consent screen.
- New, edited and deleted Strava activities are mirrored automatically (webhook).
- **Sync**: imports the last 90 days on first use, then only what's new.
- **Private activities** are imported but don't count towards club leaderboards unless the member turns
  `includePrivate` on.
- **Disconnect** at any time, and optionally delete everything imported from Strava.

---

## 1. Create the Strava API application

1. Log in to Strava with the account that will own the integration (use a shared/company account if possible).
2. Open **https://www.strava.com/settings/api** and fill in:

   | Field | Value |
   |---|---|
   | Application Name | `The Club` (members see this on the consent screen) |
   | Category | `Social Motivation` (or the closest fit) |
   | Club | optional |
   | Website | your site, e.g. `https://club.example.com` (`http://localhost:3000` for local testing) |
   | Application Description | e.g. "Imports your activities into your club's leaderboards and events." |
   | **Authorization Callback Domain** | the **host only** of the web app: `localhost` for local testing, `club.example.com` in production (no `https://`, no path, no port) |

3. Upload an app icon if Strava asks for one, then save.
4. On the same page, note the **Client ID** (a number) and click **show** next to **Client Secret**.

> New Strava apps start with a limit of **1 connected athlete** (the owner) and a rate limit of **100 requests per
> 15 minutes / 1,000 per day** (read requests). That's enough to test. Before launching to members, request a
> higher athlete capacity from Strava. The link is on the API settings page and needs screenshots of how you
> display Strava data.

## 2. Configure The Club

Run these from the project folder.

**Client ID** goes in `.env`:
```bash
sed -i.bak 's/^STRAVA_CLIENT_ID=.*/STRAVA_CLIENT_ID=<your client id>/' .env && rm .env.bak
```

**Client secret** goes in a secret file. Paste it **without** a trailing newline and never commit it:
```bash
printf '%s' '<your client secret>' > secrets/strava-client-secret
```

**Token encryption key:** members' Strava tokens are encrypted at rest. The key is created by the secrets
generator; run it again to add any secrets missing from an older setup (it never overwrites existing files):
```bash
./scripts/generate-secrets.sh
ls -l secrets/strava-token-encryption-key
```
Back this key up with your other secrets. If it's lost, members just reconnect Strava.

**Check these values in `.env`:**

| Variable | Local testing | Production |
|---|---|---|
| `STRAVA_REDIRECT_URI` | `http://localhost:3000/strava/callback` | `https://club.example.com/strava/callback` |
| `STRAVA_WEBHOOK_VERIFY_TOKEN` | any random string | a long random string, e.g. `openssl rand -hex 24` |
| `STRAVA_WEBHOOK_CALLBACK_URL` | your tunnel URL + `/api/v1/strava/webhook` (step 5) | `https://club.example.com/api/v1/strava/webhook` |
| `STRAVA_INITIAL_SYNC_DAYS` | `90` | `90` |

The redirect URI's host must equal the **Authorization Callback Domain** from step 1.

Apply the changes:
```bash
docker compose up -d activity-service
```
With `SPRING_PROFILES_ACTIVE=prod`, the service refuses to start if the client secret, encryption key or a real
verify token is missing, or if the redirect URI isn't https.

## 3. Connect your own account (test)

1. Open the web app → **Activities** → **Connect with Strava**.
2. Approve on Strava, and keep **"View data about your private activities"** ticked; the app needs
   `activity:read_all`.
3. You're sent back to The Club. Click **Sync**: the last 90 days of activities appear.

## 4. Find an admin token for the API calls below

The webhook subscription is managed through admin-only API calls. Get an access token by logging in as the
platform admin:
```bash
ADMIN_TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d "{\"email\":\"admin@theclub.local\",\"password\":\"$(cat secrets/admin-password)\"}" | jq -r .accessToken)
echo "${ADMIN_TOKEN:0:20}..."
```
Use `https://your-domain` instead of `http://localhost:8080` in production. The token is valid for 15 minutes.

## 5. Register the webhook (automatic imports)

Strava pushes events to **one public HTTPS URL per application**. When you create the subscription, Strava
immediately calls `GET <callback>?hub.mode=subscribe&hub.verify_token=…&hub.challenge=…` and expects The Club to
answer within 2 seconds. The URL must therefore be reachable from the internet **before** you create the
subscription.

**Local testing: expose your machine with a tunnel.** Pick one option.

- cloudflared (no account needed for quick tunnels):
  ```bash
  # Linux: download cloudflared once
  curl -fsSL -o cloudflared https://github.com/cloudflare/cloudflared/releases/latest/download/cloudflared-linux-amd64 && chmod +x cloudflared
  # macOS: brew install cloudflared
  ./cloudflared tunnel --url http://localhost:3000
  ```
  It prints a URL like `https://random-words.trycloudflare.com`.
- ngrok (free account):
  ```bash
  ngrok http 3000
  ```

The web app's nginx forwards `/api/` to the gateway, so the tunnel targets port 3000. Then set
`STRAVA_WEBHOOK_CALLBACK_URL=https://<tunnel-host>/api/v1/strava/webhook` in `.env`, run
`docker compose up -d activity-service`, and continue below. A quick tunnel's URL changes every time it restarts;
when it does, delete and re-create the subscription.

**Check that the callback answers** (replace the token with your `STRAVA_WEBHOOK_VERIFY_TOKEN`):
```bash
curl -s "https://<host>/api/v1/strava/webhook?hub.mode=subscribe&hub.verify_token=<verify token>&hub.challenge=test123"
# → {"hub.challenge":"test123"}
```

**Create the subscription:**
```bash
curl -s -X POST http://localhost:8080/api/v1/strava/admin/subscription -H "Authorization: Bearer $ADMIN_TOKEN"
# → {"id":123456,"callback_url":"https://…/api/v1/strava/webhook",…}
```

**Inspect or remove it:**
```bash
curl -s http://localhost:8080/api/v1/strava/admin/subscription -H "Authorization: Bearer $ADMIN_TOKEN" | jq
curl -s -X DELETE http://localhost:8080/api/v1/strava/admin/subscription/<id> -H "Authorization: Bearer $ADMIN_TOKEN"
```

Test it: record or edit an activity on Strava. Within about a minute it appears (or updates) on the Activities
page. Each event is stored first and then processed, so a restart never loses one:
```bash
docker compose exec postgres psql -U postgres -d activitydb \
  -c "SELECT aspect_type, status, attempts, last_error, received_at FROM strava_webhook_events ORDER BY received_at DESC LIMIT 10;"
```

## 6. Rate limits and monitoring

Strava limits each **application** (shared by all members) per 15 minutes and per day. The Club reads the usage
headers on every response and pauses calls at 90% until the window resets: the next :00/:15/:30/:45 UTC, or
midnight UTC for the daily limit. Webhook events are re-queued, never dropped. Manual syncs return
"Strava's request limit has been reached. Try again in N minutes."

Watch usage with the observability stack (Prometheus):
- `theclub_strava_ratelimit_usage{window="15min"}` / `theclub_strava_ratelimit_limit{window="15min"}`
- `theclub_strava_api_calls_total{outcome="rate_limited"}`
- `theclub_strava_webhook_events_total{outcome="failed"}`

## 7. Strava brand guidelines

Strava requires apps that display its data to follow its guidelines
(https://developers.strava.com/guidelines/):
- Use the official **"Connect with Strava"** button artwork for the connect action.
- Show **"Powered by Strava"** (or "Compatible with Strava") where Strava data is displayed, and link imported
  activities back to Strava ("View on Strava") where practical.
- Don't imply Strava endorses The Club.
- Respect privacy:
  - Private activities are never shown to other members, and only count towards club leaderboards when the
    member opts in.
  - When a member asks, delete their imported data: `DELETE /api/v1/strava/activities`.

Review the current [Strava API Agreement](https://www.strava.com/legal/api) before launch.

## 8. Troubleshooting

| Symptom | Cause / fix |
|---|---|
| "Strava integration is not configured" (503) | `STRAVA_CLIENT_ID` is empty in `.env`, or `activity-service` wasn't restarted. |
| Strava shows **"Bad Request — redirect_uri invalid"** | The host in `STRAVA_REDIRECT_URI` doesn't match the app's **Authorization Callback Domain**. Use the host only (`localhost` or `club.example.com`). |
| Strava shows **"Bad Request — client_id invalid"** / "Could not complete Strava authorization" | Wrong `STRAVA_CLIENT_ID`, or `secrets/strava-client-secret` is wrong or has a trailing newline (re-create it with `printf '%s'`). |
| "Allow access to your activities on Strava" | The member unticked the activities permission on Strava's consent screen. Connect again and keep it ticked. |
| Subscription creation fails: **"GET to callback URL does not return 200"** | The callback isn't publicly reachable, isn't https, or `STRAVA_WEBHOOK_VERIFY_TOKEN` differs from what's configured. Test it with the `curl … hub.challenge=test123` command above. |
| Subscription creation fails: **"already exists"** (409) | An app can have one subscription. List it and delete it first (step 5). |
| Sync returns 503 / **429** in logs | The application's rate limit is spent. Wait for the window to reset; request higher limits from Strava for launch. |
| A member sees "Your Strava connection has expired. Reconnect…" | They revoked The Club on Strava (or the refresh token stopped working). They received an in-app notification; reconnecting fixes it. |
| `Could not decrypt a Strava token` at startup or in logs | `secrets/strava-token-encryption-key` changed or is missing. Restore the original key, or have affected members reconnect. |
| Events stuck in `PENDING` / `FAILED` | Check `last_error` in `strava_webhook_events` (SQL above) and the `activity-service` logs. |
