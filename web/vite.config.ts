import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'
import { VitePWA } from 'vite-plugin-pwa'

// https://vite.dev/config/
export default defineConfig({
  plugins: [
    react(),
    VitePWA({
      registerType: 'autoUpdate',
      includeAssets: ['favicon.svg', 'apple-touch-icon.png'],
      manifest: {
        name: 'Anything',
        short_name: 'Anything',
        description: 'Your AI wrote the plan. Anything runs it.',
        start_url: '/',
        scope: '/',
        display: 'standalone',
        orientation: 'portrait',
        background_color: '#f2f2f7',
        theme_color: '#f2f2f7',
        icons: [
          { src: 'icon-192.png', sizes: '192x192', type: 'image/png' },
          { src: 'icon-512.png', sizes: '512x512', type: 'image/png' },
          { src: 'icon-maskable-512.png', sizes: '512x512', type: 'image/png', purpose: 'maskable' },
        ],
      },
      workbox: {
        // The app shell is precached, so it opens instantly even with no signal.
        globPatterns: ['**/*.{js,css,html,svg,png,woff2}'],
        runtimeCaching: [
          {
            // Today's workout: try the network briefly, then fall back to the last copy.
            urlPattern: /\/api\/plans\/\d+\/(today|progress|week).*$/,
            handler: 'NetworkFirst',
            options: {
              cacheName: 'anything-day',
              networkTimeoutSeconds: 3,
              expiration: { maxEntries: 60, maxAgeSeconds: 60 * 60 * 24 * 14 },
              cacheableResponse: { statuses: [200] },
            },
          },
          {
            urlPattern: /\/api\/plans$/,
            handler: 'NetworkFirst',
            options: {
              cacheName: 'anything-plans',
              networkTimeoutSeconds: 3,
              expiration: { maxEntries: 10, maxAgeSeconds: 60 * 60 * 24 * 14 },
              cacheableResponse: { statuses: [200] },
            },
          },
        ],
      },
      devOptions: { enabled: false },
    }),
  ],
  server: {
    // Listen on your Wi-Fi address too, so your phone can open the app
    host: true,
    // Forward /api calls to the Spring Boot server during development
    proxy: {
      '/api': 'http://localhost:8080',
    },
  },
  // `npm run build && npm run preview` serves the real service worker,
  // which is what makes the app installable and able to open offline.
  preview: {
    host: true,
    proxy: {
      '/api': 'http://localhost:8080',
    },
  },
})
