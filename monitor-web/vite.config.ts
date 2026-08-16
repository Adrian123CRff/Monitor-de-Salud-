import { defineConfig } from 'vitest/config';
import react from '@vitejs/plugin-react';

// ADR 0004: artefacto único -- el build compila directo a los recursos
// estáticos de monitor-api (no a dist/ + un paso de copia aparte), así
// Spring Boot los sirve sin configuración adicional. frontend-maven-plugin
// (ver monitor-api/pom.xml) dispara este build durante `mvn install`, para
// que un clon nuevo del repo produzca el artefacto completo sin pasos
// manuales de npm.
// El proxy de abajo evita CORS solo durante `npm run dev`.
//
// "vitest/config" en vez de "vite": re-exporta el defineConfig de Vite
// pero acepta también el campo "test" de abajo -- npm run test (Vitest) NO
// forma parte del build de Maven (a diferencia de "npm run build"), así
// que un jsdom roto nunca bloquea `mvn install`.
export default defineConfig({
  plugins: [react()],
  server: {
    proxy: {
      '/api': { target: 'http://localhost:8080', changeOrigin: true },
    },
  },
  build: { outDir: '../monitor-api/src/main/resources/static', emptyOutDir: true, sourcemap: true },
  test: {
    environment: 'jsdom',
    setupFiles: ['./src/test/setup.ts'],
  },
});
