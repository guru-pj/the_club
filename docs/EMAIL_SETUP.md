# Email Setup — Zoho ZeptoMail

The Club sends these emails:
- email verification, password reset and "password changed" (user-service)
- registration confirmation with the QR ticket, refunds and event cancellations (event-service)

All of them are rendered and delivered by **notification-service**. In production it sends through the
**Zoho ZeptoMail** transactional API; in development everything goes to **Mailpit** (http://localhost:8025).

## How delivery works

```
user-service / event-service ──POST /internal/notifications──▶ notification-service
                                                                 │ one DB transaction:
                                                                 │   in-app notification + rendered email (outbox)
                                                                 ▼
                                               EmailDispatcher (every 5 s, every replica)
                                                                 │ claims due emails (SELECT … FOR UPDATE SKIP LOCKED)
                                                                 ▼
                                               ZeptoMail API  ─or─  SMTP (Mailpit / fallback)  ─or─  log
```

- **Never lost:** the email is stored with the notification before the API answers `202`. If ZeptoMail is
  down or rate-limits us, the dispatcher retries after 1 min, 5 min, 15 min, 1 h, then every 6 h, up to 8
  attempts. Permanent errors (bad address, unverified domain, invalid token) are marked `FAILED` immediately.
- **Safe with many replicas:** each dispatcher claims different rows with `SKIP LOCKED`, so running several
  notification-service pods sends faster and never sends the same email twice. If a pod dies mid-send, its
  emails return to the queue after 10 minutes. That can, rarely, mean one duplicate: delivery is
  *at least once*, never *zero times*. A leader lock (ShedLock) wasn't used because it would serialize all
  sending on one pod.
- **Branded templates:** every email has an HTML and a plain-text version, built from
  `notification-service/src/main/resources/templates/email/`. Values are HTML-escaped, so user input can't
  inject markup.
- **Bounce handling:** ZeptoMail reports hard bounces to a webhook, and those addresses are never emailed again.

## Choosing the provider

| `MAIL_PROVIDER` | Use for | Needs |
|---|---|---|
| `zeptomail` | **production** | `secrets/zeptomail-token`, `MAIL_FROM` on a verified domain, `ZEPTOMAIL_API_URL` for your region |
| `smtp` | development (Mailpit) or a fallback SMTP server | `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_STARTTLS`, `secrets/smtp-password` |
| `log` | tests | nothing; only logs recipient and subject. **Refused** under `SPRING_PROFILES_ACTIVE=prod` |

With `SPRING_PROFILES_ACTIVE=prod`, notification-service refuses to start if the provider is `log`, or if it
is `zeptomail` without a token, so a misconfiguration can't silently drop emails.

---

## Set up ZeptoMail (production)

### 1. Create the account

1. Sign up at https://www.zoho.com/zeptomail/. If you're in India, pick the **India** data center
   (zeptomail.zoho.in). Your data center decides the API URL you'll use in step 5.
2. Finish the account verification ZeptoMail asks for. New accounts are reviewed before they can send to any
   address.

### 2. Add and verify your sending domain

1. Go to **Domains → Add Domain** and enter the domain you'll send from, e.g. `theclub.example`.
2. ZeptoMail shows DNS records. Add **all** of them at your DNS provider, exactly as shown:
   - a **DKIM** `TXT` record (name like `<selector>._domainkey.theclub.example`), which signs your emails
   - a **CNAME** for the bounce address (name like `bounce-zem.theclub.example`), which aligns SPF and
     collects bounces
3. Click **Verify** once DNS has propagated (minutes to a few hours).

> **SPF and DMARC:** ZeptoMail sends with its own bounce (Return-Path) domain through that CNAME, so you
> normally don't need to change your root SPF record. If your domain publishes a DMARC policy, keep it at
> `p=none` until you've seen ZeptoMail emails pass DKIM in the headers.

### 3. Create a Mail Agent

1. Go to **Mail Agents → Add Mail Agent** and name it `The Club – transactional`.
2. Associate the verified domain with it.

### 4. Copy the Send Mail token

1. Open the Mail Agent → **SMTP/API** → **API** tab.
2. Copy the **Send Mail Token**. The sample request shows it as `Zoho-enczapikey <token>`; either form works.
3. On the server, from the project folder:

   ```bash
   printf '%s' 'PASTE-THE-TOKEN-HERE' > secrets/zeptomail-token
   chmod 644 secrets/zeptomail-token
   ```

   Never commit this file (`secrets/` is gitignored) or paste the token into chats or tickets.

### 5. Configure `.env`

```bash
MAIL_PROVIDER=zeptomail
# Must be an address on the domain verified in step 2
MAIL_FROM=The Club <no-reply@theclub.example>
# Optional
MAIL_REPLY_TO=support@theclub.example
# Your ZeptoMail data center:
ZEPTOMAIL_API_URL=https://api.zeptomail.in/v1.1/email
```

| Data center (where your account lives) | `ZEPTOMAIL_API_URL` |
|---|---|
| India (zeptomail.zoho.in) | `https://api.zeptomail.in/v1.1/email` |
| United States (zeptomail.zoho.com) | `https://api.zeptomail.com/v1.1/email` |
| Europe (zeptomail.zoho.eu) | `https://api.zeptomail.eu/v1.1/email` |
| Australia (zeptomail.zoho.com.au) | `https://api.zeptomail.com.au/v1.1/email` |
| Japan / Canada / Saudi Arabia | `https://api.zeptomail.jp/…`, `https://api.zeptomail.ca/…`, `https://api.zeptomail.sa/…` (`/v1.1/email`) |

Also set `REQUIRE_EMAIL_VERIFICATION=true` and `APP_BASE_URL=https://<your-domain>`. The links in emails
use `APP_BASE_URL`.

### 6. Restart notification-service

```bash
# Docker host (production file only)
./scripts/generate-secrets.sh                      # idempotent; creates any secret files added in newer versions
docker compose -f docker-compose.yml up -d notification-service
docker compose -f docker-compose.yml logs notification-service | grep -i "email provider"
# → "Email provider: zeptomail"
```

On Kubernetes, put the token into the notification-service secret (key `ZEPTOMAIL_TOKEN`) and roll the
deployment.

### 7. Send a test email

1. Open the app → **Log in → Forgot password?** and enter your own address.
2. Within a few seconds:
   - the email arrives
   - the notification-service log shows `Email dispatch: 1 delivered`
   - ZeptoMail → **Reports / Processed Emails** lists it
3. As an admin, check the outbox (empty means nothing is stuck):

   ```bash
   curl -s -H "Authorization: Bearer <admin access token>" \
     "https://<your-domain>/api/v1/notifications/admin/email-outbox?status=FAILED"
   ```

### 8. Bounce webhook (recommended)

1. Get the webhook secret created by `generate-secrets.sh`:

   ```bash
   cat secrets/zeptomail-webhook-token
   ```

2. In ZeptoMail → Mail Agent → **Webhooks → Add webhook**:
   - **URL:** `https://<your-domain>/api/v1/notifications/webhooks/zeptomail?token=<that secret>`
   - **Events:** *Hard bounce* (soft bounces are ignored; they're temporary).
3. Save. ZeptoMail offers a test call; it should get `200`.

ZeptoMail can't sign webhook calls, so the secret in the URL is what authenticates them (an
`X-Webhook-Token` header works too). Keep the URL private. To rotate it, write a new value into
`secrets/zeptomail-webhook-token`, restart notification-service and update the webhook URL.

---

## Development and staging

- **Local (default):** `docker-compose.override.yml` sets the provider to SMTP → Mailpit. Every email appears at
  http://localhost:8025. No account needed.
- **Try ZeptoMail locally:** add `DEV_MAIL_PROVIDER=zeptomail` to `.env`, fill in `secrets/zeptomail-token`,
  set `MAIL_FROM` to your verified domain, then run `docker compose up -d notification-service`.
- **Staging with real data:** set `MAIL_REDIRECT_ALL_TO=qa@theclub.example`. Every email then goes only to
  that address, with the real recipient shown in the subject (`[to member@gmail.com] …`), so testers never
  email real members by accident.

## Monitoring

| Metric (Prometheus) | Meaning |
|---|---|
| `theclub_emails_sent_total{provider,template}` | emails accepted by the provider |
| `theclub_emails_failed_total{provider,reason}` | failed attempts; `reason` = `retry`, `permanent`, `exhausted` or `suppressed` |
| `theclub_email_outbox_pending` | emails waiting to be sent; should be near 0 |

Alert if `theclub_email_outbox_pending` stays above ~50, or if `permanent`/`exhausted` failures appear.
Admins can list failed emails and re-send them:

```bash
GET  /api/v1/notifications/admin/email-outbox?status=FAILED     # also PENDING, SENDING, SENT, or no status for all
POST /api/v1/notifications/admin/email-outbox/{id}/retry        # FAILED → PENDING with a fresh set of attempts
```

Email bodies and tokens are never logged or returned by these endpoints.

## Troubleshooting

| Symptom | Cause and fix |
|---|---|
| notification-service won't start: "MAIL_PROVIDER=zeptomail needs a ZeptoMail Send Mail token" | `secrets/zeptomail-token` is empty or not mounted. Fill it in (step 4) and restart. |
| Won't start: "MAIL_PROVIDER=log would silently drop every email" | You run with `SPRING_PROFILES_ACTIVE=prod`; set `MAIL_PROVIDER=zeptomail`. |
| `docker compose` error: secret file `./secrets/zeptomail-token` not found | Your `secrets/` predates email support. Run `./scripts/generate-secrets.sh` (keeps existing secrets). |
| Emails `FAILED` with `HTTP 401 … TM_3501 … Invalid API Token` | Wrong token, a token from another Mail Agent, or the **wrong region URL** (an India token only works against `api.zeptomail.in`). Fix it, restart, then retry the failed emails via the admin API. |
| `FAILED` with a 4xx mentioning the sender/from address or domain | `MAIL_FROM` isn't on a domain verified in ZeptoMail (step 2), or the domain lost verification after a DNS change. |
| Emails stay `PENDING` with `HTTP 429` or `5xx` in `lastError` | ZeptoMail is rate-limiting or briefly unavailable; retries happen automatically. Check your ZeptoMail credits and quota if it persists. |
| Nothing is sent and the outbox grows | Check `MAIL_DISPATCHER_ENABLED` isn't `false` and look for `Email dispatch run failed` in the logs. |
| Emails land in spam | Make sure DKIM shows `pass` in the received email's headers, `MAIL_FROM` matches the verified domain, and DMARC isn't rejecting. |
| A member never gets emails | Their address may have hard-bounced: check the `email_suppression` table in `notificationdb` and delete the row once the address is fixed. |
