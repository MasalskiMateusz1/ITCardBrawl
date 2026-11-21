import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { GameService } from '../game/game.service';
import {
  BoardCharacter,
  EffectView,
  EndReason,
  HandCard,
  LogEntry,
  MatchView,
  PlayedEvent,
  ReplayStep,
  Side,
  Target,
} from '../game/match';
import { LANGUAGE_STORAGE_KEY, LanguageService } from '../i18n/language.service';
import { MatchTable, REPLAY_STEP_MS } from './match-table';

const STEP_MS = 1000;

const MATCH_ID = '00000000-0000-0000-0000-000000000001';

const GUARD = { keyword: 'GUARD', text: 'Ochrona: musi zostać zaatakowana pierwsza' } as const;

const BUFF: EffectView = {
  kind: 'ATTACK',
  scope: 'TARGET',
  amount: 2,
  text: 'Wybrana postać: +2 ataku',
};

const GAIN: EffectView = {
  kind: 'BUDGET_GAIN',
  scope: 'SELF',
  amount: 2,
  text: 'Dodaj 2 do budżetu w tej turze',
};

/** Hand cards without legality; `hand()` adds it the way the backend would. */
const CARDS: Omit<HandCard, 'playable' | 'targets'>[] = [
  {
    instanceId: 1,
    cardId: 'dev-1',
    name: 'Junior Dev',
    type: 'CHARACTER',
    cost: 1,
    text: 'Pisze kod.',
    neutral: false,
    attack: 1,
    health: 2,
    ability: null,
    duration: null,
    effects: [],
    requiresTarget: false,
  },
  {
    instanceId: 2,
    cardId: 'dev-2',
    name: 'Senior Dev',
    type: 'CHARACTER',
    cost: 3,
    text: 'Refaktoryzuje.',
    neutral: false,
    attack: 3,
    health: 4,
    ability: null,
    duration: null,
    effects: [],
    requiresTarget: false,
  },
  {
    instanceId: 3,
    cardId: 'dev-3',
    name: 'Hotfix',
    type: 'EVENT',
    cost: 2,
    text: 'Gasi pożar.',
    neutral: false,
    attack: null,
    health: null,
    ability: null,
    duration: 2,
    effects: [BUFF],
    requiresTarget: true,
  },
  {
    instanceId: 4,
    cardId: 'neu-1',
    name: 'Kawa',
    type: 'EVENT',
    cost: 5,
    text: 'Dodaje energii.',
    neutral: true,
    attack: null,
    health: null,
    ability: null,
    duration: 0,
    effects: [GAIN],
    requiresTarget: false,
  },
  {
    instanceId: 5,
    cardId: 'neu-2',
    name: 'Stażysta',
    type: 'CHARACTER',
    cost: 4,
    text: 'Uczy się.',
    neutral: true,
    attack: 2,
    health: 3,
    ability: GUARD,
    duration: null,
    effects: [],
    requiresTarget: false,
  },
];

/**
 * The hand as the backend sends it: a card is playable on the player's turn when it is affordable;
 * the targeted event gets `eventTargets`.
 */
function hand(budget: number, active: Side = 'PLAYER', eventTargets: Target[] = []): HandCard[] {
  return CARDS.map((card) => ({
    ...card,
    playable: active === 'PLAYER' && card.cost <= budget,
    targets: card.requiresTarget ? eventTargets : [],
  }));
}

const HAND = hand(1);

function character(
  instanceId: number,
  name: string,
  overrides: Partial<BoardCharacter> = {},
): BoardCharacter {
  return {
    instanceId,
    cardId: `card-${instanceId}`,
    name,
    attack: 2,
    health: 3,
    damage: 0,
    canAttack: true,
    ability: null,
    baseAttack: overrides.attack ?? 2,
    baseHealth: overrides.health ?? 3,
    keywords: [],
    trapped: false,
    attackTargets: [],
    ...overrides,
  };
}

function matchView(active: Side = 'PLAYER'): MatchView {
  return {
    matchId: MATCH_ID,
    status: 'IN_PROGRESS',
    turn: { number: 1, active },
    player: {
      faction: { id: 'developers', name: 'Developerzy' },
      hp: 30,
      maxHp: 30,
      budget: { current: 1, max: 1 },
      deckCount: 25,
      hand: hand(1, active),
      board: [],
      activeEvents: [],
      graveyardCount: 0,
      lastEvent: null,
    },
    opponent: {
      faction: { id: 'testers', name: 'Testerzy' },
      hp: 30,
      maxHp: 30,
      budget: { current: 0, max: 0 },
      deckCount: 24,
      handCount: 6,
      board: [],
      activeEvents: [],
      graveyardCount: 0,
      lastEvent: null,
    },
    result: null,
    log: [],
    replay: [],
  };
}

const OPPONENT_PLAYER: Target = { kind: 'PLAYER', instanceId: null, side: 'OPPONENT' };
const PLAYER_SELF: Target = { kind: 'PLAYER', instanceId: null, side: 'PLAYER' };

function characterTarget(instanceId: number): Target {
  return { kind: 'CHARACTER', instanceId, side: null };
}

/**
 * A view mid-game: the player has an attacker on the board, the opponent two characters. With Guard
 * the backend allows only the Guard character as an attack target.
 */
function boardView(opponentGuard = false, eventTargets: Target[] = []): MatchView {
  const match = matchView();
  return {
    ...match,
    turn: { number: 5, active: 'PLAYER' },
    player: {
      ...match.player,
      budget: { current: 3, max: 3 },
      hand: hand(3, 'PLAYER', eventTargets),
      board: [
        character(10, 'Tech Lead', {
          attack: 4,
          health: 5,
          damage: 2,
          attackTargets: opponentGuard
            ? [characterTarget(21)]
            : [OPPONENT_PLAYER, characterTarget(20), characterTarget(21)],
        }),
      ],
    },
    opponent: {
      ...match.opponent,
      board: [
        character(20, 'QA Junior', { canAttack: false }),
        character(21, 'QA Lead', {
          canAttack: false,
          ability: opponentGuard ? GUARD : null,
          keywords: opponentGuard ? ['GUARD'] : [],
        }),
      ],
    },
  };
}

/** boardView() with the "Hotfix" event (instance 3) targeting Tech Lead, QA Lead or the opponent. */
function targetView(): MatchView {
  return boardView(false, [characterTarget(10), characterTarget(21), OPPONENT_PLAYER]);
}

const HOTFIX_PLAYED: PlayedEvent = { instanceId: 3, name: 'Hotfix', effects: [BUFF] };

