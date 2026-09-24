import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// The portal is served by Spring Boot at /portal/ (same origin as the API).
// Build output: PORTAL_OUT_DIR (Docker build) or src/main/resources/static/portal (local).
export default defineConfig({
  base: '/portal/',
  plugins: [react()],
  build: {
    outDir: process.env.PORTAL_OUT_DIR || '../src/main/resources/static/portal',
    emptyOutDir: true,
  },
  server: {
    port: 5173,
    proxy: { '/api': 'http://localhost:9094' },
  },
});
