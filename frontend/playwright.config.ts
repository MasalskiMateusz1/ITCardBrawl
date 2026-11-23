import { defineConfig, devices } from '@playwright/test';

// Angular CLI has no preview command, so e2e/serve.mjs serves the production build
// (dist/itcardbrawl/browser) with an SPA fallback. Port 4300 is chosen to stay clear of
// `ng serve` (4200), which proxies to the real backend; E2E_PORT overrides it.
const PORT = Number(process.env['E2E_PORT'] ?? 4300);
const baseURL = `http://localhost:${PORT}`;

// The web server builds the app first, so a local run never tests a stale build. CI builds in its
// own step and sets E2E_SKIP_BUILD=1 to skip the second build.
const serve = `node e2e/serve.mjs --port ${PORT}`;
const command = process.env['E2E_SKIP_BUILD'] ? serve : `npm run build && ${serve}`;

// The API is mocked in the browser (e2e/mock-match.ts), so there is no backend, no OAuth and no
// auth setup project: every test routes /api/me (signed in) and /api/matches/** itself.
export default defineConfig({
  testDir: './e2e',
  fullyParallel: true,
  forbidOnly: !!process.env['CI'],
  retries: process.env['CI'] ? 2 : 0,
  workers: process.env['CI'] ? 1 : undefined,
  reporter: [[process.env['CI'] ? 'github' : 'list'], ['html', { open: 'never' }]],
  use: {
    baseURL,
    // The game is Polish-first and the design canvas is in Polish.
    locale: 'pl-PL',
    trace: 'on-first-retry',
    screenshot: 'only-on-failure',
  },
  projects: [
    {
      name: 'chromium-1440x900',
      use: { ...devices['Desktop Chrome'], viewport: { width: 1440, height: 900 } },
    },
    {
      name: 'chromium-1280x720',
      use: { ...devices['Desktop Chrome'], viewport: { width: 1280, height: 720 } },
    },
  ],
  webServer: {
    command,
    url: baseURL,
    reuseExistingServer: !process.env['CI'],
    timeout: 180_000,
  },
});
