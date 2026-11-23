// Risk: match-board-renders - a signed-in player opening /match mid-game must see the board built
// from the match view: both zones with their cards, the hand, and both side columns (status panels
// and the battle log). Seed for the screenshot gate (context/changes/prod-ui/plan.md, Phase 6).
import { expect, test } from '@playwright/test';
import { midGame } from './fixtures/mid-game';
import { mockMatch } from './mock-match';

test('signed-in player sees the mid-game board: both zones, the hand and the side columns', async ({
  page,
}) => {
  await mockMatch(page, midGame);

  await page.goto('/match');

  // Opponent zone: its characters from the view (the Guard is named in the card's label).
  const opponentZone = page.getByRole('region', { name: 'Przeciwnik', exact: true });
  await expect(opponentZone).toBeVisible();
  await expect(
    opponentZone.getByRole('button', { name: 'Tester manualny (Ochrona)' }),
  ).toBeVisible();
  await expect(opponentZone.getByRole('button', { name: 'Automatyk testów' })).toBeVisible();

  // Player zone: a character that can attack is offered as an attacker.
  const playerZone = page.getByRole('region', { name: 'Ty', exact: true });
  await expect(playerZone).toBeVisible();
  await expect(
    playerZone.getByRole('button', { name: 'Wybierz do ataku: Full-stack Developer' }),
  ).toBeEnabled();

  // Hand: a playable card is offered to play, an unaffordable one is disabled.
  await expect(page.getByRole('button', { name: 'Zagraj: Junior Developer' })).toBeEnabled();
  await expect(page.getByRole('button', { name: 'Senior Developer', exact: true })).toBeDisabled();

  // Side columns: both status panels and the battle log.
  await expect(page.getByRole('region', { name: 'Status przeciwnika' })).toBeVisible();
  await expect(page.getByRole('region', { name: 'Twój status' })).toBeVisible();
  const log = page.getByRole('region', { name: 'Przebieg partii' });
  await expect(log).toBeVisible();
  await expect(log.getByText('Dobierasz: Senior Developer')).toBeVisible();

  await expect(page.getByRole('button', { name: 'Zakończ turę' })).toBeEnabled();
  // Nothing to clean up: the API is mocked per test and the test creates no data.
});