function finishedView(winner: Side, reason: EndReason): MatchView {
  const match = matchView();
  return {
    ...match,
    status: 'FINISHED',
    player: { ...match.player, hp: winner === 'PLAYER' ? 26 : -3 },
    result: { winner, reason },
  };
}

describe('MatchTable', () => {
  let httpTesting: HttpTestingController;

  beforeEach(async () => {
    // Polish before LanguageService is created (jsdom reports navigator.language = en-US).
    localStorage.setItem(LANGUAGE_STORAGE_KEY, 'pl');
    await TestBed.configureTestingModule({
      imports: [MatchTable],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: REPLAY_STEP_MS, useValue: STEP_MS },
      ],
    }).compileComponents();

    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    // Restore shared state first, so a failing verify() cannot leak fake timers or the locale.
    vi.useRealTimers();
    localStorage.clear();
    httpTesting.verify();
  });

  async function render(): Promise<ComponentFixture<MatchTable>> {
    const fixture = TestBed.createComponent(MatchTable);
    await fixture.whenStable();
    return fixture;
  }

  async function renderFixture(match: MatchView): Promise<ComponentFixture<MatchTable>> {
    const fixture = await render();
    httpTesting.expectOne({ method: 'GET', url: '/api/matches/current' }).flush(match);
    await fixture.whenStable();
    return fixture;
  }

  async function renderWithMatch(match: MatchView = matchView()): Promise<HTMLElement> {
    const fixture = await renderFixture(match);
    return fixture.nativeElement as HTMLElement;
  }

  function text(element: Element | null): string {
    return element?.textContent?.replace(/\s+/g, ' ').trim() ?? '';
  }

  function button(root: ParentNode, selector: string): HTMLButtonElement {
    const found = root.querySelector<HTMLButtonElement>(selector);
    expect(found).not.toBeNull();
    return found!;
  }

  function playButtons(element: HTMLElement): HTMLButtonElement[] {
    return Array.from(element.querySelectorAll<HTMLButtonElement>('.player .card button.play'));
  }

  it('renders the five player cards with name, cost and type', async () => {
    const element = await renderWithMatch();

    const cards = Array.from(element.querySelectorAll('.player .card'));
    expect(cards.length).toBe(5);
    expect(text(cards[0].querySelector('.card-name'))).toBe('Junior Dev');
    expect(text(cards[0].querySelector('.card-cost'))).toBe('Koszt: 1');
    expect(text(cards[0].querySelector('.card-type'))).toBe('Postać');
    expect(text(cards[0].querySelector('.card-text'))).toBe('Pisze kod.');
    expect(text(cards[2].querySelector('.card-name'))).toBe('Hotfix');
    expect(text(cards[2].querySelector('.card-cost'))).toBe('Koszt: 2');
    expect(text(cards[2].querySelector('.card-type'))).toBe('Wydarzenie');
  });

  it('shows the HP of both sides, opponent card counts and budgets', async () => {
    const element = await renderWithMatch();

    const opponent = element.querySelector('.opponent')!;
    const player = element.querySelector('.player')!;
    expect(text(opponent)).toContain('Testerzy');
    expect(text(opponent.querySelector('.hp dd'))).toBe('30/30');
    expect(text(opponent.querySelector('.budget dd'))).toBe('0/0');
    expect(text(opponent.querySelector('.hand-count dd'))).toBe('6');
    expect(text(opponent.querySelector('.deck-count dd'))).toBe('24');
    expect(text(player)).toContain('Developerzy');
    expect(text(player.querySelector('.hp dd'))).toBe('30/30');
    expect(text(player.querySelector('.budget dd'))).toBe('1/1');
    expect(text(player.querySelector('.budget dt'))).toBe('Budżet');
    expect(text(player.querySelector('.deck-count dd'))).toBe('25');
  });

  it('labels only neutral cards as "Neutralna"', async () => {
    const element = await renderWithMatch();

    const neutralNames = Array.from(element.querySelectorAll('.player .card'))
      .filter((card) => card.querySelector('.card-neutral') !== null)
      .map((card) => text(card.querySelector('.card-name')));
    expect(neutralNames).toEqual(['Kawa', 'Stażysta']);
    expect(text(element.querySelector('.card-neutral'))).toBe('Neutralna');
  });

  it('shows the turn label when the player is active', async () => {
    const element = await renderWithMatch(matchView('PLAYER'));

    expect(text(element.querySelector('.turn'))).toBe('Tura 1 — Twoja tura');
  });

  it('shows the turn label when the opponent is active', async () => {
    const element = await renderWithMatch(matchView('OPPONENT'));

    expect(text(element.querySelector('.turn'))).toBe('Tura 1 — tura przeciwnika');
  });

  it('navigates to /new-match when there is no current match (404)', async () => {
    const navigateByUrl = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    const fixture = await render();

    httpTesting
      .expectOne({ method: 'GET', url: '/api/matches/current' })
      .flush('Not Found', { status: 404, statusText: 'Not Found' });
    await fixture.whenStable();

    expect(navigateByUrl).toHaveBeenCalledWith('/new-match');
    expect((fixture.nativeElement as HTMLElement).querySelector('.message')).toBeNull();
  });

  it('shows an error and does not navigate when loading fails with 500', async () => {
    const navigateByUrl = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    const fixture = await render();

    httpTesting
      .expectOne({ method: 'GET', url: '/api/matches/current' })
      .flush('Server Error', { status: 500, statusText: 'Internal Server Error' });
    await fixture.whenStable();

    expect(navigateByUrl).not.toHaveBeenCalled();
    expect((fixture.nativeElement as HTMLElement).textContent).toContain(
      'Nie udało się wczytać partii — spróbuj ponownie.',
    );
  });

  it('links back to the lobby', async () => {
    const element = await renderWithMatch();

    const link = Array.from(element.querySelectorAll('a')).find((candidate) =>
      candidate.textContent?.includes('Wróć do lobby'),
    );
    expect(link?.getAttribute('href')).toBe('/');
  });

  it('renders in English when English is chosen', async () => {
    TestBed.inject(LanguageService).setLanguage('en');

    const element = await renderWithMatch(matchViewEn());

    expect(text(element.querySelector('.turn'))).toBe('Turn 1 — your turn');
    expect(element.querySelector('.opponent')?.getAttribute('aria-label')).toBe('Opponent');
    expect(element.querySelector('.player')?.getAttribute('aria-label')).toBe('You');
    expect(text(element.querySelector('.opponent h2'))).toBe('Opponent: Testers');
    expect(text(element.querySelector('.player h2'))).toBe('You: Developers');
    expect(text(element.querySelector('.opponent .hp dt'))).toBe('HP');
    expect(text(element.querySelector('.opponent .budget dt'))).toBe('Budget');
    expect(text(element.querySelector('.opponent .hand-count dt'))).toBe('Cards in hand');
    expect(text(element.querySelector('.opponent .deck-count dt'))).toBe('Cards in deck');
    expect(text(element.querySelector('.player .hand-heading'))).toBe('Your hand');
    const cards = Array.from(element.querySelectorAll('.player .card'));
    expect(text(cards[0].querySelector('.card-cost'))).toBe('Cost: 1');
    expect(text(cards[0].querySelector('.card-type'))).toBe('Character');
    expect(text(cards[2].querySelector('.card-type'))).toBe('Event');
    expect(text(element.querySelector('.card-neutral'))).toBe('Neutral');
    expect(text(playButtons(element)[0])).toBe('Play');
    expect(text(element.querySelector('.end-turn'))).toBe('End turn');
    expect(text(element.querySelector('a'))).toBe('Back to lobby');
  });

  it('refetches the current match once on a language change and renders it in English', async () => {
    const fixture = await render();
    httpTesting.expectOne({ method: 'GET', url: '/api/matches/current' }).flush(matchView());
    await fixture.whenStable();
    const element = fixture.nativeElement as HTMLElement;
    expect(text(element.querySelector('.turn'))).toBe('Tura 1 — Twoja tura');

    TestBed.inject(LanguageService).setLanguage('en');
    await fixture.whenStable();
    httpTesting.expectOne({ method: 'GET', url: '/api/matches/current' }).flush(matchViewEn());
    await fixture.whenStable();

    expect(text(element.querySelector('.turn'))).toBe('Turn 1 — your turn');
    expect(text(element.querySelector('.player h2'))).toBe('You: Developers');
    const cards = Array.from(element.querySelectorAll('.player .card'));
    expect(cards.length).toBe(5);
    expect(text(cards[0].querySelector('.card-text'))).toBe('Writes code.');
    expect(text(cards[0].querySelector('.card-type'))).toBe('Character');
  });

  it('cancels in-flight match requests on rapid language switches and renders only the last', async () => {
    const fixture = await render();
    const element = fixture.nativeElement as HTMLElement;
    const language = TestBed.inject(LanguageService);

    language.setLanguage('en');
    await fixture.whenStable();
    language.setLanguage('pl');
    await fixture.whenStable();

    const requests = httpTesting.match({ method: 'GET', url: '/api/matches/current' });
    expect(requests.length).toBe(3);
    expect(requests[0].cancelled).toBe(true);
    expect(requests[1].cancelled).toBe(true);
    expect(requests[2].cancelled).toBe(false);
    requests[2].flush(matchView());
    await fixture.whenStable();

    expect(text(element.querySelector('.turn'))).toBe('Tura 1 — Twoja tura');
    expect(text(element.querySelector('.player h2'))).toContain('Developerzy');
  });

  it('drops the Polish table when the refetch after switching to English fails', async () => {
    const fixture = await render();
    const element = fixture.nativeElement as HTMLElement;
    httpTesting.expectOne({ method: 'GET', url: '/api/matches/current' }).flush(matchView());
    await fixture.whenStable();
    expect(element.querySelectorAll('.player .card').length).toBe(5);

    TestBed.inject(LanguageService).setLanguage('en');
    await fixture.whenStable();
    httpTesting
      .expectOne({ method: 'GET', url: '/api/matches/current' })
      .flush('Server Error', { status: 500, statusText: 'Internal Server Error' });
    await fixture.whenStable();

    expect(element.querySelectorAll('.player .card').length).toBe(0);
    expect(element.textContent).not.toContain('Developerzy');
    expect(element.textContent).toContain('Could not load the match — please try again.');
  });

  it('shows a load error in English after switching from Polish', async () => {
    const fixture = await render();
    const element = fixture.nativeElement as HTMLElement;
    httpTesting
      .expectOne({ method: 'GET', url: '/api/matches/current' })
      .flush('Server Error', { status: 500, statusText: 'Internal Server Error' });
    await fixture.whenStable();
    expect(element.textContent).toContain('Nie udało się wczytać partii — spróbuj ponownie.');

    TestBed.inject(LanguageService).setLanguage('en');
    await fixture.whenStable();

    expect(element.textContent).toContain('Could not load the match — please try again.');
    httpTesting
      .expectOne({ method: 'GET', url: '/api/matches/current' })
      .flush('Server Error', { status: 500, statusText: 'Internal Server Error' });
    await fixture.whenStable();
    expect(element.textContent).toContain('Could not load the match — please try again.');
  });

  it('plays a card with the current turn and renders the returned board', async () => {
    const fixture = await renderFixture(matchView());
    const element = fixture.nativeElement as HTMLElement;

    playButtons(element)[0].click();
    await fixture.whenStable();

    const request = httpTesting.expectOne({ method: 'POST', url: `/api/matches/${MATCH_ID}/play` });
    expect(request.request.body).toEqual({ turn: 1, instanceId: 1, target: null });
    const after = matchView();
    request.flush({
      ...after,
      player: {
        ...after.player,
        budget: { current: 0, max: 1 },
        hand: HAND.slice(1),
        board: [character(1, 'Junior Dev', { attack: 1, health: 2, canAttack: false })],
      },
    });
    await fixture.whenStable();

    const board = Array.from(element.querySelectorAll('.player .board .character'));
    expect(board.length).toBe(1);
    expect(text(board[0].querySelector('.character-name'))).toBe('Junior Dev');
    expect(text(board[0].querySelector('.character-stats'))).toBe('Atak 1 / HP 2');
    expect(element.querySelectorAll('.player .card').length).toBe(4);
    expect(text(element.querySelector('.player .budget dd'))).toBe('0/1');
  });

  it('disables "Zagraj" for a card that costs too much', async () => {
    const element = await renderWithMatch();

    const buttons = playButtons(element);
    expect(text(buttons[0])).toBe('Zagraj');
    expect(buttons[0].disabled).toBe(false);
    expect(buttons[1].disabled).toBe(true);
    expect(buttons[2].disabled).toBe(true);
  });

  it("disables 'Zagraj' and 'Zakończ turę' on the opponent's turn", async () => {
    const element = await renderWithMatch(matchView('OPPONENT'));

    expect(playButtons(element).every((candidate) => candidate.disabled)).toBe(true);
    expect(button(element, '.end-turn').disabled).toBe(true);
  });

  it('disables "Zagraj" for a card the backend marks as not playable', async () => {
    const match = matchView();
    const element = await renderWithMatch({
      ...match,
      player: {
        ...match.player,
        budget: { current: 5, max: 5 },
        hand: hand(5).map((card) => (card.instanceId === 1 ? { ...card, playable: false } : card)),
      },
    });

    const buttons = playButtons(element);
    expect(buttons[0].disabled).toBe(true); // affordable, but e.g. the board is full
    expect(buttons[2].disabled).toBe(false);
  });

  it('plays an event without a target at once with target null', async () => {
    const match = matchView();
    const fixture = await renderFixture({
      ...match,
      player: { ...match.player, budget: { current: 5, max: 5 }, hand: hand(5) },
    });
    const element = fixture.nativeElement as HTMLElement;

    playButtons(element)[3].click();
    await fixture.whenStable();

    expect(element.querySelector('.cancel-target')).toBeNull();
    const request = httpTesting.expectOne({ method: 'POST', url: `/api/matches/${MATCH_ID}/play` });
    expect(request.request.body).toEqual({ turn: 1, instanceId: 4, target: null });
    request.flush(matchView());
    await fixture.whenStable();
  });

  it('shows card stats, ability and event duration in the hand', async () => {
    const element = await renderWithMatch();

    const cards = Array.from(element.querySelectorAll('.player .card'));
    expect(text(cards[0].querySelector('.card-stats'))).toBe('Atak 1 / HP 2');
    expect(cards[0].querySelector('.card-duration')).toBeNull();
    expect(text(cards[2].querySelector('.card-duration'))).toBe('Czas trwania (tury): 2');
    expect(cards[2].querySelector('.card-stats')).toBeNull();
    expect(text(cards[3].querySelector('.card-duration'))).toBe('Natychmiastowe');
    expect(Array.from(cards[2].querySelectorAll('.effects .effect')).map(text)).toEqual([
      'Wybrana postać: +2 ataku',
    ]);
    expect(Array.from(cards[3].querySelectorAll('.effects .effect')).map(text)).toEqual([
      'Dodaj 2 do budżetu w tej turze',
    ]);
    expect(cards[0].querySelector('.effects')).toBeNull();
    expect(text(cards[4].querySelector('.ability'))).toBe(
      'Ochrona: musi zostać zaatakowana pierwsza',
    );
  });

  it('renders boards with remaining HP, active events, graveyard and negative player HP', async () => {
    const match = boardView();
    const element = await renderWithMatch({
      ...match,
      player: {
        ...match.player,
        hp: -3,
        graveyardCount: 2,
        activeEvents: [
          {
            instanceId: 30,
            cardId: 'ev',
            name: 'Sprint',
            turnsLeft: 2,
            effects: [BUFF],
            targetName: 'Tech Lead',
          },
        ],
      },
      opponent: {
        ...match.opponent,
        graveyardCount: 4,
        activeEvents: [
          {
            instanceId: 31,
            cardId: 'ev2',
            name: 'Code Freeze',
            turnsLeft: 1,
            effects: [
              { kind: 'RUSH', scope: 'OWN_BOARD', amount: 0, text: 'Własne postacie: Szarża' },
            ],
            targetName: null,
          },
        ],
      },
    });

    const player = element.querySelector('.player')!;
    const opponent = element.querySelector('.opponent')!;
    expect(text(player.querySelector('.hp dd'))).toBe('-3/30');
    expect(text(player.querySelector('.character-stats'))).toBe('Atak 4 / HP 3');
    expect(text(player.querySelector('.graveyard dd'))).toBe('2');
    expect(text(opponent.querySelector('.graveyard dd'))).toBe('4');
    expect(text(player.querySelector('.event-name'))).toBe('Sprint');
    expect(text(player.querySelector('.event-turns'))).toBe('Pozostało tur: 2');
    expect(text(opponent.querySelector('.event-name'))).toBe('Code Freeze');
    expect(text(opponent.querySelector('.event-turns'))).toBe('Pozostało tur: 1');
    expect(text(player.querySelector('.event .effect'))).toBe('Wybrana postać: +2 ataku');
    expect(text(player.querySelector('.event-target-name'))).toBe('Cel: Tech Lead');
    expect(text(opponent.querySelector('.event .effect'))).toBe('Własne postacie: Szarża');
    expect(opponent.querySelector('.event-target-name')).toBeNull();
    expect(text(player.querySelector('.events-heading'))).toBe('Aktywne wydarzenia');
  });

  it('attacks the opponent player after choosing an attacker', async () => {
    const fixture = await renderFixture(boardView());
    const element = fixture.nativeElement as HTMLElement;
    expect(element.querySelector('.attack-player')).toBeNull();
    expect(element.querySelector('.opponent .target')).toBeNull();

    const attackButton = button(element, '.player .character button.attack');
    expect(text(attackButton)).toBe('Atakuj');
    attackButton.click();
    await fixture.whenStable();

    const attackPlayer = button(element, '.attack-player');
    expect(text(attackPlayer)).toBe('Atakuj gracza');
    expect(attackPlayer.disabled).toBe(false);
    expect(element.querySelectorAll('.opponent .target').length).toBe(2);
    attackPlayer.click();
    await fixture.whenStable();

    const request = httpTesting.expectOne({
      method: 'POST',
      url: `/api/matches/${MATCH_ID}/attack`,
    });
    expect(request.request.body).toEqual({ turn: 5, attackerId: 10, targetId: null });
    request.flush(boardView());
    await fixture.whenStable();

    expect(element.querySelector('.attack-player')).toBeNull();
  });

  it('attacks a character target and allows cancelling the selection', async () => {
    const fixture = await renderFixture(boardView());
    const element = fixture.nativeElement as HTMLElement;

    button(element, '.player .character button.attack').click();
    await fixture.whenStable();
    button(element, '.cancel-attack').click();
    await fixture.whenStable();
    expect(element.querySelector('.opponent .target')).toBeNull();

    button(element, '.player .character button.attack').click();
    await fixture.whenStable();
    const targets = Array.from(element.querySelectorAll<HTMLButtonElement>('.opponent .target'));
    expect(text(targets[0])).toBe('Cel');
    targets[1].click();
    await fixture.whenStable();

    const request = httpTesting.expectOne({
      method: 'POST',
      url: `/api/matches/${MATCH_ID}/attack`,
    });
    expect(request.request.body).toEqual({ turn: 5, attackerId: 10, targetId: 21 });
    request.flush(boardView());
    await fixture.whenStable();
  });

  it('enables only the attack targets the backend allows', async () => {
    const fixture = await renderFixture(boardView(true));
    const element = fixture.nativeElement as HTMLElement;

    button(element, '.player .character button.attack').click();
    await fixture.whenStable();

    expect(button(element, '.attack-player').disabled).toBe(true);
    const targets = Array.from(element.querySelectorAll<HTMLButtonElement>('.opponent .target'));
    expect(targets[0].disabled).toBe(true);
    expect(targets[1].disabled).toBe(false);
  });

  it('enters target mode for an event with a target and offers only its targets', async () => {
    const fixture = await renderFixture(targetView());
    const element = fixture.nativeElement as HTMLElement;
    expect(element.querySelector('.event-target')).toBeNull();
    expect(element.querySelector('.target-player')).toBeNull();

    playButtons(element)[2].click();
    await fixture.whenStable();

    httpTesting.expectNone({ method: 'POST', url: `/api/matches/${MATCH_ID}/play` });
    expect(text(element.querySelector('.choose-target'))).toBe('Wybierz cel wydarzenia');
    expect(element.querySelector('.player .card.selected .card-name')?.textContent).toBe('Hotfix');
    const own = Array.from(element.querySelectorAll<HTMLButtonElement>('.player .event-target'));
    const enemy = Array.from(
      element.querySelectorAll<HTMLButtonElement>('.opponent .event-target'),
    );
    expect(own.map((b) => b.disabled)).toEqual([false]);
    expect(enemy.map((b) => b.disabled)).toEqual([true, false]);
    expect(text(enemy[0])).toBe('Cel');
    const opponentHp = button(element, '.opponent .target-player');
    const playerHp = button(element, '.player .target-player');
    expect(text(opponentHp)).toBe('Cel: HP przeciwnika');
    expect(text(playerHp)).toBe('Cel: Twoje HP');
    expect(opponentHp.disabled).toBe(false);
    expect(playerHp.disabled).toBe(true);
    // Attack targets are not offered in target mode.
    expect(element.querySelector('.opponent .target')).toBeNull();

    enemy[1].click();
    await fixture.whenStable();

    const request = httpTesting.expectOne({ method: 'POST', url: `/api/matches/${MATCH_ID}/play` });
    expect(request.request.body).toEqual({
      turn: 5,
      instanceId: 3,
      target: { kind: 'CHARACTER', instanceId: 21 },
    });
    request.flush(boardView());
    await fixture.whenStable();
    expect(element.querySelector('.event-target')).toBeNull();
    expect(element.querySelector('.cancel-target')).toBeNull();
  });

  it('plays an event on a player target with kind and side', async () => {
    const fixture = await renderFixture(targetView());
    const element = fixture.nativeElement as HTMLElement;

    playButtons(element)[2].click();
    await fixture.whenStable();
    button(element, '.opponent .target-player').click();
    await fixture.whenStable();

    const request = httpTesting.expectOne({ method: 'POST', url: `/api/matches/${MATCH_ID}/play` });
    expect(request.request.body).toEqual({
      turn: 5,
      instanceId: 3,
      target: { kind: 'PLAYER', side: 'OPPONENT' },
    });
    request.flush(boardView());
    await fixture.whenStable();
  });

  it('leaves target mode on "Anuluj" and when an attacker is chosen', async () => {
    const fixture = await renderFixture(targetView());
    const element = fixture.nativeElement as HTMLElement;

    playButtons(element)[2].click();
    await fixture.whenStable();
    const cancel = button(element, '.cancel-target');
    expect(text(cancel)).toBe('Anuluj');
    cancel.click();
    await fixture.whenStable();
    expect(element.querySelector('.event-target')).toBeNull();
    expect(element.querySelector('.choose-target')).toBeNull();

    playButtons(element)[2].click();
    await fixture.whenStable();
    button(element, '.player .character button.attack').click();
    await fixture.whenStable();
    expect(element.querySelector('.event-target')).toBeNull();
    expect(element.querySelectorAll('.opponent .target').length).toBe(2);

    playButtons(element)[2].click();
    await fixture.whenStable();
    expect(element.querySelector('.opponent .target')).toBeNull();
    expect(element.querySelector('.cancel-attack')).toBeNull();
    expect(element.querySelectorAll('.event-target').length).toBe(3);
  });

  it('disables "Zagraj", "Cel" and "Anuluj" while an event request is in flight', async () => {
    const fixture = await renderFixture(targetView());
    const element = fixture.nativeElement as HTMLElement;

    playButtons(element)[2].click();
    await fixture.whenStable();
    button(element, '.opponent .target-player').click();
    await fixture.whenStable();

    expect(playButtons(element).every((b) => b.disabled)).toBe(true);
    const targets = Array.from(element.querySelectorAll<HTMLButtonElement>('.event-target'));
    expect(targets.length).toBe(3);
    expect(targets.every((b) => b.disabled)).toBe(true);
    expect(button(element, '.opponent .target-player').disabled).toBe(true);
    expect(button(element, '.cancel-target').disabled).toBe(true);

    httpTesting
      .expectOne({ method: 'POST', url: `/api/matches/${MATCH_ID}/play` })
      .flush(boardView());
    await fixture.whenStable();
    expect(playButtons(element)[0].disabled).toBe(false);
  });

  it('shows effective stats, trapping and keywords given by events', async () => {
    const match = boardView();
    const element = await renderWithMatch({
      ...match,
      player: {
        ...match.player,
        board: [
          character(10, 'Tech Lead', {
            attack: 6,
            baseAttack: 4,
            health: 5,
            baseHealth: 5,
            keywords: ['RUSH'],
          }),
          character(11, 'Nowy', { canAttack: false }),
        ],
      },
      opponent: {
        ...match.opponent,
        board: [
          character(20, 'QA Junior', {
            canAttack: false,
            trapped: true,
            ability: GUARD,
            keywords: ['GUARD'],
          }),
        ],
      },
    });

    const own = element.querySelectorAll('.player .character');
    expect(text(own[0].querySelector('.character-stats'))).toBe('Atak 6 / HP 5');
    expect(own[0].querySelector('.character-stats')?.classList).toContain('modified');
    expect(Array.from(own[0].querySelectorAll('.character-keyword')).map(text)).toEqual(['Szarża']);
    expect(own[0].querySelector('.character-trapped')).toBeNull();
    expect(own[1].querySelector('.character-stats')?.classList).not.toContain('modified');
    const trapped = element.querySelector('.opponent .character')!;
    expect(text(trapped.querySelector('.character-trapped'))).toBe('Uwięziona');
    // The own Guard ability is shown as its text, not as a given keyword.
    expect(trapped.querySelector('.character-keyword')).toBeNull();
  });

  it('shows the last played event of each side', async () => {
    const match = boardView();
    const element = await renderWithMatch({
      ...match,
      player: { ...match.player, lastEvent: HOTFIX_PLAYED },
    });

    const player = element.querySelector('.player')!;
    const opponent = element.querySelector('.opponent')!;
    expect(text(player.querySelector('.last-played-heading'))).toBe('Ostatnio zagrane');
    expect(text(player.querySelector('.last-played-name'))).toBe('Hotfix');
    expect(text(player.querySelector('.last-played .effect'))).toBe('Wybrana postać: +2 ataku');
    expect(text(opponent.querySelector('.last-played'))).toBe('Brak zagranych wydarzeń');
  });

  it('renders the event texts in English', async () => {
    TestBed.inject(LanguageService).setLanguage('en');
    const match = targetView();
    const fixture = await renderFixture({
      ...match,
      opponent: {
        ...match.opponent,
        board: [character(20, 'QA Junior', { trapped: true, keywords: ['GUARD'] })],
      },
    });
    const element = fixture.nativeElement as HTMLElement;

    const cards = Array.from(element.querySelectorAll('.player .card'));
    expect(text(cards[3].querySelector('.card-duration'))).toBe('Instant');
    expect(text(element.querySelector('.player .last-played-heading'))).toBe('Last played');
    expect(text(element.querySelector('.player .last-played'))).toBe('No events played');
    const opponentCharacter = element.querySelector('.opponent .character')!;
    expect(text(opponentCharacter.querySelector('.character-trapped'))).toBe('Trapped');
    expect(text(opponentCharacter.querySelector('.character-keyword'))).toBe('Guard');

    playButtons(element)[2].click();
    await fixture.whenStable();
    expect(text(element.querySelector('.choose-target'))).toBe('Choose the event target');
    expect(text(element.querySelector('.cancel-target'))).toBe('Cancel');
    expect(text(element.querySelector('.opponent .target-player'))).toBe("Target: opponent's HP");
    expect(text(element.querySelector('.player .target-player'))).toBe('Target: your HP');
    expect(text(element.querySelector('.event-target'))).toBe('Target');
  });

  it('offers "Atakuj" only for characters that can attack', async () => {
    const match = boardView();
    const element = await renderWithMatch({
      ...match,
      player: {
        ...match.player,
        board: [character(10, 'Tech Lead'), character(11, 'Nowy', { canAttack: false })],
      },
    });

    const characters = element.querySelectorAll('.player .character');
    expect(characters[0].querySelector('button.attack')).not.toBeNull();
    expect(characters[1].querySelector('button.attack')).toBeNull();
  });

  it('ends the turn with POST end-turn', async () => {
    const fixture = await renderFixture(matchView());
    const element = fixture.nativeElement as HTMLElement;

    const endTurn = button(element, '.end-turn');
    expect(text(endTurn)).toBe('Zakończ turę');
    endTurn.click();
    await fixture.whenStable();

    const request = httpTesting.expectOne({
      method: 'POST',
      url: `/api/matches/${MATCH_ID}/end-turn`,
    });
    expect(request.request.body).toEqual({ turn: 1 });
    request.flush({ ...matchView(), turn: { number: 2, active: 'PLAYER' } });
    await fixture.whenStable();

    expect(text(element.querySelector('.turn'))).toBe('Tura 2 — Twoja tura');
  });

  it('refetches the current match without a message on 409', async () => {
    const fixture = await renderFixture(matchView());
    const element = fixture.nativeElement as HTMLElement;

    button(element, '.end-turn').click();
    await fixture.whenStable();
    httpTesting
      .expectOne({ method: 'POST', url: `/api/matches/${MATCH_ID}/end-turn` })
      .flush({ message: 'stale' }, { status: 409, statusText: 'Conflict' });
    await fixture.whenStable();

    httpTesting
      .expectOne({ method: 'GET', url: '/api/matches/current' })
      .flush({ ...matchView(), turn: { number: 3, active: 'PLAYER' } });
    await fixture.whenStable();

    expect(text(element.querySelector('.turn'))).toBe('Tura 3 — Twoja tura');
    expect(element.querySelector('.message')).toBeNull();
  });

  it('sends the player to /new-match when a move gets 404 (match gone)', async () => {
    const fixture = await renderFixture(matchView());
    const element = fixture.nativeElement as HTMLElement;
    const navigateByUrl = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);

    button(element, '.end-turn').click();
    await fixture.whenStable();
    httpTesting
      .expectOne({ method: 'POST', url: `/api/matches/${MATCH_ID}/end-turn` })
      .flush(null, { status: 404, statusText: 'Not Found' });
    await fixture.whenStable();

    httpTesting
      .expectOne({ method: 'GET', url: '/api/matches/current' })
      .flush(null, { status: 404, statusText: 'Not Found' });
    await fixture.whenStable();

    expect(navigateByUrl).toHaveBeenCalledWith('/new-match');
    expect(element.querySelector('.message')).toBeNull();
  });

  it('shows "moveRejected" on 400 and keeps the table', async () => {
    const fixture = await renderFixture(matchView());
    const element = fixture.nativeElement as HTMLElement;

    playButtons(element)[0].click();
    await fixture.whenStable();
    httpTesting
      .expectOne({ method: 'POST', url: `/api/matches/${MATCH_ID}/play` })
      .flush({ message: 'Za mało budżetu' }, { status: 400, statusText: 'Bad Request' });
    await fixture.whenStable();

    expect(text(element.querySelector('.message'))).toBe('Ten ruch jest niedozwolony.');
    expect(element.querySelectorAll('.player .card').length).toBe(5);
  });

  it('shows "moveFailed" on a server error', async () => {
    const fixture = await renderFixture(matchView());
    const element = fixture.nativeElement as HTMLElement;

    button(element, '.end-turn').click();
    await fixture.whenStable();
    httpTesting
      .expectOne({ method: 'POST', url: `/api/matches/${MATCH_ID}/end-turn` })
      .flush('Server Error', { status: 500, statusText: 'Internal Server Error' });
    await fixture.whenStable();

    expect(text(element.querySelector('.message'))).toBe(
      'Nie udało się wykonać ruchu — spróbuj ponownie.',
    );
  });

  it('disables every move button while a request is in flight', async () => {
    const fixture = await renderFixture(boardView());
    const element = fixture.nativeElement as HTMLElement;

    button(element, '.end-turn').click();
    await fixture.whenStable();

    const buttons = Array.from(element.querySelectorAll<HTMLButtonElement>('button'));
    expect(buttons.length).toBeGreaterThan(0);
    expect(buttons.every((candidate) => candidate.disabled)).toBe(true);

    httpTesting
      .expectOne({ method: 'POST', url: `/api/matches/${MATCH_ID}/end-turn` })
      .flush(boardView());
    await fixture.whenStable();

    expect(button(element, '.end-turn').disabled).toBe(false);
    expect(playButtons(element)[0].disabled).toBe(false);
  });

  describe('playback of the computer turn', () => {
    function advance(fixture: ComponentFixture<MatchTable>, ms: number): void {
      vi.advanceTimersByTime(ms);
      fixture.detectChanges();
    }

    function logTexts(element: HTMLElement): string[] {
      return Array.from(element.querySelectorAll('.log .log-entry')).map(text);
    }

    function attackButton(element: HTMLElement): HTMLButtonElement {
      return button(element, '.player .character button.attack');
    }

    /** Renders boardView() with real timers, then installs the fake clock for the playback. */
    async function renderBoardThenFakeClock(): Promise<ComponentFixture<MatchTable>> {
      const fixture = await renderFixture(boardView());
      vi.useFakeTimers();
      return fixture;
    }

    function flushEndTurn(): void {
      httpTesting
        .expectOne({ method: 'POST', url: `/api/matches/${MATCH_ID}/end-turn` })
        .flush(endTurnResponse());
    }

    it('sends exactly one end-turn request on a quick double click', async () => {
      const fixture = await renderBoardThenFakeClock();
      const element = fixture.nativeElement as HTMLElement;

      const endTurn = button(element, '.end-turn');
      endTurn.click();
      flushEndTurn();
      // The second click lands before the disabled state is rendered.
      endTurn.click();
      fixture.detectChanges();
      expect(element.querySelector('.end-turn')).toBeNull();

      advance(fixture, STEP_MS * 2);
      expect(button(element, '.end-turn').disabled).toBe(false);
    });

    it('shows the steps one by one and enables the buttons after the last one', async () => {
      const fixture = await renderBoardThenFakeClock();
      const element = fixture.nativeElement as HTMLElement;

      button(element, '.end-turn').click();
      flushEndTurn();
      fixture.detectChanges();

      expect(text(element.querySelector('.opponent-moving'))).toBe('Ruch przeciwnika…');
      expect(element.querySelector('.end-turn')).toBeNull();
      expect(text(element.querySelector('.turn'))).toBe('Tura 6 — tura przeciwnika');
      expect(text(element.querySelector('.player .hp dd'))).toBe('30/30');
      expect(logTexts(element)).toEqual(LOG.slice(0, 2).map((entry) => entry.text));
      expect(playButtons(element).every((candidate) => candidate.disabled)).toBe(true);
      expect(attackButton(element).disabled).toBe(true);

      advance(fixture, STEP_MS - 1);
      expect(logTexts(element).length).toBe(2);
      advance(fixture, 1);
      expect(text(element.querySelector('.player .hp dd'))).toBe('26/30');
      expect(logTexts(element)).toEqual(LOG.slice(0, 3).map((entry) => entry.text));
      expect(element.querySelectorAll('.opponent .board .character').length).toBe(3);
      expect(text(element.querySelector('.opponent-moving'))).toBe('Ruch przeciwnika…');

      advance(fixture, STEP_MS);
      expect(element.querySelector('.opponent-moving')).toBeNull();
      expect(text(element.querySelector('.turn'))).toBe('Tura 7 — Twoja tura');
      expect(logTexts(element)).toEqual(LOG.map((entry) => entry.text));
      expect(button(element, '.end-turn').disabled).toBe(false);
      expect(playButtons(element)[0].disabled).toBe(false);
      expect(attackButton(element).disabled).toBe(false);
    });

    it("shows the opponent's last played event in the playback steps", async () => {
      const fixture = await renderBoardThenFakeClock();
      const element = fixture.nativeElement as HTMLElement;

      button(element, '.end-turn').click();
      flushEndTurn();
      fixture.detectChanges();
      expect(text(element.querySelector('.opponent .last-played'))).toBe('Brak zagranych wydarzeń');

      advance(fixture, STEP_MS);
      expect(text(element.querySelector('.opponent .last-played-name'))).toBe('Code Review');
      expect(text(element.querySelector('.opponent .last-played .effect'))).toBe(
        'Zadaj przeciwnikowi 4 obrażenia',
      );

      advance(fixture, STEP_MS);
      expect(element.querySelector('.opponent-moving')).toBeNull();
      expect(text(element.querySelector('.opponent .last-played-name'))).toBe('Code Review');
    });

    it("captions each playback step with the computer's reason and drops it afterwards", async () => {
      const fixture = await renderBoardThenFakeClock();
      const element = fixture.nativeElement as HTMLElement;

      button(element, '.end-turn').click();
      httpTesting
        .expectOne({ method: 'POST', url: `/api/matches/${MATCH_ID}/end-turn` })
        .flush(reasonResponse());
      fixture.detectChanges();

      const caption = element.querySelector('.move-reason');
      expect(caption?.getAttribute('role')).toBe('status');
      expect(text(caption)).toBe('Dlaczego: Wystawia postać, bo stół jest pusty');
      const reasonEntries = element.querySelectorAll('.log .log-entry.reason');
      expect(Array.from(reasonEntries).map(text)).toEqual(['Wystawia postać, bo stół jest pusty']);

      advance(fixture, STEP_MS);
      expect(text(element.querySelector('.move-reason'))).toBe(
        'Dlaczego: Atakuje gracza, bo może wygrać',
      );

      advance(fixture, STEP_MS);
      expect(element.querySelector('.opponent-moving')).toBeNull();
      expect(element.querySelector('.move-reason')).toBeNull();
      expect(element.querySelectorAll('.log .log-entry.reason').length).toBe(2);
      expect(element.querySelectorAll('.log .log-entry:not(.reason)').length).toBe(5);
    });

    it('refetches the match only after playback when the language changes during it', async () => {
      const fixture = await renderBoardThenFakeClock();
      const element = fixture.nativeElement as HTMLElement;

      button(element, '.end-turn').click();
      flushEndTurn();
      fixture.detectChanges();

      TestBed.inject(LanguageService).setLanguage('en');
      fixture.detectChanges();
      httpTesting.expectNone({ method: 'GET', url: '/api/matches/current' });
      expect(text(element.querySelector('.opponent-moving'))).toBe('Opponent is moving…');

      advance(fixture, STEP_MS);
      httpTesting.expectNone({ method: 'GET', url: '/api/matches/current' });
      expect(logTexts(element).length).toBe(3);

      advance(fixture, STEP_MS);
      httpTesting.expectOne({ method: 'GET', url: '/api/matches/current' }).flush({
        ...endTurnResponse(),
        replay: [],
        log: [{ side: 'PLAYER', text: 'You end your turn', reason: false }],
      });
      fixture.detectChanges();

      expect(text(element.querySelector('.turn'))).toBe('Turn 7 — your turn');
      expect(logTexts(element)).toEqual(['You end your turn']);
    });

    it('plays back the start response handed over by new-match without GET /current', () => {
      TestBed.inject(GameService).setStartedMatch(endTurnResponse());
      vi.useFakeTimers();

      const fixture = TestBed.createComponent(MatchTable);
      fixture.detectChanges();
      const element = fixture.nativeElement as HTMLElement;
      httpTesting.expectNone({ method: 'GET', url: '/api/matches/current' });
      expect(text(element.querySelector('.opponent-moving'))).toBe('Ruch przeciwnika…');
      expect(logTexts(element).length).toBe(2);

      advance(fixture, STEP_MS * 2);
      expect(element.querySelector('.opponent-moving')).toBeNull();
      expect(text(element.querySelector('.turn'))).toBe('Tura 7 — Twoja tura');
      expect(TestBed.inject(GameService).takeStartedMatch()).toBeNull();
    });
  });

  it('shows the win with its reason and a link to /new-match, without move buttons', async () => {
    const element = await renderWithMatch(finishedView('PLAYER', 'HP_DEPLETED'));

    const result = element.querySelector('.result')!;
    expect(text(result.querySelector('h2'))).toBe('Wygrana');
    expect(text(result.querySelector('.result-reason'))).toBe(
      'Przyczyna: HP spadło do 0 lub niżej',
    );
    const link = result.querySelector('a.play-again');
    expect(text(link)).toBe('Zagraj ponownie');
    expect(link?.getAttribute('href')).toBe('/new-match');
    expect(element.querySelector('button')).toBeNull();
  });

  it('shows the loss and the real HP below zero', async () => {
    const element = await renderWithMatch(finishedView('OPPONENT', 'EMPTY_DECK'));

    expect(text(element.querySelector('.result h2'))).toBe('Przegrana');
    expect(text(element.querySelector('.result-reason'))).toBe('Przyczyna: pusta talia');
    expect(text(element.querySelector('.player .hp dd'))).toBe('-3/30');
  });

  it('shows the result in English', async () => {
    TestBed.inject(LanguageService).setLanguage('en');

    const element = await renderWithMatch(finishedView('OPPONENT', 'HP_DEPLETED'));

    expect(text(element.querySelector('.result h2'))).toBe('Defeat');
    expect(text(element.querySelector('.result-reason'))).toBe('Reason: HP dropped to 0 or below');
    expect(text(element.querySelector('.play-again'))).toBe('Play again');
  });

  it('renders log entries in chronological order', async () => {
    const element = await renderWithMatch({
      ...matchView(),
      log: [
        { side: 'PLAYER', text: 'Zagrywasz: Junior Dev', reason: false },
        { side: 'OPPONENT', text: 'Przeciwnik zagrywa: Tech Lead', reason: false },
        { side: 'OPPONENT', text: 'Tech Lead atakuje Cię i zadaje 8 obrażeń', reason: false },
      ],
    });

    const entries = Array.from(element.querySelectorAll('.log .log-entry')).map(text);
    expect(entries).toEqual([
      'Zagrywasz: Junior Dev',
      'Przeciwnik zagrywa: Tech Lead',
      'Tech Lead atakuje Cię i zadaje 8 obrażeń',
    ]);
    expect(text(element.querySelector('.log h2'))).toBe('Przebieg partii');
  });
});

