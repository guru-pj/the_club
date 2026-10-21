# Run and test The Club: browser, Android and iPhone

This guide gets The Club running on your own computer and walks through testing every feature in a browser, in the
Android app and in the iPhone app. It is for developers and testers. For deploying a real server see
[`PRODUCTION.md`](PRODUCTION.md); for publishing the apps see [`NATIVE_APPS.md`](NATIVE_APPS.md).

**Contents**

1. [What you'll have at the end](#1-what-youll-have-at-the-end)
2. [Already have a copy? Update it](#2-already-have-a-copy-update-it)
3. [First-time setup](#3-first-time-setup)
4. [Test in a browser](#4-test-in-a-browser)
5. [Test the Android app](#5-test-the-android-app)
6. [Test the iPhone app](#6-test-the-iphone-app)
7. [Feature checklist](#7-feature-checklist)
8. [Troubleshooting](#8-troubleshooting)

---

## 1. What you'll have at the end

The whole platform runs in Docker on your computer:

| Address | What it is |
|---|---|
| http://localhost:3000 | **The Club** website. It also serves the API (`/api`) and uploaded pictures (`/media`) to the apps |
| http://localhost:8025 | Mailpit: every email the app sends (nothing leaves your computer) |
| http://localhost:8080 | The API gateway (scripts use it; the apps use port 3000) |

Local defaults: payments run in **test mode** (no real money), email verification is **off**, and emails are caught by
Mailpit.

**Accounts to sign in with**

| Who | Email | Password |
|---|---|---|
| Admin | `ADMIN_EMAIL` in `.env` (default `admin@theclub.local`) | `cat secrets/admin-password` |
| Sample club owners and members | listed when you run `./scripts/seed-demo.sh` (step 3.6) | `Demo-pass-2026!` |

---

## 2. Already have a copy? Update it

Newer versions need things an older copy doesn't have yet: new secrets for image storage, new database columns, new
app plugins. Do these steps every time you pull a new version.

> **Typical error from an old copy:**
> `secret file the_club_seaweedfs-s3.json does not exist` or
> `bind source path does not exist: …/secrets/seaweedfs-s3.json`.
> Step 2.3 fixes it.

**2.1 Back up the databases** (if you have data you care about):
```bash
cd ~/the_club
./scripts/backup.sh
```

**2.2 Get the latest code:**
```bash
git pull
```

**2.3 Add any new secrets.** The script only creates files that are missing; your existing keys, passwords and the
admin password stay as they are:
```bash
./scripts/generate-secrets.sh
ls secrets/seaweedfs-s3.json secrets/media-s3-secret-key     # both must exist now
```

**2.4 Check `.env` for new settings.** Your `.env` is a copy of `enviroinment_variables.env` from when you set up. Compare
the two and add anything new:
```bash
diff <(grep -oE '^[A-Z_]+=' enviroinment_variables.env | sort) <(grep -oE '^[A-Z_]+=' .env | sort)
```
Lines starting with `<` exist in the template but not in your `.env`. Most have working defaults, so you only need to
copy them if you want to change them. One you do need for the Android app against a local server: `CORS_ALLOWED_ORIGINS`
must contain `http://localhost`:
```
CORS_ALLOWED_ORIGINS=http://localhost:3000,http://localhost:5173,http://localhost,https://localhost,capacitor://localhost,tauri://localhost
```

**2.5 Rebuild and restart.** The service images are built from the jars Maven produces, so build those first:
```bash
mvn clean install -DskipTests
docker compose up -d --build
```
Database changes (migrations) run by themselves when each service starts.

**2.6 Wait until everything is healthy, then check:**
```bash
docker compose ps                     # repeat until nothing says "starting" (about a minute)
./scripts/smoke-test.sh               # must end with ALL PASSED
```

**2.7 Rebuild the apps** if you test on a phone: Android in [step 5](#5-test-the-android-app), iPhone in
[step 6](#6-test-the-iphone-app). An app built from older code doesn't have the newer features.

---

## 3. First-time setup

### 3.1 Install the tools

| Tool | Version | Check with |
|---|---|---|
| Git | any | `git --version` |
| Java (JDK, not just a JRE) | 21 | `java -version` and `javac -version` |
| Maven | 3.9+ | `mvn -v` (must show Java 21) |
| Docker with Compose | Docker Desktop on macOS/Windows, Docker Engine on Linux | `docker compose version` |
| Node.js | 22 (only for the apps and the browser test script) | `node -v` |
| `openssl` and `jq` | any | `openssl version && jq --version` |

On macOS: `brew install git openjdk@21 maven node@22 jq` and install Docker Desktop. Start Docker Desktop before going on.
On Linux, add yourself to the `docker` group (`sudo usermod -aG docker $USER`), then log out and back in; `docker run --rm hello-world`
must work **without** `sudo`. Never run `mvn` or the scripts with `sudo`.

### 3.2 Get the code

```bash
cd ~
git clone https://github.com/guru-pj/the_club.git
cd the_club
```
The repository is private: you need to be added as a collaborator, and git asks for your GitHub username and a
personal access token (github.com → Settings → Developer settings → Personal access tokens → *Fine-grained*, with read
access to *Contents*). Never paste a token into a chat or a file in the repository.

### 3.3 Configure

```bash
cp enviroinment_variables.env .env
./scripts/generate-secrets.sh
```
The script creates `secrets/` (never committed) and prints the admin password once. Read it again any time with
`cat secrets/admin-password`.

Check that the ports are free:
```bash
bash -c 'for p in 3000 3306 5432 8025 8080 8081 8082 8083 8084 8085 8086 8087 8761 8888; do (echo > /dev/tcp/127.0.0.1/$p) 2>/dev/null && echo "  port $p is already in use"; done; echo "port check done"'
```
A local MySQL (3306) or PostgreSQL (5432) is the usual clash. Move ours: set `MYSQL_PORT=3307` and `POSTGRES_PORT=5433`
in `.env`.

### 3.4 Build and start

```bash
mvn clean install -DskipTests
docker compose up -d --build
```
The first run takes 5–15 minutes (downloads); later runs about a minute. Wait for healthy:
```bash
docker compose ps
```
Every service should say `Up … (healthy)`; the two `db-backup-*` containers just say `Up`.

### 3.5 Check it works

```bash
./scripts/smoke-test.sh
```
It signs up users, takes a club through verification, creates events, pays, checks in, posts, chats and more, and ends
with `ALL PASSED`. If the first lines fail with **503**, the services are still finding each other: wait 30 seconds and
run it again.

### 3.6 Add sample data (recommended)

```bash
./scripts/seed-demo.sh
```
This adds three verified clubs in Chennai with full profiles, published, paid and draft events, members who follow,
subscribe, register, post, comment, review and chat, and one club application waiting for the admin. It prints the
accounts:

| Email | Role | Good for testing |
|---|---|---|
| `anika@demo.theclub.local` | Owner of **Marina Morning Runners** | Owner dashboard, events, check-in, chat moderation |
| `vikram@demo.theclub.local` | Owner of **Coastal Cycling Collective**, plus an application for **Adyar Triathletes** waiting for review | A second owner; applications |
| `zara@demo.theclub.local` | Owner of **Anna Nagar Strength Lab** | A paid event |
| `meera@demo.theclub.local` | Subscriber of Marina Morning Runners | Member side: feed, chat, tickets |
| `arjun@demo.theclub.local` | Subscriber of two clubs, paid for a ride | Paid tickets |
| `fatima@demo.theclub.local` | Follows Marina Morning Runners | A follower (can comment, can't chat) |

All use the password `Demo-pass-2026!`. Running the script again only prints the list.

---

## 4. Test in a browser

### 4.1 On your computer

Open **http://localhost:3000** and sign in. Chrome, Edge, Firefox and Safari all work.

To test two people at once (chat, likes, an owner and a member), use a normal window for one account and a **private /
incognito window** for the other. Each window keeps its own sign-in.

Emails (registration confirmations, tickets, password resets) appear at **http://localhost:8025**.

### 4.2 On a phone's browser (same Wi-Fi)

Find your computer's address on the network:
- macOS: **System Settings** → **Wi-Fi** → **Details** → *IP address*, or `ipconfig getifaddr en0`
- Linux: `hostname -I`

On the phone, open `http://<that-address>:3000`. Everything works except **Use my current location**: browsers only
share location with secure (`https://`) sites or `localhost`. Type a city instead, or test location in the apps.

### 4.3 Browser test script (optional)

An automated browser run of the whole journey (sign-up to chat, about 4 minutes, needs Chrome):
```bash
cd web-app && npm ci
APP_URL=http://localhost:3000 ADMIN_EMAIL=admin@theclub.local ADMIN_PASSWORD="$(cat ../secrets/admin-password)" npm run e2e
```
It ends with `FLOW COMPLETE` and takes screenshots in `web-app/e2e/screenshots/`. On macOS, Chrome is found at its usual
place; elsewhere set `CHROME_PATH`.

---

## 5. Test the Android app

The Android app is the same UI packaged with Capacitor. You can run it in the **Android emulator** (Linux, macOS or
Windows) or on a **real phone** over USB.

### 5.1 Tools

Install **Android Studio** (it brings the Android SDK and the emulator), plus JDK 21 and Node.js 22. In Android Studio:
**More Actions** → **Virtual Device Manager** → **Create device** (e.g. *Pixel 8*, a recent system image) → **Finish**.

Tell the build where the SDK is (add it to your shell profile to keep it):
```bash
export ANDROID_HOME="$HOME/Library/Android/sdk"     # macOS
export ANDROID_HOME="$HOME/Android/Sdk"             # Linux
```

### 5.2 Point the app at your computer and build it

The app needs the address of the website on your computer (port **3000**, which serves `/api` and `/media`):

| Where the app runs | Address |
|---|---|
| Android emulator | `http://10.0.2.2:3000` (the emulator's name for your computer) |
| Real phone on the same Wi-Fi | `http://<your-computer-ip>:3000` (see 4.2) |

```bash
cd ~/the_club/web-app
npm ci
VITE_API_BASE_URL=http://10.0.2.2:3000 npm run android:sync
cd android && ./gradlew assembleDebug
```
The app is `web-app/android/app/build/outputs/apk/debug/app-debug.apk`.

Re-run `npm run android:sync` and `./gradlew assembleDebug` (with the same `VITE_API_BASE_URL`) after every `git pull`.

### 5.3 Install and open it

**Emulator:** start it from Android Studio's Device Manager (▶), then:
```bash
"$ANDROID_HOME/platform-tools/adb" install -r app/build/outputs/apk/debug/app-debug.apk
```
**Real phone:** turn on **Developer options** (Settings → About phone → tap *Build number* 7 times) and **USB debugging**,
connect the cable, accept the prompt on the phone, and run the same `adb install` command.

Or open the project in Android Studio (`npm run android:open`) and press **Run ▶**.

Open **The Club** from the app drawer and sign in with one of the accounts above.

### 5.4 What's different in the app

- **Sign-in** is kept by the app itself; closing and reopening it keeps you signed in.
- **Location:** "Use my current location" shows Android's permission dialog (choose *While using the app*). In the
  emulator, set a position under **⋮ Extended controls** → **Location**.
- **Photos:** the photo buttons open Android's picker (gallery, camera, files).
- **Share** opens Android's share sheet (Messages, Gmail, Drive, Copy, …).
- **Download** on a proof document opens the share sheet to save it (e.g. to Drive) or open it.
- **Chat** reconnects by itself when you come back to the app, and catches up on messages sent meanwhile.
- **Check-in scanner** asks for the camera the first time.
- With a plain `http://` local server the app runs on `http://localhost` inside the phone (so pictures load). That's why
  `CORS_ALLOWED_ORIGINS` needs `http://localhost` (step 2.4). A real server uses `https://` and needs nothing special.

---

## 6. Test the iPhone app

The iPhone app needs a **Mac with Xcode**. You can run it in the **iOS Simulator** (no iPhone needed) or on your own
**iPhone** over a cable. A free Apple ID is enough; an app installed this way works for 7 days, then press Run again.

### 6.1 Tools

- **Xcode 16 or later** from the Mac App Store. Open it once and let it install its components.
- **Node.js 22**: `brew install node@22`.
- For a real iPhone: iOS 15 or later, a cable, and your Apple ID.

### 6.2 Point the app at the Mac and build it

The app needs the address of the website on the Mac (port **3000**, which serves `/api` and `/media`; port 8080 would
leave pictures blank):

| Where the app runs | Address |
|---|---|
| iOS Simulator on the same Mac | `http://localhost:3000` |
| Real iPhone on the same Wi-Fi | `http://<your-mac-name>.local:3000`. Find the name under **System Settings** → **General** → **Sharing** → *Local hostname* (e.g. `GuruPjs-MacBook-Air.local`) |

iOS only allows plain `http://` to local addresses like these; any other server must be `https://`.

```bash
cd ~/the_club/web-app
npm ci
echo 'VITE_API_BASE_URL=http://GuruPjs-MacBook-Air.local:3000' > .env.native.local   # your Mac's name, or http://localhost:3000 for the Simulator
npm run ios:sync
npm run ios:open
```
`ios:open` opens the project in Xcode. The first build downloads the Swift packages (Capacitor, Share, Filesystem), which
takes a few minutes.

Re-run `npm run ios:sync` after every `git pull` and after changing `.env.native.local`.

### 6.3 Run in the Simulator

In Xcode's toolbar, choose a simulator (e.g. *iPhone 16*) and press **Run ▶**. The app opens in the Simulator.
To share a location: **Simulator** menu → **Features** → **Location** → *Custom Location…* (Chennai is about 13.05, 80.28).

### 6.4 Run on your iPhone

1. **Add your Apple ID to Xcode:** **Xcode** → **Settings…** → **Accounts** → **+** → **Apple ID**.
2. **Signing:** in the left sidebar click **App** (blue icon) → target **App** → **Signing & Capabilities** → tick
   **Automatically manage signing** → **Team**: your name (*Personal Team*). If Xcode says the bundle identifier is taken,
   change **Bundle Identifier** to something unique, e.g. `com.yourname.theclub`.
3. **Connect the iPhone** with the cable, unlock it and tap **Trust This Computer**.
4. **Developer Mode:** on the iPhone, **Settings** → **Privacy & Security** → **Developer Mode** → on, and restart when asked.
   (It appears after the iPhone has been connected to Xcode once.)
5. In Xcode's toolbar choose your iPhone and press **Run ▶**.
6. The first time, the iPhone says *Untrusted Developer*: **Settings** → **General** → **VPN & Device Management** → your
   Apple ID → **Trust**. Then open **The Club**.
7. **Local network:** iOS asks *"The Club" would like to find devices on your local network*: tap **Allow**. Without it
   the app can't reach the Mac (*Cannot reach the server*).

If the iPhone can't reach the Mac, check: both on the same Wi-Fi (not a guest network); the website opens in Safari on the
iPhone at `http://<your-mac-name>.local:3000`; macOS **System Settings** → **Network** → **Firewall** allows incoming
connections for Docker.

### 6.5 What's different in the app

- **Location**, **camera** (photos and the check-in scanner) and **saving a photo** each ask for permission the first
  time. Change them later under **Settings** → **The Club**.
- **Share** opens the iOS share sheet (Messages, Mail, Copy, AirDrop, …).
- **Download** on a proof document opens the share sheet: choose **Save to Files** or open it in another app.
- **Chat** reconnects by itself when you come back to the app.

---

## 7. Feature checklist

Run through these in the browser, the Android app and the iPhone app. "Sample" accounts come from step 3.6. Expected
results are what you should see; anything else is a bug worth reporting (with a screenshot).

### 7.1 Accounts and profile

| # | Do this | Expected |
|---|---|---|
| 1 | **Create an account** (any email; verification is off locally) | You land on profile setup |
| 2 | Add a photo, tap **Use my current location**, write a bio, **Finish** | "Your approximate location is saved"; you land on Clubs |
| 3 | Sign out and in again; close and reopen the app | Still works; in the app you stay signed in |
| 4 | **Forgot password?** on the login screen | The reset email appears in Mailpit (http://localhost:8025) |

### 7.2 Discover and join (as `meera` or a new account)

| # | Do this | Expected |
|---|---|---|
| 5 | **Clubs** | *Near you* lists the sample clubs with distances; also *Popular* and upcoming events |
| 6 | Open **Marina Morning Runners** | Cover photo, logo, **Verified** badge, rating; tabs Feed, About, Chat, Events, … |
| 7 | **About** tab | Description, rules, meeting point with map link, contact, website and social links |
| 8 | **Follow**, then **Subscribe** | Badges change; subscriber count goes up |
| 9 | **Events** in the bottom bar | Upcoming events with "Registration open" / "Opens soon" / "Full" |

### 7.3 Events, payment and tickets

| # | Do this | Expected |
|---|---|---|
| 10 | Open a free event → **Register** | "Registered"; a ticket under **Tickets** with a QR code; a confirmation email in Mailpit |
| 11 | Open **ECR 100 km Brevet** (paid) → **Register** → **Pay ₹299** → **Pay ₹299** in the test payment window | Payment succeeds (test mode, no real money); ticket issued |
| 12 | Subscriber-only event (**Track Tuesday**) as `fatima` (a follower) | Can't register: "subscribers only" |

### 7.4 Feed: posts, photos, likes, comments, sharing

| # | Do this | Expected |
|---|---|---|
| 13 | As a subscriber, in the club's **Feed**: title, message, **Add photos**, **Post** | The post appears with your photo |
| 14 | **Like** a post | Heart fills; count goes up; tapping again removes it |
| 15 | **Comment** → write → **Comment** | Your comment appears; the post's author gets a notification |
| 16 | As `fatima` (follower), comment | Works. As someone who neither follows nor subscribes: "Follow or subscribe to the club to comment" |
| 17 | **Share** on a post | Browser: "Link copied" (or the share menu on Safari/phones). Apps: the system share sheet |
| 18 | Open the shared link in a private window (signed out) | A preview of the post with **Log in** / **Create an account** |
| 19 | As an owner (`vikram`), **Reshare** a Marina Morning Runners post into your club | It appears in Coastal Cycling Collective's feed, pointing back to the original |
| 20 | Delete your own post or comment (**⋯** / bin icon) | Gone for everyone |

### 7.5 Chat and messages

| # | Do this | Expected |
|---|---|---|
| 21 | As `meera`, club → **Chat** → send a message | It appears at once |
| 22 | At the same time as `anika` in another window or device, open the same chat | Messages from each side appear live, without refreshing |
| 23 | As `fatima` (follower), open **Chat** | "The club chat is for subscribers" |
| 24 | **Members** tab → **Message** next to someone | A direct conversation opens under **Messages** (speech-bubble icon) |
| 25 | Send a photo with the 🖼 button | The photo shows in the conversation |
| 26 | The other person's **Messages** icon | Shows an unread count; opening the conversation clears it |
| 27 | Message **⋯** → **Report** | "Thanks, we'll review it"; the admin sees it under **Reported messages** |
| 28 | In a direct conversation, **Block** | The other person can no longer message you; **Unblock** undoes it |
| 29 | As the owner (`anika`), message **⋯** → **Delete** or **Mute in this chat** | Deleted shows "Message deleted" for everyone; a muted member can read but not write |
| 30 | Apps only: leave the app for a minute, send a message from another device, come back | The message is there and new ones arrive live again |

### 7.6 Club owner journey

| # | Do this | Expected |
|---|---|---|
| 31 | **Create account** → choose **I run a club** | After setup you land on the club application |
| 32 | Fill in the club (name, category, city, contact email or phone, address, links) → **Submit application** | Status **Pending review** |
| 33 | **Upload a document** (a PDF or photo) → **Download** it | It downloads (browser) or opens in the share sheet (apps) |
| 34 | **Owner dashboard** before approval | "No verified clubs yet" |
| 35 | As admin: avatar menu → **Owner applications** → the application → **Download** the document → **Ask for changes** | The applicant sees **Changes required** and your message |
| 36 | As the applicant: **Update details** → **Save changes** → **Resubmit for review** | Back to **Pending review** |
| 37 | As admin: **Approve** → check the official name → **Approve and create the club** | The applicant gets a notification |
| 38 | As the applicant: sign in again | You land on the **Owner dashboard** with your verified club and a profile checklist |
| 39 | **Complete the profile**: logo, cover photo, description, location, rules, contact | The checklist fills; the club page shows everything; the name can't be changed |
| 40 | **Create event** → fill in → **Save and review** | A **Draft** only you can see (members don't find it) |
| 41 | **Publish event** | Published; followers and subscribers get a notification; members can register |
| 42 | Dashboard on the event's day | **Today** with **Scan tickets**; registrations and check-ins counted |
| 43 | **Check-in scanner** → scan a member's QR (or type the code) | "Checked in"; scanning it again: "already checked in"; a ticket for another event is refused |
| 44 | After the event: **Write a recap** | The feed opens with *Event recap* and the event selected |
| 45 | **Mark as completed** (once it has started) | It moves to past events; registrations and numbers stay |
| 46 | Try a second club: **Apply for another club** | A separate application; owning one club doesn't approve another |

### 7.7 Admin

| # | Do this | Expected |
|---|---|---|
| 47 | Avatar menu → **Owner applications** | `vikram`'s **Adyar Triathletes** application waiting |
| 48 | Avatar menu → **Reported messages** | Reports from 27; **Remove message** or **Keep message** |

---

## 8. Troubleshooting

| Problem | Cause and fix |
|---|---|
| `secret file the_club_seaweedfs-s3.json does not exist` / `bind source path does not exist: …/secrets/seaweedfs-s3.json` | The copy was set up before image uploads existed. Run `./scripts/generate-secrets.sh` (it only adds what's missing), then `docker compose up -d --build`. See [step 2](#2-already-have-a-copy-update-it). |
| `permission denied … docker.sock` (Linux) | You haven't logged out and in since joining the `docker` group (or run `newgrp docker`). Don't use `sudo`. |
| `Cannot connect to the Docker daemon` (macOS) | Start Docker Desktop and wait until it says *running*. |
| A service shows `Restarting` or `unhealthy` | `docker compose logs <service> --tail 100`. "Port already allocated": see the port check in 3.3. Database errors after a pull: make sure you ran `mvn clean install -DskipTests` before `--build`. |
| The smoke test fails with **503** | The services are still registering. Wait 30 seconds and run it again. |
| The website shows old screens after a pull | `docker compose up -d --build web-app`, then reload the page (Cmd/Ctrl+Shift+R). |
| Pictures don't show (website) | `docker compose ps seaweedfs` must be healthy; check step 2.3. |
| App: **Cannot reach the server** | The address in the app is wrong or unreachable. Emulator: `http://10.0.2.2:3000`; real devices: your computer's address or `<mac-name>.local` with port **3000**. Open that address in the phone's browser first: if the website doesn't open there, it's the network (same Wi-Fi, firewall). Rebuild the app after changing the address. |
| App: sign-in fails with **403**, or nothing loads | The server doesn't allow the app's origin. `CORS_ALLOWED_ORIGINS` in `.env` must include `http://localhost` (Android with a local server), `https://localhost` (Android with an HTTPS server) and `capacitor://localhost` (iPhone). Then `docker compose up -d gateway-service`. |
| App: pictures are blank | The app points at port 8080 instead of 3000, or (Android) the app was built before this was fixed: rebuild with `VITE_API_BASE_URL=…:3000`. |
| App: no new features after `git pull` | Rebuild the app (5.2 or 6.2); it carries its own copy of the screens. |
| Android build: `SDK location not found` | Set `ANDROID_HOME` (5.1). |
| iPhone: *Untrusted Developer* | **Settings** → **General** → **VPN & Device Management** → your Apple ID → **Trust**. |
| iPhone: the app stopped opening after a week | Free Apple ID installs last 7 days. Connect the iPhone and press **Run ▶** in Xcode again. |
| iPhone: *Cannot reach the server* only on the iPhone, Simulator works | Allow **Local Network** for The Club (**Settings** → **Privacy & Security** → **Local Network**), and use `<mac-name>.local`, not `localhost`. |
| Xcode: bundle identifier *not available* | Change **Bundle Identifier** to something unique (6.4, step 2). |
| "Use my current location" does nothing in a phone browser | Browsers only share location with `https://` sites. Use the app, or type your city. |
| Chat messages only appear after a refresh | Something between the app and the server buffers the live connection. Locally this shouldn't happen; check `docker compose logs web-app gateway-service`. Behind your own proxy see [`PRODUCTION.md`](PRODUCTION.md) §4. |
| Login says the account is locked | 5 wrong passwords lock it for 15 minutes. Wait, or use **Forgot password?**. |

More detail: [`README.md`](../README.md) (setup and the full UI tour), [`NATIVE_APPS.md`](NATIVE_APPS.md) (building,
signing and publishing the apps), [`SERVICE_CONTRACTS.md`](SERVICE_CONTRACTS.md) (every API endpoint).
