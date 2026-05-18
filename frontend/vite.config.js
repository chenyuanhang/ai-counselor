import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig({
  base: '/counselor/',
  plugins: [react()],
  server: {
    proxy: {
      '/counselor/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/counselor\/api/, '/api'),
      },
      '/counselor/auth': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/counselor\/auth/, '/auth'),
      },
    },
  },
})