const LOG: LogEntry[] = [
  { side: 'PLAYER', text: 'Kończysz turę', reason: false },
  { side: 'OPPONENT', text: 'Tura przeciwnika 6', reason: false },
  { side: 'OPPONENT', text: 'Przeciwnik zagrywa: QA Senior', reason: false },
  { side: 'OPPONENT', text: 'Przeciwnik kończy turę', reason: false },
  { side: 'PLAYER', text: 'Twoja tura 7', reason: false },
];

/**
 * End turn from boardView(): step 1 is the state after the player's end of turn, step 2 the
 * opponent's play (4 damage to the player), step 3 the final state after the opponent ends its turn.
 */
function endTurnResponse(): MatchView {
  const before = boardView();
  const opponentTurn = { number: 6, active: 'OPPONENT' } as const;
  const opponent = {
    ...before.opponent,
    board: [...before.opponent.board, character(22, 'QA Senior', { canAttack: false })],
    lastEvent: {
      instanceId: 40,
      name: 'Code Review',
      effects: [
        {
          kind: 'PLAYER_DAMAGE',
          scope: 'OPPONENT',
          amount: 4,
          text: 'Zadaj przeciwnikowi 4 obrażenia',
        },
      ],
    } satisfies PlayedEvent,
  };
  const player = { ...before.player, hp: 26 };
  const step = (overrides: Partial<ReplayStep>): ReplayStep => ({
    turn: opponentTurn,
    status: 'IN_PROGRESS',
    result: null,
    player: before.player,
    opponent: before.opponent,
    logSize: 0,
    ...overrides,
  });
  return {
    ...before,
    turn: { number: 7, active: 'PLAYER' },
    player,
    opponent,
    log: LOG,
    replay: [
      step({ logSize: 2 }),
      step({ player, opponent, logSize: 3 }),
      step({ turn: { number: 7, active: 'PLAYER' }, player, opponent, logSize: 5 }),
    ],
  };
}

