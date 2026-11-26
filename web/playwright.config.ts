import { defineConfig, devices } from '@playwright/test'

// The API runs in Docker Compose with the test sign-in provider; see the README.
export default defineConfig({
  testDir: 'e2e',
  // The S1 specs allow 60 s and wait up to 20 s for the sync; the default 30 s would cut them short.
  timeout: 90_000,
  fullyParallel: false,
  workers: 1,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 1 : 0,
  // A test that passes only on the retry still fails the run in CI.
  failOnFlakyTests: !!process.env.CI,
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
    // The e2e app talks to the real API, even from a shell that set VITE_API_MOCKS.
    env: { VITE_API_MOCKS: '', VITE_GOOGLE_MAPS_EMBED_KEY: 'e2e-key' },
  },
})
