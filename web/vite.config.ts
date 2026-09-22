import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    // Listen on your Wi-Fi address too, so your phone can open the app
    host: true,
    // Forward /api calls to the Spring Boot server during development
    proxy: {
      '/api': 'http://localhost:8080',
    },
  },
})
