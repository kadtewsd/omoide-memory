import { defineConfig, loadEnv } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'
import path from 'node:path'

// https://vitejs.dev/config/
export default defineConfig(({ mode }) => {
    const env = loadEnv(mode, process.cwd(), '')
    const backendTarget = env.VITE_API_URL || 'http://localhost:8080'
    const allowedHosts = env.VITE_ALLOWED_HOSTS
        ? env.VITE_ALLOWED_HOSTS.split(',').map(h => h.trim())
        : ['localhost', '127.0.0.1', '.local']

    const proxyConfig = {
        '/feed': { target: backendTarget, changeOrigin: true },
        '/content': { target: backendTarget, changeOrigin: true },
        '/contents-captured-ym': { target: backendTarget, changeOrigin: true },
        '/comment-created-ym': { target: backendTarget, changeOrigin: true },
        '/albums': { target: backendTarget, changeOrigin: true },
        '/photos': { target: backendTarget, changeOrigin: true },
        '/video': { target: backendTarget, changeOrigin: true },
    }

    return {
        plugins: [react(), tailwindcss()],
        server: {
            host: '0.0.0.0',
            port: 5173,
            allowedHosts,
            proxy: proxyConfig,
        },
        resolve: {
            alias: {
                '@': path.resolve(__dirname, './src'),
            }
        },
        preview: {
            host: '0.0.0.0',
            port: 5173,
            allowedHosts,
            proxy: proxyConfig,
        },
        build: {
            sourcemap: true,
        },
    }
})
