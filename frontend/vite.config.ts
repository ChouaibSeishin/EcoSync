import { defineConfig } from 'vitest/config';
import react from '@vitejs/plugin-react';
const target = process.env.API_PROXY_TARGET ?? 'http://127.0.0.1:18080';
export default defineConfig({
  plugins: [react()],
  server: { port: 5173, strictPort: true, proxy: { '/api': { target }, '/actuator': { target } } },
  test: { environment: 'jsdom', setupFiles: ['./src/test-setup.ts'], include: ['src/**/*.test.{ts,tsx}'], restoreMocks: true },
});
