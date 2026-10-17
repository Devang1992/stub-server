import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// Build output goes straight into the Spring Boot static folder, so the JAR serves the UI.
export default defineConfig({
  plugins: [react()],
  build: {
    outDir: '../backend/src/main/resources/static',
    emptyOutDir: true,
  },
  server: {
    port: 5173,
    proxy: {
      '/api': 'http://localhost:8080',
      '/stub': 'http://localhost:8080',
      '/actuator': 'http://localhost:8080',
    },
  },
});
