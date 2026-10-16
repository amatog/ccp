import { defineConfig, devices } from '@playwright/test';

/**
 * BASE_URL zeigt auf die zu testende Instanz:
 *  - lokal / CI:  http://localhost:8080 (Standard)
 *  - nach Deploy: http://<VPS_HOST>:8081
 */
export default defineConfig({
  testDir: './tests',
  timeout: 30_000,
  retries: process.env.CI ? 1 : 0,
  reporter: [
    ['list'],
    ['html', { outputFolder: 'playwright-report', open: 'never' }],
    ['json', { outputFile: 'test-results/results.json' }],
  ],
  use: {
    baseURL: process.env.BASE_URL ?? 'http://localhost:8080',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
    locale: 'de-CH',
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
});
