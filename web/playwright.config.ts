import { defineConfig, devices } from '@playwright/test'

// The API runs in Docker Compose with the test sign-in provider; see the README.
export default defineConfig({
  testDir: 'e2e',
  fullyParallel: false,
  workers: 1,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 1 : 0,
  reporter: process.env.CI
    ? [['github'], ['html', { open: 'never' }]]
    : [['list'], ['html', { open: 'never' }]],
  globalSetup: './e2e/global-setup.ts',
  use: {
    baseURL: 'http://localhost:5173',
    locale: 'pt-BR',
    timezoneId: 'America/Fortaleza',
    trace: 'retain-on-failure',
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
  // `vite preview` serves the build with the PWA's service worker, which the offline test needs;
  // `vite dev` does not register it. With `strictPort`, a forgotten `dev` on 5173 fails the run.
  webServer: {
    command: 'node --run build && node --run preview',
    url: 'http://localhost:5173',
    reuseExistingServer: false,
    timeout: 120_000,
  },
})
