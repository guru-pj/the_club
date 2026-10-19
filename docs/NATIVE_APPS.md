# Android, iOS and macOS apps

The Android and iOS apps (Capacitor) and the macOS app (Tauri) all wrap the React web app in `web-app/`. They show the same screens and features as the website. They are built from the same source with `vite build --mode native` and installed on the device. They call the gateway at an absolute URL that is fixed at build time.

| | Android | iOS (iPhone and iPad) | macOS |
|---|---|---|---|
| Wrapper | Capacitor 8 (`web-app/android/`) | Capacitor 8 (`web-app/ios/`) | Tauri 2 (`web-app/src-tauri/`) |
| App ID | `com.theclub.app` | `com.theclub.app` | `com.theclub.app` |
| Origin seen by the gateway | `https://localhost` | `capacitor://localhost` | `tauri://localhost` |
| Minimum OS | Android 7.0 | iOS 15 | macOS 13 Ventura, Apple Silicon and Intel |
| Members get it from | GitHub Release: `TheClub-android.apk` | **TestFlight** | GitHub Release: `TheClub-macos.dmg` |

iPhones only install apps signed by Apple, so the iOS app can't be downloaded as a file. It goes to TestFlight, Apple's beta-distribution app, which needs an Apple Developer Program membership (Part 1, step 5).

