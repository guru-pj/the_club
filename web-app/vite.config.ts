/// <reference types="vitest/config" />
import react from '@vitejs/plugin-react'
import { defineConfig, loadEnv } from 'vite'

// In development every /api call is proxied to the gateway, so the browser never needs CORS.
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  // The Android and macOS apps load the UI from the device, so they need the gateway's absolute URL
  if (mode === 'native' && !env.VITE_API_BASE_URL) {
    throw new Error('Set VITE_API_BASE_URL (e.g. https://club.example.com) to build the native apps')
  }
  return {
    plugins: [react()],
    server: {
      port: 5173,
      proxy: {
        '/api': { target: env.GATEWAY_URL || 'http://localhost:8080', changeOrigin: true },
        // uploaded images, served by the web-app container's nginx from the bucket
        '/media': { target: env.MEDIA_URL || 'http://localhost:3000', changeOrigin: true },
      },
    },
    test: {
      environment: 'node',
      include: ['src/**/*.test.ts'],
    },
  }
})
