import type { CapacitorConfig } from '@capacitor/cli'

// A plain-HTTP API (a local backend, e.g. http://10.0.2.2:3000 from the emulator) needs cleartext allowed, and the app
// itself is then served from http://localhost: Android's web view blocks plain-HTTP images on an https:// page even
// with mixed content allowed. Production APIs must be HTTPS, and the app is then served from https://localhost.
const httpApi = (process.env.VITE_API_BASE_URL ?? '').startsWith('http://')

const config: CapacitorConfig = {
  appId: 'com.theclub.app',
  appName: 'The Club',
  webDir: 'dist',
  server: {
    androidScheme: httpApi ? 'http' : 'https',
    cleartext: httpApi,
  },
  android: {
    allowMixedContent: httpApi,
  },
}

export default config
