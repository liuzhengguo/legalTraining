import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// 工程 B 学生移动端 · 开发端口 5173
// 生产走 VITE_API_BASE（https://api.xxx.edu.cn），本地走 /dev-api 代理
export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5173,
    proxy: {
      '/dev-api': {
        target: process.env.VITE_DEV_BACKEND || 'http://localhost:8080',
        changeOrigin: true,
        rewrite: (p) => p.replace(/^\/dev-api/, '')
      }
    }
  },
  build: {
    outDir: 'dist',
    chunkSizeWarningLimit: 800
  }
})
