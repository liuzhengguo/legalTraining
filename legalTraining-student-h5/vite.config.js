import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { readFileSync } from 'node:fs'

const { version } = JSON.parse(readFileSync(new URL('./package.json', import.meta.url), 'utf8'))

function releaseMetadata(release) {
  const source = JSON.stringify(release)
  return {
    name: 'student-release-metadata',
    configureServer(server) {
      server.middlewares.use('/student-version.json', (_, response) => {
        response.setHeader('Content-Type', 'application/json')
        response.setHeader('Cache-Control', 'no-store')
        response.end(source)
      })
    },
    generateBundle() {
      this.emitFile({ type: 'asset', fileName: 'student-version.json', source })
    }
  }
}

// 工程 B 学生移动端 · 开发端口 5173
// 生产走 VITE_API_BASE（https://api.xxx.edu.cn），本地走 /dev-api 代理
export default defineConfig(({ command }) => {
  const release = { version, build: command === 'build' ? new Date().toISOString() : 'development' }
  return {
    plugins: [vue(), releaseMetadata(release)],
    define: { __STUDENT_RELEASE__: JSON.stringify(release) },
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
  }
})
