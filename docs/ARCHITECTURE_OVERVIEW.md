# How The Club Works

The Club is a website where fitness clubs (running, cycling, swimming and more) run their community: members join clubs, log workouts, sign up and pay for events, get a QR ticket, check in on the day, and earn points on monthly leaderboards. This document explains how it is built, in plain language, with no technical background assumed. Every technical word is explained where it first appears and again in the [Glossary](#glossary) at the end.

For engineers: service contracts are in [SERVICE_CONTRACTS.md](SERVICE_CONTRACTS.md); deployment is in [DEPLOY_LINUX.md](DEPLOY_LINUX.md), [DEPLOY_MACOS.md](DEPLOY_MACOS.md) and [PRODUCTION.md](PRODUCTION.md).

## Who uses it

Three kinds of people use The Club. Everyone starts as a **Member**; the other two roles are granted.

| Role | Who they are | How they get the role | What they can do |
| --- | --- | --- | --- |
| **Member** | Anyone who signs up | Sign up with name, email and password, then click the link in the welcome email | Join clubs, log workouts, connect Strava, register and pay for events, carry QR tickets, earn points and stars, see leaderboards, receive notifications |
| **Club owner** | The person who runs a club (a coach or organiser) | Applies inside the app with the club's details and proof (social links, documents); an Admin approves | Everything a Member can do, plus: create and edit their club, post announcements and event recaps, create events with dates, capacity, price and points, see who registered, refund payments, cancel events, scan QR tickets at the door |
| **Admin** | The platform operator (us) | Created automatically when the system is first installed | Review club-owner applications: approve (and create the club in one step), reject with a reason, or ask for more information |

A person can only own clubs after approval. This keeps fake or spam clubs off the platform, because events can take real money.

## What it does

The Club was built in two phases. Both are complete.

| Area | What the user experiences | Rules worth knowing |
| --- | --- | --- |
| **Accounts** | Sign up, confirm email, log in, forgot password, change password, "sign out of all devices", edit profile (name, city, bio, uploaded photo, location). New accounts get a short profile setup after their first login. | 5 wrong passwords lock the account for 15 minutes. A reset link works for 30 minutes. Login stays active while the site is open and refreshes itself quietly. A location is stored only to about 100 m and never shown to others. |
| **Discover** | Clubs near you (closest first, with distance), popular clubs, and upcoming events from nearby clubs; search by name and city | "Near" means within 25 km of the location the member shared; without one, clubs in their city |
| **Clubs** | Club page with logo, photo gallery, address and map link, members, events, posts, and reviews. **Follow** a club for its updates and event announcements, or **subscribe** (free membership) where the club offers it. | Following and subscribing are separate. Subscribers appear on leaderboards, can register for subscriber-only events and can review. Club types: running, cycling, triathlon, swimming, fitness, other |
| **Reviews & ratings** | 1–5 stars and a comment per person, shown with the average and a breakdown; the owner can reply | Only subscribers and people who checked in at one of the club's events review; never the owner. Owners can't delete reviews; admins remove abusive ones. |
| **Club feed** | Owners post announcements and event recaps with photos, optionally attaching one of their own Strava workouts, and reshare other clubs' posts and events. Subscribers post too. Anyone signed in can like and share a post; followers and subscribers comment. A shared link shows a preview to people who aren't signed in. | Only the owner posts announcements and reshares. Authors are told about comments. The owner can remove any post or comment. |
| **Club chat & messages** | Each club has a live group chat for its subscribers and owner. People in the same club can message each other directly. Photos can be sent too. | Followers can't read the club chat. Anyone can block someone or report a message to the admins; the owner can delete messages and mute people in the club chat. Messages are stored on the server and are not end-to-end encrypted. |
| **Activities** | Members log workouts by hand (run, ride, walk, hike, swim, workout, yoga, other) with distance and time, or import them from Strava | Private Strava workouts are imported but don't count on club leaderboards unless the member opts in |
| **Strava link** | "Connect with Strava" once; new, edited and deleted Strava workouts then mirror into The Club automatically within about a minute | First connection imports the last 90 days. Members can disconnect and delete imported data at any time. |
| **Club leaderboard** | Ranks club members by distance, time or number of sessions, for this week, month, year or all time | Built from the activities above |
| **Events** | Owners create events with date, place, capacity, price (0 = free), points for attending, a registration window (opens / closes), rules and instructions, and a cover image. Members see upcoming events from clubs they follow, subscribe to or are near, each showing whether registration is open. | Registration is possible only inside the window and while seats remain. Anyone signed in may register, unless the owner marks the event subscribers-only. |
| **Registration & payment** | Members tap Register. Free events confirm instantly; paid events open a payment screen (Razorpay: cards, UPI, net banking) | An unpaid seat is held for 15 minutes, then released |
| **Confirmation & ticket** | After confirmation: an in-app notification, a confirmation email, and a **QR ticket** in the Tickets page | The QR code is unique per registration |
| **Check-in** | On the day, the owner opens the scanner on a phone and scans each ticket (or types the code) | Check-in opens 6 hours before the start and closes 12 hours after; a ticket can't be used twice |
| **Cancellations & refunds** | Members can cancel free registrations, or unpaid ones, before the start. Owners refund individual payments. Cancelling a paid event refunds everyone automatically and emails them. | Members can't self-refund a paid ticket; they contact the owner |
| **Points & stars** | Every check-in earns the event's points (10 by default). Every 5 events attended earns a star. | Points come only from real check-ins, not registrations |
| **Monthly leaderboard** | Top members and top clubs of the month, platform-wide | Based on points from check-ins |
| **Notifications** | Bell icon: new club posts and new events (for followers and subscribers), registration confirmations, refunds, event cancellations, owner-application decisions | Also sent by email for the important ones (see below) |

**Emails the system sends:** email confirmation, password reset, "your password was changed", registration confirmed (with QR ticket), payment refunded, event cancelled.

## The building analogy

Think of The Club as an **office building**. Visitors enter through one reception desk, which sends each request to the right department. Every department does one job, keeps its own filing cabinet, and talks to other departments through internal phone lines.

| Part (technical name) | In the building | What it actually does |
| --- | --- | --- |
| **Web app** | The shop window and forms visitors fill in | The website people see in their browser or phone: pages, buttons, forms, the QR scanner. It contains no data itself; it asks the departments for everything. |
| **Gateway** | Reception desk | The single entry point for every request. It checks the visitor's badge (login), turns away anyone sending too many requests (to stop attacks), and forwards the request to the right department. It never lets outsiders reach internal-only phone lines. |
| **Auth service** | Security office | Handles logging in and out: checks the typed password, issues the digital badge that proves who you are (valid 15 minutes, renewed silently), locks accounts after repeated wrong passwords, and can cancel all of a person's sessions. |
| **User service** | HR / member records | Keeps each person's profile, role (member, club owner, admin) and scrambled password. Runs email confirmation, forgot-password and change-password. Creates the first admin when the system is installed. |
| **Club service** | Club management office | Clubs, who belongs to which club, club feeds, posts, likes and comments, club chat and direct messages, club leaderboards, and the club-owner application process. |
| **Activity service** | Training log office | Workouts logged by hand, the Strava connection, and the automatic import of Strava workouts. |
| **Event service** | Events and box office | Events, registration windows, seats, payments and refunds, QR tickets, check-in, points, stars and the monthly leaderboard. |
| **Notification service** | Mailroom | Delivers in-app notifications and sends emails. Emails are queued first and posted afterwards, so none are lost if the email provider is briefly down. |
| **Discovery service** (Eureka) | Internal phone directory | Every department registers its current phone number here when it starts. Others look it up instead of hard-coding it, so departments can move or be duplicated without anyone noticing. |
| **Config service** | Policy binder | One central place for shared settings. |
| **Databases** (MySQL and PostgreSQL) | Filing cabinets | Where information is stored permanently. Each department has its own drawer and cannot open another's. |
| **Backup jobs** | Night-shift archivist | Copies every filing cabinet every night at 02:30 and keeps 14 days of copies. |
| **Caddy** (production only) | The building's front gate with a lock | Gives the site its padlock (HTTPS) so everything between the visitor and us is encrypted, and renews the certificate automatically. |
| **Monitoring** (Prometheus, Grafana, Loki, Tempo) | CCTV and dashboards | Records how busy and healthy each department is, keeps the logs, and raises alerts. |

In technical terms, each department is a **service** (a small, separate program). Building an application from several services is called a **microservices architecture**; the alternative, one big program, is a **monolith**.

## At a glance

**Every request enters through one door; each department keeps its own data.**

```
            ┌──────────────────────────────────────┐
            │     Members, club owners, admins     │
            │      in a browser or on a phone      │
            └──────────────────┬───────────────────┘
                               ▼
            ┌──────────────────────────────────────┐
            │       Caddy: the HTTPS padlock       │
            └──────────────────┬───────────────────┘
                               ▼
            ┌──────────────────────────────────────┐
            │  Web app: pages, forms, QR scanner   │
            └──────────────────┬───────────────────┘
                               ▼
            ┌──────────────────────────────────────┐
            │       Gateway (reception desk)       │
            │ checks login, limits floods, routes  │
            └──────────────────┬───────────────────┘
                               ▼
┌─ Six departments, each owns its database ─────────────┐   ┌─ Supporting services ─┐
│                                                       │   │                       │
│  ┌───────────────┐ ┌───────────────┐ ┌───────────────┐│   │  Phone directory      │
│  │ Auth          │ │ User          │ │ Club          ││   │  Central settings     │
│  │ Logins,       │ │ Profiles,     │ │ Clubs, feed,  ││ ┄ │  Nightly backups      │
│  │ sessions      │ │ passwords     │ │ owners        ││   │  Monitoring, alerts   │
│  │ DB: authdb    │ │ DB: userdb    │ │ DB: clubdb    ││   │                       │
│  └───────────────┘ └───────────────┘ └───────────────┘│   └───────────────────────┘
│  ┌───────────────┐ ┌───────────────┐ ┌───────────────┐│
│  │ Activity      │ │ Event         │ │ Notification  ││
│  │ Workouts,     │ │ Tickets,      │ │ In-app and    ││
│  │ Strava        │ │ payments      │ │ email         ││
│  │ DB: activitydb│ │ DB: eventdb   │ │ DB: notif.db  ││
│  └───────▲───────┘ └───────▲───────┘ └───────┬───────┘│
└──────────┼─────────────────┼─────────────────┼────────┘
           ▼                 ▼                 ▼
   ┌───────────────┐ ┌───────────────┐ ┌───────────────┐
   │ Strava        │ │ Razorpay      │ │ Zoho ZeptoMail│
   │ outside co.   │ │ outside co.   │ │ outside co.   │
   └───────────────┘ └───────────────┘ └───────────────┘
```

Read it top to bottom: every request passes the padlock, the web app and the reception desk before reaching a department. Departments never share a database. Two-headed arrows mean messages flow both ways: Strava and Razorpay also call us back. The supporting services are helpers every department relies on.

## Why it's built as separate pieces

We chose separate services over one big program. For a product manager, the practical effects are:

| Benefit | What it means for the product |
| --- | --- |
| **Failures stay contained** | If the mailroom (emails) is down, people can still sign up, log workouts and register; their emails wait in a queue and go out when it recovers. |
| **Scale only what's busy** | On a big race day, we can run three copies of the events service without paying to copy everything else. |
| **Safer changes** | A change to leaderboards touches only the club or event service; the payment code is not even redeployed. |
| **Clear ownership** | Each area (accounts, clubs, events, …) has its own code and data, so teams or contractors can work in parallel. |
| **Swap providers easily** | Email (Zoho ZeptoMail) and payments (Razorpay) sit behind one department each. Changing provider is a contained project. |

| Cost | How we handle it |
| --- | --- |
| More moving parts to run | Everything is packaged in containers and starts with one command; health checks restart broken parts automatically |
| Departments must talk over the network | The phone directory (discovery service) finds each one; calls have timeouts and retries |
| Information is split across filing cabinets | Each department owns its data and asks others when it needs something; there are no shared tables to get out of sync |
| Harder to trace a problem across departments | Every request carries a tracking number through all departments, and monitoring shows the whole path |

**In one sentence:** it is a microservices system kept in a single code repository, which gives the safety of separate parts with the convenience of one place to manage the code.

## Behind the scenes: what happens when…

Every action below starts the same way: the browser sends a request to the **gateway** (reception), which checks the login badge and forwards it. These steps are left out below to keep each journey short.

### …someone signs up

1. The person fills in name, email and password in the web app.
2. The **user service** checks the password is strong enough and creates their profile with the role Member.
3. It stores the password in scrambled form (a *hash*): even we cannot read it, only check whether a typed password matches.
4. It creates a one-time confirmation link and asks the **notification service** to email it.
5. The notification service writes the email into its queue, then a background worker sends it through **Zoho ZeptoMail**.
6. The person clicks the link and the user service marks the email as confirmed. In production, logging in is blocked until this happens.

### …someone logs in

1. The auth service fetches the stored hash from the user service over an internal line and checks the typed password against it.
2. After 5 wrong attempts, the account is locked for 15 minutes.
3. On success it issues two things: a short-lived **access badge** (a *token*, valid 15 minutes) and a long-lived **renewal pass** stored in a protected browser cookie that page scripts can't read.
4. Every request carries the access badge. Each department checks it itself using the auth service's public signature, so the auth service isn't asked on every click.
5. When the badge expires, the web app quietly exchanges the renewal pass for a new one. "Sign out of all devices" cancels every renewal pass.

### …someone becomes a club owner

1. A member submits an application (club name, city, social links, proof links).
2. The **club service** stores it as Pending. An Admin sees it in Owner applications.
3. The Admin approves, rejects with a reason, or asks for more information (the member can then update and resubmit).
4. On approval, the **user service** upgrades the person to Club owner and, if the Admin chose, the club is created in the same step. The person gets a notification and sees owner tools after logging in again.

### …a member joins a club and logs a workout

1. Join: the club service adds a membership record (member of club X).
2. Log: the **activity service** saves the workout (type, distance, time, date).
3. Club leaderboard: when anyone opens it, the club service gets the member list and asks the activity service for their totals for the chosen period, then ranks them.

### …a member connects Strava

1. The member clicks Connect with Strava and is sent to Strava's own login and consent page. We never see their Strava password.
2. Strava sends them back with a one-time code. The activity service swaps it for access keys, **encrypts** the keys and stores them.
3. The first sync imports the last 90 days of workouts.
4. From then on, when the member records a workout, Strava calls our **webhook** (a doorbell Strava rings to say something changed). We note the event in a durable inbox first, reply "got it" within 2 seconds, then a background worker fetches the details and saves the workout. Edits and deletions are mirrored the same way.
5. Strava limits how often any app may call it (per 15 minutes and per day). We track usage and pause at 90%, resuming automatically when the window resets. No events are lost; they wait in the inbox.

### …a member registers and pays for an event

1. The member taps Register. The **event service** checks: is registration open, are seats left, is the member not already registered?
2. **Free event:** the registration is confirmed immediately. Skip to step 7.
3. **Paid event:** a seat is held as *Pending payment* for 15 minutes, and a payment order is created with **Razorpay**.
4. The Razorpay payment window opens inside our page. The member pays by card, UPI or net banking. Card details go to Razorpay, never to us.
5. We learn about the payment in three independent ways, so a closed browser or lost connection never loses a paid seat:
    1. The browser reports success, and we verify Razorpay's digital signature on that report.
    2. Razorpay calls our payment **webhook** directly, also signed.
    3. Every 5 minutes a **reconciliation** job asks Razorpay about any payment still pending.
6. If nothing is paid within 15 minutes, the seat is released and the payment marked Expired.
7. Once confirmed, a unique ticket code is created and the event service tells the notification service, which posts an in-app notification and queues a confirmation email with the **QR ticket** attached.

### …a member arrives on event day

1. The member opens Tickets and shows the QR code (a picture of their ticket code).
2. The owner opens the event's check-in scanner on a phone; the camera reads the code (or the owner types it).
3. The event service checks: right event, check-in window open (6 hours before start to 12 hours after), ticket not already used, registration confirmed.
4. The registration becomes *Checked in* and the member earns the event's points. Every 5th event attended adds a star.
5. The monthly leaderboard (top members and top clubs) updates from these points.

### …an owner refunds or cancels

1. **Refund one person:** the event service asks Razorpay to refund; the payment becomes *Refund pending*, then *Refunded* when Razorpay confirms. The member is notified and emailed.
2. **Cancel an event:** the event is marked Cancelled, every paid registration is refunded automatically, and everyone registered gets a notification and an "event cancelled" email.

### …someone forgets their password

1. They enter their email on the login page. The response is the same whether or not the email exists, so nobody can probe which emails have accounts.
2. If the account exists, the user service emails a reset link valid for 30 minutes.
3. Setting a new password signs them out everywhere and sends a "your password was changed" email, so a real owner notices an unexpected change.

## Where information is kept

Each department has its own database, a structured store that keeps information safely when the system restarts. A department can read only its own database. If it needs something another department owns, it asks that department.

| Database | Owned by | What it holds | Engine |
| --- | --- | --- | --- |
| userdb | User service | Profiles (name, email, city, bio, photo link), roles, scrambled passwords, email-confirmation and password-reset links | MySQL |
| authdb | Auth service | Active login sessions (renewal passes), recent login attempts used for lockouts | MySQL |
| clubdb | Club service | Clubs, memberships, feed posts, likes, comments, chat messages, club-owner applications and admin decisions | MySQL |
| activitydb | Activity service | Workouts (manual and from Strava), Strava connections with encrypted keys, the Strava event inbox, Strava usage counters | PostgreSQL |
| eventdb | Event service | Events, registrations, payments and refunds, ticket codes, check-ins, points and stars | MySQL |
| notificationdb | Notification service | In-app notifications, the email queue with delivery status, addresses that bounced (so we stop emailing them) | MySQL |

**Two database products:** MySQL and PostgreSQL are both widely used, free, open-source databases. Activities use PostgreSQL because it handles large volumes of time-based, number-heavy data well; the rest use MySQL.

**What we never store:** card numbers or bank details (Razorpay holds those), Strava passwords, readable passwords, or photos and documents themselves (we store links to them).

**Identifiers:** every record gets a long random-looking ID (a *UUID*) rather than 1, 2, 3…, so nobody can guess other records' addresses or count how many users we have.

**Changing the structure safely:** every change to a database's layout is written as a numbered migration script that runs automatically on start-up, in order, exactly once. Every environment ends up with an identical structure, and changes are reviewed like any other code.

**Analytics:** an optional query tool, Trino, can read across all the databases at once for reports, without touching the live services.

## Outside services we depend on

| Service | What we use it for | Cost model | If it is down or misconfigured |
| --- | --- | --- | --- |
| **Razorpay** | Taking payments (cards, UPI, net banking) and refunds | Fee per successful transaction | New paid registrations can't complete; seats are held 15 minutes and then released. Payments already taken are confirmed later by the 5-minute reconciliation. Free events are unaffected. |
| **Zoho ZeptoMail** | Sending emails | Pay per email credits | Emails wait in our queue and are retried automatically. A wrong or expired key also keeps them queued until fixed; nothing is dropped. Everything else keeps working. |
| **Strava** | Importing members' workouts | Free API, with call limits per app | Imports pause; Strava's notifications wait in our inbox and are processed when it returns. Manual workout logging still works. |
| **Let's Encrypt** (via Caddy) | The HTTPS certificate (the browser padlock) | Free | Certificates last 90 days and renew automatically about 30 days early, so a short outage has no effect. |
| **GitHub** | Stores the code; runs automatic tests and security scans on every change | Free / per seat | Development pauses; the live site is unaffected. |
| **Cloud or server provider** | The machine the site runs on | Monthly | The site is down until the machine is back; data is safe in its databases and backups. |

**Strava access limits:** a new Strava app starts with a limit of 1 connected athlete and about 100 requests per 15 minutes. Before launching to members, we must apply to Strava for higher limits, and follow Strava's brand rules ("Connect with Strava" button, "Powered by Strava" label).

## Security and privacy

| Protection | What it means in practice |
| --- | --- |
| **Encrypted connection (HTTPS)** | Everything between a member's device and us is encrypted, shown by the browser padlock. A one-line switch (HSTS), turned on at go-live, tells browsers to always use HTTPS. |
| **One public door** | Only the website is reachable from the internet. Departments, databases and internal tools are on a private network. |
| **Passwords** | Stored only as hashes. Minimum strength rules apply. 5 wrong tries lock the account for 15 minutes. |
| **Short-lived login badges** | A stolen badge stops working within 15 minutes. Renewal passes sit in a cookie that page scripts can't read and other websites can't use. |
| **Sign out everywhere** | Members can end every session on every device; changing or resetting a password does this automatically. |
| **Email confirmation** | In production, an account can't log in until its email is confirmed, which stops sign-ups with other people's addresses. |
| **No account probing** | "Forgot password" answers the same way whether or not an email exists. |
| **Rate limits** | Each visitor's address may make at most 10 login/sign-up/reset attempts per minute and 300 requests per minute overall, which stops password-guessing and floods. |
| **Roles enforced on the server** | Hiding a button isn't relied on. Every department checks the badge's role before acting (for example, only that club's owner can scan its tickets). |
| **Internal lines are locked** | Department-to-department calls need a secret internal key, and the reception desk refuses them from outside. |
| **Payments** | Card data never touches our systems. Every payment message from Razorpay is checked against a digital signature, so faked "payment succeeded" messages are rejected. |
| **Secrets kept out of code** | Passwords, signing keys and API keys live in a protected folder on the server, never in the code or settings files. In production the system refuses to start if any is missing or still a placeholder. |
| **Strava keys encrypted** | Members' Strava keys are encrypted in the database with a separate key. |
| **Member control** | Members can disconnect Strava and delete all imported data. Private Strava workouts never appear to others. |
| **Security headers** | The site tells browsers to block common attacks (embedding our pages in other sites, running unexpected scripts). |
| **Automated checks** | Every code change is scanned for vulnerable libraries and insecure code before it can ship. |

## When things go wrong

The system is designed so that a failure delays work instead of losing it.

| Situation | What happens | What the user notices |
| --- | --- | --- |
| A department crashes | Each part has a health check. A broken part is restarted automatically, and the others keep running. | That feature may be unavailable for under a minute; the rest of the site works. |
| A department restarts and moves | It re-registers in the phone directory; others find its new address within seconds. | Possibly a brief "try again" message. |
| Email provider is down | Emails stay in the queue and are retried with growing gaps. The queue is in the database, so a restart doesn't lose them. | Emails arrive late, not never. |
| An email address bounces | The address is recorded and not emailed again, protecting our sender reputation. | — |
| Member closes the browser mid-payment | The Razorpay webhook or the 5-minute reconciliation confirms the payment. | The ticket appears a few minutes later. |
| Member starts paying but gives up | The held seat is released after 15 minutes. | Someone else can take the seat. |
| Strava is down or rate-limited | Notifications wait in our inbox and are processed later. | Workouts appear late. |
| Two copies of a service run at once (for scale) | Scheduled jobs (payment checks, email sending, Strava processing) use locks, so each job runs once, not twice. | — |
| Server disk or database failure | Every database is backed up nightly at 02:30; 14 days are kept. Copies can be pushed to separate storage (cloud bucket or another machine), and any database can be restored on its own. | Up to one day of data could be lost in the worst case; managed databases with continuous backup are the next step up. |
| Server reboots | Everything is set to start again automatically. | A few minutes of downtime. |
| A bad code change | Automated tests run on every change before it can ship, including tests against real MySQL and PostgreSQL. The previous version can be redeployed. | Ideally nothing. |

## Where it runs, how we watch it, how changes ship

### Packaging: containers

Each department is packaged as a **container**: a sealed box holding the program and everything it needs, which runs identically on a laptop, a Mac or a cloud server. **Docker** is the tool that runs containers, and **Docker Compose** starts the whole set with one command.

### Hosting options

| Option | Good for | Notes |
| --- | --- | --- |
| Single cloud server (recommended start) | Launch and early growth | One machine with 4 CPUs and 8 GB memory runs everything. Step-by-step guide: [DEPLOY_LINUX.md](DEPLOY_LINUX.md). |
| A Mac at home or in an office | Demos, a small club | Works, but the Mac must stay awake and online. Guide: [DEPLOY_MACOS.md](DEPLOY_MACOS.md). |
| Kubernetes (cloud cluster) | Larger scale, high availability | A ready-made setup (Helm chart) runs at least 2 copies of each department, adds copies automatically under load, and restricts which parts may talk to which. See [KUBERNETES.md](KUBERNETES.md). |

### Environments

| Environment | Payments | Emails | Purpose |
| --- | --- | --- | --- |
| Local / test | Fake (mock) payments, no money moves | Caught by a local inbox viewer (Mailpit) | Development, demos, testing |
| Production | Real Razorpay | Real delivery via ZeptoMail | Real members |

In production mode, the system **refuses to start** if a test setting (like fake payments) or a missing key is left in place. This is a deliberate safety net.

### Watching it (observability)

- **Metrics:** numbers over time, such as requests per second, error rates, response times, and business counts (sign-ups, registrations, payments, emails sent, Strava imports). Shown on **Grafana** dashboards.
- **Logs:** each department's diary of what it did, collected in one searchable place.
- **Traces:** one request followed through every department it touched, with timings, to find where something slowed down or failed.
- **Alerts:** automatic warnings, for example when a department is down, errors spike, emails pile up, or Strava limits are near.

Details: [OBSERVABILITY.md](OBSERVABILITY.md).

### How changes ship

1. A developer changes the code and pushes it to GitHub.
2. GitHub automatically runs all tests (including against real databases), builds the website, and scans for security problems.
3. If everything passes on the main branch, it builds new container images and scans them.
4. To deploy, the server pulls the new code or images and restarts the changed parts. Database layout changes apply themselves on start-up.
5. Dependency updates (new versions of the libraries we use) are proposed automatically every week.

## Limits, known gaps and what's next

### Known gaps today

| Gap | Impact | Effort to close |
| --- | --- | --- |
| Owner proof documents are links, not uploads | Applicants must host documents elsewhere and paste links | Small: reuse the image storage |
| Club-owner decisions arrive as in-app notifications only | Applicants who don't log in won't notice the decision | Small: the email template exists; it needs connecting |
| Members can't cancel a paid ticket themselves | They must contact the owner for a refund | Small to medium, depending on refund policy |
| No waiting list for full events | Full means full; a released seat goes to whoever registers next | Medium |
| Rate limits are counted per server copy | With several gateway copies, limits become looser | Medium: move counters to a shared store (Redis) |
| Expired one-time links and old login attempts aren't cleaned up | Tables grow slowly over time | Small: a nightly clean-up job |
| Live ZeptoMail, Razorpay and Strava accounts not yet tested end to end | Must be verified before launch | Run the go-live checks in the deployment guides |
| Event times use one time zone (default India) | Clubs in other countries see times in that zone | Medium |
| No push notifications | New messages and posts show up only when the app or site is open | Medium: phone push services |

### Planned next (Phase 3 ideas)

- Competitions between clubs
- Training plans
- Linking Strava workouts to events automatically (attendance proof)
- Paid club memberships
- Analytics dashboards for owners

## Glossary

| Term | Plain meaning |
| --- | --- |
| API | The set of requests one program can make to another, like a menu a restaurant offers |
| Access token (badge) | A digital pass proving who you are, valid 15 minutes |
| Backup | A copy of the data kept in case the original is lost |
| Caddy | The program at the front of the server that provides HTTPS |
| CI/CD | Automatic testing (continuous integration) and shipping (continuous delivery) of code changes |
| Container | A sealed package holding a program and everything it needs to run |
| Cookie | A small piece of data a website stores in your browser |
| Database | Organised, permanent storage for information |
| Discovery service (Eureka) | The internal phone directory departments use to find each other |
| Docker / Docker Compose | Tools that run containers, and start many of them together |
| Encryption | Scrambling data so only someone with the right key can read it |
| Environment | A separate copy of the system: test, or production (live) |
| Gateway | The single entry point that checks and routes every request |
| Grafana | The dashboard tool showing graphs of system health |
| Hash | A one-way scramble of a password; it can be checked but not reversed |
| Health check | An automatic "are you OK?" question each part answers regularly |
| Helm chart | A ready-made recipe for installing the system on Kubernetes |
| HSTS | A setting telling browsers to always use the secure (HTTPS) version |
| HTTPS | The encrypted version of web traffic, shown by the padlock |
| Kubernetes | A system that runs containers across many machines and keeps them healthy |
| Logs | Each program's running diary of what it did |
| Mailpit | A test inbox that catches emails so none reach real people during testing |
| Metrics | Measurements over time (counts, speeds, error rates) |
| Microservices | Building an application from several small, independent programs |
| Migration | A numbered, reviewed script that changes a database's structure |
| Mock (payment) | A fake payment used for testing; no money moves |
| Monolith | Building an application as one single program |
| MySQL / PostgreSQL | Two popular free databases |
| OAuth | The standard "Log in with X / Connect with X" permission flow (used for Strava) |
| Outbox / queue | A to-do list stored in the database, so work survives restarts |
| QR code | A square barcode a phone camera can read; ours holds the ticket code |
| Rate limit | A cap on how many requests are allowed in a time window |
| Razorpay | The payment company that processes card and UPI payments for us |
| Reconciliation | Periodically double-checking our payment records against Razorpay's |
| Refresh / renewal pass | A longer-lived login pass used only to get new access badges |
| Repository (repo) | The place where the code and its history are stored (GitHub) |
| Service | One independent program with one job (a "department") |
| Strava | A popular app for recording runs and rides |
| Trace | The path of one request through all the departments, with timings |
| UUID | A long random identifier that can't be guessed |
| Webhook | A doorbell: another company's system calls ours to say something happened |
| ZeptoMail | Zoho's service for sending transactional emails |
