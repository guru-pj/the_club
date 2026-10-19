import type { CapacitorConfig } from '@capacitor/cli'

// A plain-HTTP API (a local backend, e.g. http://10.0.2.2:8080 from the emulator) needs cleartext and mixed content
// allowed, because the app itself is served from https://localhost. Production APIs must be HTTPS.
const httpApi = (process.env.VITE_API_BASE_URL ?? '').startsWith('http://')

const config: CapacitorConfig = {
  appId: 'com.theclub.app',
  appName: 'The Club',
  webDir: 'dist',
  server: {
    androidScheme: 'https',
    cleartext: httpApi,
  },
  android: {
    allowMixedContent: httpApi,
  },
}

export default config
