import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// ADR 0004: el backend sirve este build compilado (dist/) como estático.
// El proxy de abajo evita CORS solo durante `npm run dev`.
export default defineConfig({
  plugins: [react()],
  server: {
    proxy: {
      '/api': { target: 'http://localhost:8080', changeOrigin: true },
    },
  },
  build: { outDir: 'dist', sourcemap: true },
});
