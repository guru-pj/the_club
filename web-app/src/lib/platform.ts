/**
 * True in the Android and iOS (Capacitor) and macOS (Tauri) builds, made with `vite build --mode native`.
 * Those apps load the UI from the device and call the API on another origin, so the HttpOnly refresh cookie
 * (Path=/api/v1/auth, SameSite=Strict) is never sent; the refresh token is kept in app storage instead.
 */
export const isNativeApp = import.meta.env.VITE_NATIVE === 'true'

/** Public URL of the web app, for flows that must finish in a browser (Strava's OAuth callback). */
export const webAppUrl = ((import.meta.env.VITE_WEB_APP_URL as string | undefined) ?? '').replace(/\/$/, '')
