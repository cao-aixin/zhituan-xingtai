import { fileURLToPath, URL } from 'node:url'
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// Vite 配置：开发模式下将 /api 与 /file 请求代理到后端 8092 端口
export default defineConfig({
  plugins: [vue()],
  resolve: {
    // @ 别名指向 src 目录
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url))
    }
  },
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8092',
        changeOrigin: true
      },
      '/file': {
        target: 'http://localhost:8092',
        changeOrigin: true
      }
    }
  },
  build: {
    outDir: 'dist',
    chunkSizeWarningLimit: 1500
  }
})
