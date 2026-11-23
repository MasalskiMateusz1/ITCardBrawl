# Test stack

## E2E

<!-- Written by /10x-e2e-setup. Re-run it to change this section; other skills only read it. -->

- runner: Playwright Test, @playwright/test 1.65.1
- config: frontend/playwright.config.ts (run every command from `frontend/`; `npm run e2e` type-checks `e2e/` with `e2e/tsconfig.json` first, then runs `playwright test`)
- single-spec command: npx playwright test e2e/<name>.spec.ts
- full-suite command: npm run e2e
- base URL: http://localhost:4300
- port: 4300 (chosen for e2e: Angular CLI has no preview command and `ng serve` holds 4200 with a proxy to the real backend; override with E2E_PORT)
- web server command: npm run build && node e2e/serve.mjs --port $E2E_PORT (static server for dist/itcardbrawl/browser with SPA fallback; /api/** answers 404 when not mocked); E2E_SKIP_BUILD=1 skips the build (CI builds in its own step); reuseExistingServer outside CI
- auth setup project: none — the board runs against mocked API responses: `mockMatch(page, fixture)` in frontend/e2e/mock-match.ts routes /api/me (signed in), /api/matches/** (the fixture) and /media/cards/** (404, placeholder art); no backend, no OAuth
- storageState: none
- seed: frontend/e2e/seed.spec.ts — protects match-board-renders: a signed-in player opening /match mid-game sees both zones, the hand and both side columns built from the match view
- fixtures: frontend/e2e/fixtures/*.ts, typed as MatchView (frontend/src/app/game/match.ts)
- projects: chromium-1440x900, chromium-1280x720 (Desktop Chrome, locale pl-PL)
- CI: `e2e` job in .github/workflows/ci.yml (npm ci → playwright install --with-deps chromium → npm run build → npm run e2e with E2E_SKIP_BUILD=1; HTML report uploaded on failure)
- browser CLI: --help only (playwright-cli not wired into the agent)
- updated: 2026-11-23
