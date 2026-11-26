import { defineConfig, devices } from '@playwright/test'
import { API_URL, TLS_DIR, WEB_URL } from './e2e/urls.ts'

// Chromium registers no service worker from an origin whose certificate it does not trust, even with
// `ignoreHTTPSErrors`, and the offline test needs the worker. The flag is for this test browser only.
const chromiumOptions = { args: ['--ignore-certificate-errors'] }

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
    baseURL: WEB_URL,
    // The certificate is a local test one (`node --run e2e:cert`), which no browser trusts.
    ignoreHTTPSErrors: true,
    locale: 'pt-BR',
    timezoneId: 'America/Fortaleza',
    trace: 'retain-on-failure',
  },
  // The same suite on a desktop, an Android phone and an iPhone. CI runs each in its own job; locally,
  // `--project` picks one.
  projects: [
    { name: 'chromium', use: { ...devices['Desktop Chrome'], launchOptions: chromiumOptions } },
    { name: 'mobile-chrome', use: { ...devices['Pixel 7'], launchOptions: chromiumOptions } },
    { name: 'mobile-safari', use: { ...devices['iPhone 15'] } },
  ],
  // `vite preview` serves the build with the PWA's service worker, which the offline test needs;
  // `vite dev` does not register it. With `strictPort`, a forgotten `dev` on 5173 fails the run.
  webServer: {
    command: 'node --run build && node --run preview',
    url: WEB_URL,
    ignoreHTTPSErrors: true,
    reuseExistingServer: false,
    timeout: 120_000,
    // The e2e app talks to the real API, even from a shell that set VITE_API_MOCKS. E2E_TLS_DIR has
    // `vite preview` serve HTTPS with the test certificate.
    env: {
      VITE_API_MOCKS: '',
      VITE_API_URL: API_URL,
      VITE_GOOGLE_MAPS_EMBED_KEY: 'e2e-key',
      E2E_TLS_DIR: TLS_DIR,
    },
  },
})
