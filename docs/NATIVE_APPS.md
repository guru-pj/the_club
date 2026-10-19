# Android and macOS apps

The Android app (Capacitor) and the macOS app (Tauri) both wrap the React web app in `web-app/`. They show the same screens and features as the website. They are built from the same source with `vite build --mode native` and installed on the device. They call the gateway at an absolute URL that is fixed at build time.

| | Android | macOS |
|---|---|---|
| Wrapper | Capacitor 8 (`web-app/android/`) | Tauri 2 (`web-app/src-tauri/`) |
| App ID | `com.theclub.app` | `com.theclub.app` |
| Origin seen by the gateway | `https://localhost` | `tauri://localhost` |
| Minimum OS | Android 7.0 (API 24) | macOS 13 Ventura, Apple Silicon and Intel |

## How they differ from the website

- **Session:** the website keeps the refresh token in an HttpOnly cookie. The apps call the API from another origin, so that cookie is never sent. Instead, the apps store the refresh token in their own sandboxed storage and send it in the body of `/api/v1/auth/refresh` and `/logout` (see `src/api/client.ts`). Android cloud backup is disabled so the token stays on the device.
- **CORS:** the gateway must allow both app origins. The development defaults already do. For production, add them to `CORS_ALLOWED_ORIGINS` (the Helm value is `config.corsAllowedOrigins`):
  ```
  CORS_ALLOWED_ORIGINS=https://club.example.com,https://localhost,tauri://localhost
  ```
- **Strava:** Strava's OAuth callback returns to the website, so members connect Strava once on the website. Syncing and imported activities then work in the apps.
- **QR check-in:** the camera scanner asks for camera permission the first time it is used. The manual ticket-code field works everywhere.
- **HTTPS:** production APIs must use HTTPS. A plain-`http://` URL is accepted only for local testing; the Android build then allows cleartext traffic.

## Building in CI (recommended)

The `.github/workflows/apps.yml` workflow builds both apps on every push to `main` that touches `web-app/`, and on demand from the Actions tab (**Native apps → Run workflow**).

1. In the repository, go to **Settings → Secrets and variables → Actions → Variables** and add:
   - `APP_API_BASE_URL`: the public gateway URL, e.g. `https://club.example.com` (required; the jobs are skipped without it)
   - `APP_WEB_URL`: the website URL, shown in the Strava hint (optional)
2. Download the artifacts from the workflow run:
   - `the-club-android`: `app-debug.apk`
   - `the-club-macos`: `The Club_<version>_universal.dmg`

Both builds are **unsigned**:
- **Android:** the APK is debug-signed. Install it with `adb install app-debug.apk`, or allow installs from unknown sources on the phone. Publishing to Google Play needs a release keystore and `assembleRelease`/`bundleRelease`.
- **macOS:** Gatekeeper blocks unsigned apps. Right-click the app → **Open** the first time, or run `xattr -dr com.apple.quarantine "/Applications/The Club.app"`. Distributing to other users needs an Apple Developer ID certificate and notarization (Tauri reads `APPLE_CERTIFICATE`, `APPLE_SIGNING_IDENTITY`, `APPLE_ID`, `APPLE_PASSWORD` and `APPLE_TEAM_ID`).

## Building locally

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

To test against a backend running on your computer, the emulator reaches it at `http://10.0.2.2:8080`. A real phone on the same Wi-Fi uses `http://<your-computer-ip>:8080`, which requires the gateway port to be exposed beyond `127.0.0.1`.

Run `npm run android:sync` again after every UI change.

### macOS (on a Mac only)

Requires Rust (`curl https://sh.rustup.rs -sSf | sh`) and the Xcode Command Line Tools.

```bash
npm run macos:dev          # app window with live reload against the Vite dev server
npm run macos:build        # src-tauri/target/release/bundle/{macos,dmg}/
```

With a local backend, use `VITE_API_BASE_URL=http://localhost:8080`.

## Icons

The icons are generated from `public/favicon.svg`. After changing the logo, regenerate them:

```bash
npx tauri icon public/favicon.svg
for d in mdpi hdpi xhdpi xxhdpi xxxhdpi; do cp src-tauri/icons/android/mipmap-$d/*.png android/app/src/main/res/mipmap-$d/; done
rm -rf src-tauri/icons/android src-tauri/icons/ios
```
