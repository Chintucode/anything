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
        // Screens open from the cached app even offline; the API never does.
        navigateFallbackDenylist: [/^\/api\//],
        runtimeCaching: [
          {
            // Today's workout: the network decides, and the cache is only for when
            // there is no network at all.
            //
            // There used to be a three-second timeout here, which sounds generous
            // and is actively harmful: on a slow-but-working connection the cache
            // answered instead, with a copy up to a fortnight old, and the app had
            // no way to tell that from the truth. Tick an exercise, watch the
            // refetch be answered from before the tick, and watch the row untick
            // itself — permanently, because the server and the screen now disagree
            // and nothing will ever correct it.
            //
            // Without the timeout, Workbox falls back to the cache only when the
            // request genuinely fails, which is exactly the offline case. A slow
            // connection now means waiting, which is honest.
            urlPattern: /\/api\/plans\/\d+\/(today|progress|week).*$/,
            handler: 'NetworkFirst',
            options: {
              cacheName: 'anything-day',
              // Three days: enough to open the app offline on a trip, not enough
              // for the fallback to be from another training week.
              expiration: { maxEntries: 60, maxAgeSeconds: 60 * 60 * 24 * 3 },
              cacheableResponse: { statuses: [200] },
            },
          },
          {
            urlPattern: /\/api\/plans$/,
            handler: 'NetworkFirst',
            options: {
              cacheName: 'anything-plans',
              expiration: { maxEntries: 10, maxAgeSeconds: 60 * 60 * 24 * 3 },
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
