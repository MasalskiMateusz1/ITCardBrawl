// Risk: match-board-looks-as-designed - the battle board must keep the look approved against the
// design canvas (Board.dc.html / Card.dc.html) in every key state, at both desktop viewports: the
// layout, colours, type and radii, and no clipping at 1280x720. This is the visual gate of the
// prod-ui change (context/changes/prod-ui/plan.md, Phase 7); baselines are pinned on Linux chromium.
import { expect, test, type Locator, type Page } from '@playwright/test';
import type { MatchView } from '../src/app/game/match';
import { eventStack, gameOverWin, opponentReplay } from './fixtures/board-states';
import { midGame } from './fixtures/mid-game';
import { mockMatch } from './mock-match';

/**
 * Any fixed instants: the board shows no dates, the clock only has to stand still. The clock is
 * installed at INSTALLED and paused a little later (a pause must lie ahead of the running clock).
 */
const INSTALLED = new Date('2026-01-05T10:00:00Z');
const NOW = new Date('2026-01-05T10:01:00Z');

/** Longer than a smooth scroll of the battle log takes in the browser (real time). */
const SMOOTH_SCROLL_MS = 500;

/** Delay between computer moves played back on the table (REPLAY_STEP_MS). */
const REPLAY_STEP_MS = 700;

/**
 * Lets the page render with the clock frozen: the board's timers (turn countdown, replay steps)
 * only move when a test advances them, so Angular's render scheduling (timers, animation frames)
 * runs only in slices like this one.
 */
async function settle(page: Page): Promise<void> {
  await page.clock.runFor(100);
}

/**
 * Advances the frozen clock in short slices until `locator` renders. A response arrives in real
 * time, but the page draws it only on its next (fake) timer or animation frame.
 */
async function rendered(page: Page, locator: Locator): Promise<void> {
  await expect(async () => {
    await page.clock.runFor(16);
    await expect(locator).toBeVisible({ timeout: 50 });
  }).toPass({ intervals: [0] });
}

/**
 * Rests the battle log on its newest entry, where it follows new entries to. Its own smooth scroll
 * runs in real time, outside the frozen clock, and one still in flight can carry it elsewhere (it
 * may aim at a height measured before the table reflowed), so the test puts the log at the bottom
 * and checks it stays there once any such scroll is over.
 */
async function logAtBottom(page: Page): Promise<void> {
  const log = page.getByRole('region', { name: 'Przebieg partii' });
  const gap = () => log.evaluate((box) => box.scrollHeight - box.clientHeight - box.scrollTop);
  await expect(async () => {
    await log.evaluate((box) => (box.scrollTop = box.scrollHeight));
    await page.waitForTimeout(SMOOTH_SCROLL_MS);
    expect(await gap()).toBeLessThanOrEqual(1);
  }).toPass({ intervals: [0] });
}

/**
 * Waits until every card shows the placeholder art: the art requests answer 404 (mockMatch), and
 * a card drops its image on the error, which the page draws only on its next (fake) frame.
 */
async function placeholderArt(page: Page): Promise<void> {
  await expect(async () => {
    await page.clock.runFor(16);
    await expect(page.locator('app-card img')).toHaveCount(0, { timeout: 50 });
  }).toPass({ intervals: [0] });
}

/** Screenshots the page once the art placeholders and the battle log have settled. */
async function snap(page: Page, name: string): Promise<void> {
  await placeholderArt(page);
  if ((await page.getByRole('region', { name: 'Przebieg partii' }).count()) > 0) {
    await logAtBottom(page);
  }
  await expect(page).toHaveScreenshot(name);
}

/** Freezes the clock at NOW, before the page loads, and mocks the API with `match`. */
async function freeze(page: Page, match: MatchView): Promise<void> {
  await page.clock.install({ time: INSTALLED });
  await page.clock.pauseAt(NOW);
  await mockMatch(page, match);
}