/**
 * endTurnResponse() with a reason before each computer move: step 1 shows the first reason,
 * step 2 the second, the final state ends the playback.
 */
function reasonResponse(): MatchView {
  const response = endTurnResponse();
  const log: LogEntry[] = [
    { side: 'PLAYER', text: 'Kończysz turę', reason: false },
    { side: 'OPPONENT', text: 'Tura przeciwnika 6', reason: false },
    { side: 'OPPONENT', text: 'Wystawia postać, bo stół jest pusty', reason: true },
    { side: 'OPPONENT', text: 'Przeciwnik zagrywa: QA Senior', reason: false },
    { side: 'OPPONENT', text: 'Atakuje gracza, bo może wygrać', reason: true },
    { side: 'OPPONENT', text: 'QA Senior atakuje Cię i zadaje 4 obrażenia', reason: false },
    { side: 'PLAYER', text: 'Twoja tura 7', reason: false },
  ];
  const [first, second, last] = response.replay;
  return {
    ...response,
    log,
    replay: [
      { ...first, logSize: 4 },
      { ...second, logSize: 6 },
      { ...last, logSize: 7 },
    ],
  };
}

function matchViewEn(): MatchView {
  const match = matchView();
  return {
    ...match,
    player: {
      ...match.player,
      faction: { id: 'developers', name: 'Developers' },
      hand: match.player.hand.map((card, index) =>
        index === 0 ? { ...card, text: 'Writes code.' } : card,
      ),
    },
    opponent: { ...match.opponent, faction: { id: 'testers', name: 'Testers' } },
  };
}
