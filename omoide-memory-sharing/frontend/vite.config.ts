import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'

// https://vitejs.dev/config/
export default defineConfig({
  plugins: [react(), tailwindcss()],
  server: {
    host: '0.0.0.0',
    port: 5173,
    allowedHosts: ['ganymede.local'],
    proxy: {
      '/feed': 'http://localhost:8080',
      '/content': 'http://localhost:8080',
      '/contents-captured-ym': 'http://localhost:8080',
      '/comment-created-ym': 'http://localhost:8080',
      '/albums': 'http://localhost:8080',
      '/photos': 'http://localhost:8080',
      '/video': 'http://localhost:8080',
    },
  },
  preview: {
    host: '0.0.0.0',
    port: 5173,
    allowedHosts: ['ganymede.local'],
    proxy: {
      '/feed': 'http://localhost:8080',
      '/content': 'http://localhost:8080',
      '/contents-captured-ym': 'http://localhost:8080',
      '/comment-created-ym': 'http://localhost:8080',
      '/albums': 'http://localhost:8080',
      '/photos': 'http://localhost:8080',
      '/video': 'http://localhost:8080',
    },
  },
})
