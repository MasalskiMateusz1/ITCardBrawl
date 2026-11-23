import type { Page } from '@playwright/test';
import type { Player } from '../src/app/auth.service';
import type { MatchView } from '../src/app/game/match';

/** The signed-in probe's answer: authGuard lets the board route through. */
export const signedInPlayer: Player = {
  provider: 'github',
  displayName: 'Gracz E2E',
  avatarUrl: null,
};

/**
 * Drives the board without a backend: `/api/me` answers signed in and every `/api/matches/**`
 * request (the current match and any move) answers with `match`. Card art (`/media/cards/**`)
 * answers 404, so every card shows the placeholder whatever image files exist.
 */
export async function mockMatch(page: Page, match: MatchView): Promise<void> {
  await page.route('**/api/me', (route) => route.fulfill({ json: signedInPlayer }));
  await page.route('**/api/matches/**', (route) => route.fulfill({ json: match }));
  await page.route('**/media/cards/**', (route) => route.fulfill({ status: 404 }));
}