/** Opens /match on `match` with the clock frozen and waits for the board. */
async function openBoard(page: Page, match: MatchView): Promise<void> {
  await freeze(page, match);
  await page.goto('/match');
  await rendered(page, page.getByRole('region', { name: 'Przebieg partii' }));
  await settle(page);
}

/** Clicks a card's button with the keyboard, which lifts the card without the aim arrow. */
async function pressCard(page: Page, name: string): Promise<void> {
  const card = page.getByRole('button', { name, exact: true });
  await card.focus();
  await page.keyboard.press('Enter');
  // Focus opens the hover card; drop it so only the selection shows.
  await card.blur();
  await settle(page);
}

test('default mid-game board', async ({ page }) => {
  await openBoard(page, midGame);

  await expect(page.getByRole('timer')).toHaveText(/Pozostało: 45 s/);
  await snap(page, 'mid-game.png');
});

test('attacker selected: targets highlighted, the attacker lifted into the centre', async ({
  page,
}) => {
  await openBoard(page, midGame);

  await pressCard(page, 'Wybierz do ataku: Full-stack Developer');

  await expect(page.getByRole('button', { name: 'Anuluj atak' })).toBeVisible();
  await expect(page.getByRole('button', { name: /^Atakuj: Tester manualny/ })).toBeEnabled();
  await expect(page.locator('app-lifted-card')).toBeVisible();
  await snap(page, 'attacker-selected.png');
});

test('event targeting: the event lifted, its targets highlighted', async ({ page }) => {
  await openBoard(page, midGame);

  await pressCard(page, 'Zagraj: git push --force');

  await expect(
    page.getByRole('status').filter({ hasText: 'Wybierz cel wydarzenia' }),
  ).toBeVisible();
  await expect(page.locator('app-lifted-card')).toBeVisible();
  await snap(page, 'event-targeting.png');
});

// Not a screenshot: the cancel relies on the browser letting a press on a disabled button (an
// illegal target) reach the table, which only a real browser shows.
test('a click on an illegal target cancels the attack selection', async ({ page }) => {
  await openBoard(page, midGame);

  await pressCard(page, 'Wybierz do ataku: Full-stack Developer');
  await expect(page.getByRole('button', { name: 'Anuluj atak' })).toBeVisible();

  // The Guard (Tester manualny) makes Automatyk testów an illegal target: its button is disabled.
  const illegal = page
    .getByRole('region', { name: 'Przeciwnik', exact: true })
    .getByRole('button', { name: 'Automatyk testów', exact: true });
  await expect(illegal).toBeDisabled();
  const box = (await illegal.boundingBox())!;
  await page.mouse.click(box.x + box.width / 2, box.y + box.height / 2);
  await settle(page);

  await expect(page.getByRole('button', { name: 'Anuluj atak' })).toBeHidden();
});

test('hover preview: the hovered hand card shown enlarged', async ({ page }) => {
  await openBoard(page, midGame);

  await page.getByRole('button', { name: 'Zagraj: Junior Developer' }).hover();
  await settle(page);

  await expect(page.locator('app-hover-card')).toBeVisible();
  await snap(page, 'hover-preview.png');
});

test('focus-visible on a board card', async ({ page }) => {
  await openBoard(page, midGame);

  // A key press first, so the browser treats the focus as keyboard focus (:focus-visible).
  await page.keyboard.press('Tab');
  await page.getByRole('button', { name: 'Wybierz do ataku: Gumowa kaczka' }).focus();
  await settle(page);

  await expect(page.locator(':focus-visible')).toHaveAccessibleName(
    'Wybierz do ataku: Gumowa kaczka',
  );
  await snap(page, 'focus-visible.png');
});

test('urgent timer: ten seconds or less left', async ({ page }) => {
  await openBoard(page, midGame);

  await page.clock.runFor(37_000);

  const timer = page.getByRole('timer');
  await expect(timer).toHaveText(/Pozostało: 8 s/);
  await expect(timer).toHaveClass(/urgent/);
  await snap(page, 'urgent-timer.png');
});

