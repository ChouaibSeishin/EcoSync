import { defineConfig } from '@playwright/test';
export default defineConfig({
  testDir: './e2e', timeout: 90_000, expect: { timeout: 15_000 }, fullyParallel: false, workers: 1, retries: 0,
  reporter: [['list'], ['html', { open: 'never' }]],
  use: { baseURL: process.env.E2E_BASE_URL ?? 'http://127.0.0.1:5173', screenshot: 'only-on-failure', trace: 'off' },
});
