import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'
import tailwindcss from '@tailwindcss/vite'
import { fileURLToPath, URL } from 'node:url'

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd())
  const target = env.VITE_DEV_PROXY_TARGET?.trim() || 'http://127.0.0.1:8080'
  const url = new URL(target)
  if (
    !['http:', 'https:'].includes(url.protocol) ||
    url.username ||
    url.password ||
    url.pathname !== '/' ||
    url.search ||
    url.hash
  )
    throw new Error('VITE_DEV_PROXY_TARGET must be an HTTP origin')
  return {
    plugins: [vue(), tailwindcss()],
    resolve: { alias: { '@': fileURLToPath(new URL('./src', import.meta.url)) } },
    server: {
      host: '127.0.0.1',
      port: Number(env.VITE_DEV_PORT || 5173),
      strictPort: true,
      proxy: {
        '/api': { target, changeOrigin: true, headers: { Origin: target } },
        '/uploads': { target, changeOrigin: true },
      },
    },
    build: { target: 'es2023', sourcemap: false },
  }
})