**Contents**
- [Part 1 — Publish the apps (maintainers)](#part-1--publish-the-apps-maintainers): one-time setup, releasing a version, TestFlight, test builds
- [Part 2 — Download and install (everyone)](#part-2--download-and-install-everyone): [Android](#installing-on-android), [iPhone and iPad](#installing-on-iphone-and-ipad), [macOS](#installing-on-macos), [updating](#updating-to-a-new-version), [troubleshooting](#troubleshooting)
- [Part 3 — Build on your own machine (developers)](#part-3--build-on-your-own-machine-developers)
- [How the apps differ from the website](#how-the-apps-differ-from-the-website)

---

## Part 1 — Publish the apps (maintainers)

The `.github/workflows/apps.yml` workflow (**Native apps** in the Actions tab) builds both apps on GitHub's servers. You need no Android SDK and no Mac.

| What triggers it | What you get |
|---|---|
| A tag `app-v1.2.3` | A **GitHub Release** with `TheClub-android.apk`, `TheClub-macos.dmg` and `SHA256SUMS.txt`, plus the iOS build **uploaded to TestFlight** (once step 5 is done). This is what members install. |
| A push to `main` touching `web-app/`, or **Run workflow** | **Test builds** as workflow artifacts, kept 14 days and visible only to people with repo access. iOS is a Simulator build for developers. |

### Step 1 — Point the apps at your server (once)

The apps can't discover your server; its address is built into them.

1. On GitHub, open the repository → **Settings** → **Secrets and variables** → **Actions** → **Variables** tab → **New repository variable**.
2. Add `APP_API_BASE_URL`: your public HTTPS address, the same one members open in a browser, e.g. `https://club.example.com`. No trailing slash.
3. Optionally, add `APP_WEB_URL` with the same address. The apps show it where members must use the website (connecting Strava).

Without `APP_API_BASE_URL`, every job in the workflow is skipped.

### Step 2 — Allow the apps on your server (once)

On the server, add the three app origins to `CORS_ALLOWED_ORIGINS` in `.env`, keeping your website's address:
```bash
CORS_ALLOWED_ORIGINS=https://club.example.com,https://localhost,capacitor://localhost,tauri://localhost
```
Then apply it:
```bash
docker compose -f docker-compose.yml up -d gateway-service
```
On Kubernetes, set `config.corsAllowedOrigins` in your values file instead and run `helm upgrade`.

Without this step the apps install fine, but login fails with **Cannot reach the server**.

### Step 3 — Create the Android signing key (once)

Android installs an update only if it is signed with the **same key** as the installed app. Create one permanent key and give it to GitHub. You need JDK 21 for `keytool` (any machine set up from the README has it):

```bash
keytool -genkeypair -v -keystore the-club-release.keystore -alias theclub \
  -keyalg RSA -keysize 4096 -validity 10000
```
Choose a strong password when asked. The name and organisation questions can be answered freely. Then encode the key for GitHub:
```bash
# Linux
base64 -w0 the-club-release.keystore > keystore.b64
# macOS
base64 -i the-club-release.keystore -o keystore.b64
```

In **Settings** → **Secrets and variables** → **Actions** → **Secrets** tab, add four repository secrets:

| Secret | Value |
|---|---|
| `ANDROID_KEYSTORE_BASE64` | the entire contents of `keystore.b64` |
| `ANDROID_KEYSTORE_PASSWORD` | the password you chose |
| `ANDROID_KEY_ALIAS` | `theclub` |
| `ANDROID_KEY_PASSWORD` | the same password, unless you gave the key its own |

Then delete `keystore.b64`. Keep `the-club-release.keystore` and its password in a password manager or another safe place outside the repository. **If the key is lost, no future version can update the installed app**: every member would have to uninstall it, losing their sign-in, and install it again. Never commit the keystore.

Release builds fail with a clear error if these secrets are missing. Test builds fall back to a temporary debug key.

### Step 4 — Publish a release

1. Make sure `main` contains what you want to ship and that its **CI** run is green.
2. Pick the next version, `MAJOR.MINOR.PATCH`, higher than the last one under **Releases**, and tag it:
   ```bash
   git checkout main && git pull
   git tag app-v1.0.0
   git push origin app-v1.0.0
   ```
   You can also go to **Releases** → **Draft a new release** → **Choose a tag** → type `app-v1.0.0` → **Create new tag on publish**. Then cancel without publishing: the workflow creates the release itself. Using the terminal is simpler.
3. Open **Actions** → **Native apps** and watch the run. It takes about 15–25 minutes; the macOS build is the slowest.
4. When it finishes, the release appears under **Releases** with both files and download links.

Share these links with members. They always point to the newest release:
```
https://github.com/guru-pj/the_club/releases/latest/download/TheClub-android.apk
https://github.com/guru-pj/the_club/releases/latest/download/TheClub-macos.dmg
```
For one specific version, replace `latest/download` with `download/app-v1.0.0`.

> ⚠️ **The repository is private, so its releases are private too.** These links work only for people signed in to GitHub with access to the repository; everyone else sees 404. To let members download without GitHub access, choose one of these:
> - Make the repository public. Its code becomes visible to everyone.
> - Keep a separate public repository just for releases.
> - Upload the two files to the website, for example under `/downloads`, and link to them from there.

### Step 5 — Set up TestFlight for iPhone and iPad (once, optional)

Skip this step if you don't need the iOS app. Releases still publish Android and macOS, and the workflow notes that the iOS upload was skipped.

You need an **Apple Developer Program** membership for an individual or organisation (yearly fee) and the Account Holder or an Admin to do the following.

1. **Create the app record.** In [App Store Connect](https://appstoreconnect.apple.com) → **Apps** → **+** → **New App**:
   - Platform: **iOS**; Name: **The Club** (must be unique on the App Store; add your club's name if it's taken)
   - Bundle ID: choose **com.theclub.app**. If it isn't listed, register it first at [developer.apple.com](https://developer.apple.com/account/resources/identifiers/list) → **Identifiers** → **+** → **App IDs** → **App**, with explicit Bundle ID `com.theclub.app`.
   - SKU: anything, e.g. `the-club-ios`
2. **Create an API key** so GitHub can sign and upload. Go to **Users and Access** → **Integrations** → **App Store Connect API** → **Team Keys** → **+**. Name it `GitHub Actions` and give it access **Admin**; signing certificates can only be created with Admin. Click **Generate**, then:
   - note the **Issuer ID** shown above the list and the key's **Key ID**;
   - click **Download** next to the key. The `.p8` file can be downloaded **only once**.
3. **Find your Team ID** at [developer.apple.com/account](https://developer.apple.com/account) → **Membership details** → **Team ID**. It's 10 characters.
4. Encode the key:
   ```bash
   # Linux
   base64 -w0 AuthKey_XXXXXXXXXX.p8 > key.b64
   # macOS
   base64 -i AuthKey_XXXXXXXXXX.p8 -o key.b64
   ```
5. Add four repository **secrets** (Settings → Secrets and variables → Actions → **Secrets**):

   | Secret | Value |
   |---|---|
   | `APPLE_TEAM_ID` | your Team ID |
   | `APP_STORE_CONNECT_KEY_ID` | the Key ID |
   | `APP_STORE_CONNECT_ISSUER_ID` | the Issuer ID |
   | `APP_STORE_CONNECT_KEY_P8_BASE64` | the entire contents of `key.b64` |

   Delete `key.b64`, and keep the `.p8` file somewhere safe outside the repository. If it's lost, revoke the key in App Store Connect and create a new one.
6. Push a release tag (step 4). After the run, the build shows up in App Store Connect → **The Club** → **TestFlight**, usually 10–30 minutes later once Apple has processed it.

**Inviting testers** (App Store Connect → The Club → **TestFlight**):
- **Internal testing:** up to 100 people from your App Store Connect team. Add a group under **Internal Testing**, add the people, and they get each new build automatically, with no review.
- **External testing (club members):** up to 10,000 people. Create a group under **External Testing**, add the build and fill in **Test Information** (a contact email and what to test). The first build of each version goes through a short **Beta App Review**, typically about a day. Then either add members by email or switch on **Public Link** and share that link. Later builds of the same version usually need no review.

TestFlight builds expire after **90 days**, so release at least that often, or move to the App Store (Part 3).

### Test builds (before releasing)

Every push to `main` that changes `web-app/` produces test builds. To build on demand, go to **Actions** → **Native apps** → **Run workflow**. To try another server, enter a different gateway URL in the form.

To download them:
1. Open **Actions** → **Native apps** → the run you want. It must show a green tick.
2. Scroll to **Artifacts** at the bottom and click `the-club-android` or `the-club-macos`.
3. GitHub downloads a **`.zip`**. Unzip it to get `TheClub-android.apk` or `TheClub-macos.dmg`, then install it as in Part 2.

`the-club-ios-simulator` holds `TheClub-ios-simulator.zip`, a build for the iOS Simulator in Xcode; it doesn't install on a real iPhone. On a Mac with Xcode, unzip it, start a simulator, and drag `App.app` onto it. Or run:
```bash
open -a Simulator
xcrun simctl install booted App.app
xcrun simctl launch booted com.theclub.app
```

Test builds are for checking a change, not for members:
- Without the keystore secrets they are debug-signed, so they can't update a release install or be updated by one. Uninstall one before installing the other.
- They expire after 14 days.

---

## Part 2 — Download and install (everyone)

### Installing on Android

Works on Android 7.0 and later, on phones and tablets.

1. **Download.** On the phone, open the Android link from your club (`…/TheClub-android.apk`) in Chrome. If GitHub asks you to sign in, do so. Chrome may warn that *this type of file can harm your device*: tap **Download anyway**.
2. **Open it.** When the download finishes, tap **Open** in the bar at the bottom. You can also open **Files** → **Downloads** → `TheClub-android.apk`.
3. **Allow the install.** The first time, Android blocks apps that don't come from the Play Store:
   - **Android 8 and later:** tap **Settings** in the message → switch on **Allow from this source** for Chrome (or your Files app) → press back.
   - **Android 7:** **Settings** → **Security** → switch on **Unknown sources** → go back to the download.
4. Tap **Install**. If **Play Protect** says *app not recognised* or *unsafe app blocked*, tap **More details** → **Install anyway**. This happens because the app isn't on the Play Store, not because something is wrong.
5. Tap **Open**, or find **The Club** in your app drawer, and sign in with your usual email and password.
6. **Camera (club owners).** The first time you scan tickets at check-in, Android asks to use the camera: tap **While using the app**. If you tapped *Don't allow*, go to **Settings** → **Apps** → **The Club** → **Permissions** → **Camera** → **Allow only while using the app**. You can always type the ticket code instead.

You can switch **Allow from this source** off again after installing; Android remembers the app.

From a computer instead, with USB debugging switched on:
```bash
adb install TheClub-android.apk
```

### Installing on iPhone and iPad

Works on iOS and iPadOS 15 and later. The app comes through Apple's **TestFlight** app, not as a download link.

1. **Install TestFlight.** On the iPhone, open the **App Store**, search for **TestFlight** (by Apple) and tap **Get**.
2. **Accept the invitation.** Either:
   - **Email invite:** open the email *"You're invited to test The Club"* on the iPhone and tap **View in TestFlight**. Then tap **Accept** in TestFlight.
   - **Public link** from your club: open it on the iPhone and tap **Accept**.
3. Tap **Install**. **The Club** appears on your Home Screen.
4. Open it and sign in with your usual email and password. The first time, TestFlight shows a short "What to Test" screen; tap **Next**/**Start Testing**.
5. **Camera (club owners).** The first time you scan tickets, iOS asks to use the camera: tap **Allow**. To change it later: **Settings** → **The Club** → **Camera**. You can always type the ticket code instead.

**Updates:** TestFlight notifies you of new builds. Open **TestFlight** → **The Club** → **Update**. To update automatically, switch on **Automatic Updates** on the app's page in TestFlight.

**Expired build:** a TestFlight build stops opening after 90 days. Install the newest build from TestFlight.

### Installing on macOS

Works on macOS 13 Ventura and later, on Apple Silicon (M1 and newer) and Intel Macs.

1. **Download.** Open the macOS link from your club (`…/TheClub-macos.dmg`) in Safari or Chrome. Sign in to GitHub if asked.
2. **Open the disk image.** Double-click `TheClub-macos.dmg` in **Downloads**. A window opens showing **The Club** and an **Applications** folder.
3. **Install.** Drag **The Club** onto **Applications**. Then eject the disk image (the ⏏ button next to it in the Finder sidebar) and delete the `.dmg`.
4. **Open it the first time.** The app isn't signed with an Apple Developer ID yet, so macOS blocks the first launch:
   - **macOS 15 Sequoia and later:** open **The Club** from **Applications**. A message says it *could not be verified*: click **Done**. Then open  → **System Settings** → **Privacy & Security**, scroll down to *"The Club" was blocked…*, click **Open Anyway**, and confirm with your Mac password or Touch ID. Click **Open Anyway** once more.
   - **macOS 13 Ventura and 14 Sonoma:** in **Applications**, **right-click** (or Control-click) **The Club** → **Open** → **Open**.

   This is only needed once. Afterwards, open it from Launchpad, Spotlight (⌘ Space, type *The Club*) or the Dock.
5. Sign in with your usual email and password.
6. **Camera (club owners).** Scanning tickets asks to use the camera; click **OK**. To change it later: **System Settings** → **Privacy & Security** → **Camera** → **The Club**.

If macOS says the app *is damaged and can't be opened*, the download was quarantined. Run this in **Terminal**, then open the app again:
```bash
xattr -dr com.apple.quarantine "/Applications/The Club.app"
```

### Updating to a new version

The apps don't update themselves. When your club announces a new version, download it from the same link and install it over the old one:
- **Android:** open the new APK and tap **Update**. You stay signed in.
- **macOS:** drag the new **The Club** onto **Applications** and choose **Replace**. If it's running, quit it first (⌘ Q). The first-launch approval may be needed again.

iPhone and iPad update through TestFlight, as described above.

To see which version you have:
- **Android:** **Settings** → **Apps** → **The Club** (version at the bottom).
- **iPhone and iPad:** **TestFlight** → **The Club**.
- **macOS:** **The Club** menu → **About The Club**.

### Uninstalling

- **Android:** long-press **The Club** → **Uninstall**. You can also go to **Settings** → **Apps** → **The Club** → **Uninstall**.
- **iPhone and iPad:** touch and hold **The Club** → **Remove App** → **Delete App**.
- **macOS:** drag **The Club** from **Applications** to the Bin. Its saved sign-in is in `~/Library/WebKit/com.theclub.app`; delete that folder to remove it too.

### Troubleshooting

| Problem | Fix |
|---|---|
| Download link shows **404** | You're not signed in to GitHub with access to the repository. See the private-repository note in Part 1, step 4. |
| Android: **App not installed** / *package conflicts with an existing package* | The installed copy was signed with a different key: a test build, or a build from before the release key was set up. Uninstall **The Club** and install again. You'll need to sign in again. |
| Android: **There was a problem parsing the package** | The download was incomplete, or the phone runs Android older than 7.0. Download again. |
| Android: **App not installed** when installing an older version | Android doesn't install a lower version over a newer one. Uninstall first. |
| **Cannot reach the server** on the login screen | Check the phone or Mac is online and the website works in a browser. If the website works but the app doesn't, the server is missing Part 1, step 2. |
| The camera doesn't start at check-in | Allow the camera permission as above, or type the ticket code instead. |
| Strava shows *connect on the website* | That's expected: connect Strava once on the website, then tap **Check again** in the app. |
| macOS: **"The Club" can't be opened because Apple cannot check it for malicious software** | Follow step 4 of the macOS install. |
| iPhone: the TestFlight invite says **not available** or the link shows **This beta is full** / **isn't accepting testers** | Ask a club admin: the build may still be in Beta App Review, the group may be full, or the public link was switched off. |
| iPhone: **The Club** won't open and TestFlight says the build **expired** | Install the latest build from TestFlight. |
| Maintainers: the iOS step fails with *No profiles for 'com.theclub.app'* or *Cloud signing permission error* | The API key needs the **Admin** role, and the bundle ID and App Store Connect app must exist (Part 1, step 5). |
| Maintainers: upload fails with *bundle version must be higher* | Every upload needs a new build number. Re-run the workflow; each run uses a higher number. |

---

## Part 3 — Build on your own machine (developers)

All commands run in `web-app/`. Set the gateway URL first, either in the environment or in `.env.native.local` (git-ignored):

```bash
echo 'VITE_API_BASE_URL=https://club.example.com' > .env.native.local
```

### Android (Linux or macOS)

Requires JDK 21 and the Android SDK; installing Android Studio provides the SDK.

```bash
npm run android:sync       # build the UI and copy it into android/
npm run android:open       # open in Android Studio, then Run ▶
# or from the command line:
cd android && ./gradlew assembleDebug    # app/build/outputs/apk/debug/app-debug.apk
```

To test against a backend running on your computer, the emulator reaches it at `http://10.0.2.2:8080`. A real phone on the same Wi-Fi uses `http://<your-computer-ip>:8080`, which requires the gateway port to be exposed beyond `127.0.0.1`. Plain `http://` is for local testing only; the build then allows cleartext traffic.

To build a signed release locally, set `ANDROID_KEYSTORE_FILE`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS` and `ANDROID_KEY_PASSWORD`, plus `APP_VERSION` and `APP_VERSION_CODE`, then run `./gradlew assembleRelease`.

Run `npm run android:sync` again after every UI change.

### iOS (on a Mac only)

Requires Xcode 16 or later from the Mac App Store. Open it once to install its components.

```bash
npm run ios:sync           # build the UI and copy it into ios/
npm run ios:open           # opens ios/App/App.xcodeproj in Xcode
```
In Xcode, choose an iPhone simulator in the toolbar and press **Run ▶**. Swift packages are downloaded on the first build.

The simulator reaches a backend on your Mac at `http://localhost:8080`. Plain HTTP to local addresses is allowed for this; anything else must be HTTPS. To run on your own iPhone, select the **App** target → **Signing & Capabilities** → **Team** (a free Apple ID works for your own device, for 7 days at a time), then pick the phone as the run destination.

Run `npm run ios:sync` again after every UI change.

### macOS (on a Mac only)

Requires Rust (`curl https://sh.rustup.rs -sSf | sh`) and the Xcode Command Line Tools (`xcode-select --install`).

```bash
npm run macos:dev          # app window with live reload against the Vite dev server
npm run macos:build        # src-tauri/target/release/bundle/{macos,dmg}/
```

With a local backend, use `VITE_API_BASE_URL=http://localhost:8080`.

### Icons

The icons are generated from `public/favicon.svg`. The iOS icon is `ios/App/App/Assets.xcassets/AppIcon.appiconset/AppIcon-512@2x.png`: 1024×1024 with **no transparency**, because App Store Connect rejects icons with an alpha channel. Replace it with an opaque square PNG after changing the logo; iOS rounds the corners itself. For the Android and macOS icons, run:

```bash
npx tauri icon public/favicon.svg
for d in mdpi hdpi xhdpi xxhdpi xxxhdpi; do cp src-tauri/icons/android/mipmap-$d/*.png android/app/src/main/res/mipmap-$d/; done
rm -rf src-tauri/icons/android src-tauri/icons/ios
```

### Publishing to the app stores (not set up)

GitHub Releases are enough for a club. Store distribution needs extra accounts and is not automated yet:
- **App Store:** take a build that's already in TestFlight, add screenshots, a description and a privacy policy URL in App Store Connect, and **Submit for Review**. No rebuild is needed.
- **Google Play:** a Play Console developer account (one-time fee). Build an App Bundle with `./gradlew bundleRelease` using the same keystore, and complete the store listing and data-safety form.
- **Signed macOS app:** an Apple Developer Program membership. With it, Tauri signs and notarizes during `tauri build` when `APPLE_CERTIFICATE`, `APPLE_CERTIFICATE_PASSWORD`, `APPLE_SIGNING_IDENTITY`, `APPLE_ID`, `APPLE_PASSWORD` and `APPLE_TEAM_ID` are set. A signed app opens without the first-launch warning.

---

## How the apps differ from the website

- **Session:** the website keeps the refresh token in an HttpOnly cookie. The apps call the API from another origin, so that cookie is never sent. Instead, the apps store the refresh token in their own sandboxed storage and send it in the body of `/api/v1/auth/refresh` and `/logout` (see `src/api/client.ts`). Android cloud backup is disabled so the token stays on the device.
- **CORS:** the gateway must allow all three app origins (Part 1, step 2). The development defaults already do.
- **Strava:** Strava's OAuth callback returns to the website, so members connect Strava once on the website. Syncing and imported activities then work in the apps.
- **QR check-in:** the camera scanner asks for camera permission the first time it is used. The manual ticket-code field works everywhere.
- **Versions:** release tags set the version shown in the apps. Android's internal version number is the workflow run number, which only grows, so every release can update the previous one.
