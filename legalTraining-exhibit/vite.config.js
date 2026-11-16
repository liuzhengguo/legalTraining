import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

const backend = process.env.VITE_DEV_BACKEND || 'http://localhost:8080'

// 工程 C 展厅展示端 · 开发端口 5174
// 三种运行模式共用同一套内容接口，只在渲染层分叉（需求 9.6）
export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5174,
    proxy: {
      // 仅代理开放接口，公网工程不接触内部接口（/edu /exh /ops）
      '/profile': { target: backend, changeOrigin: true },
      '/gallery-api': { target: backend, changeOrigin: true, rewrite: p => p.replace('/gallery-api', '/gallery/v1') },
      '/dev-openapi': {
        target: backend,
        changeOrigin: true,
        rewrite: (p) => p.replace(/^\/dev-openapi/, '/openapi/v1')
      }
    }
  },
  build: {
    outDir: 'dist'
  }
})