test("opponent's turn: its moves played back step by step", async ({ page }) => {
  await openBoard(page, opponentReplay);

  // The second step is due now; the table draws it on the next frame (the third is 700 ms away).
  await page.clock.runFor(REPLAY_STEP_MS);
  await rendered(
    page,
    page.locator('.move-reason').filter({ hasText: 'Przeciwnik dokłada tanią postać' }),
  );
  await expect(page.getByText('Ruch przeciwnika…')).toBeVisible();
  await expect(page.getByRole('region', { name: 'Przeciwnik', exact: true })).toContainText(
    'Stażysta QA',
  );
  await snap(page, 'opponent-replay.png');
});

test("player's graveyard overlay", async ({ page }) => {
  await openBoard(page, midGame);

  await page.getByRole('button', { name: 'Cmentarz 2 — pokaż karty' }).click();
  await settle(page);

  await expect(page.getByRole('heading', { name: 'Twój cmentarz' })).toBeVisible();
  await snap(page, 'graveyard-player.png');
});

// The plan's log overlay was dropped in Phase 5 (the log stays in the right column, covered by
// every board shot); the second overlay shot is the opponent's graveyard with a card shown in full.
test("opponent's graveyard overlay with a card shown in full", async ({ page }) => {
  await openBoard(page, midGame);

  await page.getByRole('button', { name: 'Cmentarz 3 — pokaż karty' }).click();
  await settle(page);
  await page.getByRole('button', { name: 'Pokaż kartę: Stażysta QA' }).click();
  await settle(page);

  await expect(page.getByRole('heading', { name: 'Cmentarz przeciwnika' })).toBeVisible();
  await expect(page.locator('app-graveyard-list app-card.details')).toBeVisible();
  await snap(page, 'graveyard-opponent-card.png');
});

// User decision (2026-11-23): the active events are a fanned stack of cards that opens an overlay
// with the full cards, their effects, turns left and target.
test("opponent's active events overlay", async ({ page }) => {
  await openBoard(page, eventStack);

  await page.getByRole('button', { name: 'Aktywne wydarzenia 2 — pokaż karty' }).click();
  await settle(page);

  await expect(page.getByRole('heading', { name: 'Aktywne wydarzenia przeciwnika' })).toBeVisible();
  await expect(page.locator('app-active-events-list .active-event')).toHaveCount(2);
  await expect(page.getByText('Cel: Backendowiec')).toBeVisible();
  await snap(page, 'active-events-opponent.png');
});

test('game over: the player won', async ({ page }) => {
  await openBoard(page, gameOverWin);

  await expect(page.getByRole('heading', { name: 'Wygrana' })).toBeVisible();
  await snap(page, 'game-over-win.png');
});

test('loading: the board skeleton while the match is fetched', async ({ page }) => {
  await freeze(page, midGame);
  // Registered last, so it wins over mockMatch: the match request never answers.
  await page.route('**/api/matches/current', () => {});
  await page.goto('/match');
  await rendered(page, page.locator('app-board-skeleton'));

  await expect(page.getByRole('status').filter({ hasText: 'Ładowanie partii…' })).toBeVisible();
  await snap(page, 'loading.png');
});

test('load error: the match could not be fetched', async ({ page }) => {
  await freeze(page, midGame);
  // A server error (not 401, not 404) shows the error instead of the board.
  await page.route('**/api/matches/current', (route) => route.fulfill({ status: 500 }));
  await page.goto('/match');
  await rendered(page, page.getByRole('alert'));

  await expect(page.getByRole('alert')).toHaveText(
    'Nie udało się wczytać partii — spróbuj ponownie.',
  );
  await snap(page, 'load-error.png');
  // Nothing to clean up: the API is mocked per test and the tests create no data.
});
