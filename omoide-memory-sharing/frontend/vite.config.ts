import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'
import os from 'node:os'

const hostname = os.hostname().toLowerCase()
const localHost = `${hostname}.local`
const defaultApiUrl = `http://${localHost}:8080`

// https://vitejs.dev/config/
export default defineConfig({
  plugins: [react(), tailwindcss()],
  define: {
    'import.meta.env.VITE_API_URL': JSON.stringify(
      process.env.VITE_API_URL && process.env.VITE_API_URL !== 'http://localhost:8080'
        ? process.env.VITE_API_URL
        : defaultApiUrl
    ),
  },
  server: {
    host: '0.0.0.0',
    port: 5173,
    allowedHosts: [localHost],
    proxy: {
      '/feed': { target: defaultApiUrl, changeOrigin: true },
      '/content': { target: defaultApiUrl, changeOrigin: true },
      '/contents-captured-ym': { target: defaultApiUrl, changeOrigin: true },
      '/comment-created-ym': { target: defaultApiUrl, changeOrigin: true },
      '/albums': { target: defaultApiUrl, changeOrigin: true },
      '/photos': { target: defaultApiUrl, changeOrigin: true },
      '/video': { target: defaultApiUrl, changeOrigin: true },
    },
  },
  preview: {
    host: '0.0.0.0',
    port: 5173,
    allowedHosts: [localHost, 'ganymede.local', '.local'],
    proxy: {
      '/feed': { target: defaultApiUrl, changeOrigin: true },
      '/content': { target: defaultApiUrl, changeOrigin: true },
      '/contents-captured-ym': { target: defaultApiUrl, changeOrigin: true },
      '/comment-created-ym': { target: defaultApiUrl, changeOrigin: true },
      '/albums': { target: defaultApiUrl, changeOrigin: true },
      '/photos': { target: defaultApiUrl, changeOrigin: true },
      '/video': { target: defaultApiUrl, changeOrigin: true },
    },
  },
})

