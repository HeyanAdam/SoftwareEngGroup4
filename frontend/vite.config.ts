import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { compression } from 'vite-plugin-compression2'

export default defineConfig({
  plugins: [
    vue(),
    // 构建期静态资源预压缩：生成 .gz 与 .br 供 Nginx 阶段直接使用，
    // 原始文件保留（deleteOriginalAssets: false），不影响无压缩回退。
    compression({
      algorithms: ['gzip', 'brotliCompress'],
      threshold: 1024,
      deleteOriginalAssets: false,
    }),
  ],
  server: {
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
      // C-2：WebSocket 握手必须单独转发，否则 dev server 会返回 404
      '/ws': {
        target: 'ws://localhost:8080',
        ws: true,
        changeOrigin: true,
      },
    },
  },
})
