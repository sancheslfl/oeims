import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig({
  plugins: [react()],
  server: {
    proxy: {
      '/auth':         { target: 'http://localhost:8080', changeOrigin: true },
      '/exams':        { target: 'http://localhost:8080', changeOrigin: true },
      '/sessions':     { target: 'http://localhost:8080', changeOrigin: true },
      '/participants': { target: 'http://localhost:8080', changeOrigin: true },
      '/ws': {
        target: 'ws://localhost:8080',
        ws: true,
        changeOrigin: true,
      },
    },
  },
})
