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
    faction: 'developers',
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
    faction: 'developers',
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
    faction: 'developers',
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
    faction: 'neutral',
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
    faction: 'neutral',
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
    cost: 2,
    faction: 'developers',
    ...overrides,
  };
}

function matchView(active: Side = 'PLAYER'): MatchView {
  return {
    matchId: MATCH_ID,
    status: 'IN_PROGRESS',
    turn: { number: 1, active, timeLimitSeconds: null },
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
      graveyard: [],
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
      graveyard: [],
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
    turn: { number: 5, active: 'PLAYER', timeLimitSeconds: null },
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

  /** The hand `<li>` items (the Card's own `article.card` sits inside them). */
  function handCards(element: HTMLElement): Element[] {
    return Array.from(element.querySelectorAll('.hand > li.card'));
  }

  /** The card button inside a board or hand `<li>`. */
  function cardButton(item: Element): HTMLButtonElement {
    return button(item, '.card-action');
  }

  function playButtons(element: HTMLElement): HTMLButtonElement[] {
    return handCards(element).map(cardButton);
  }

  function characterButtons(
    element: HTMLElement,
    side: 'player' | 'opponent',
  ): HTMLButtonElement[] {
    return Array.from(element.querySelectorAll(`.zone.${side} .board .character`)).map(cardButton);
  }

  /** The target button of a status panel; `null` while that player is no legal target. */
  function statusTarget(
    element: HTMLElement,
    side: 'player' | 'opponent',
  ): HTMLButtonElement | null {
    return element.querySelector<HTMLButtonElement>(`.status.${side} .status-target`);
  }

  /** Move buttons only: the language switch in the aside is no move. */
  function moveButtons(element: HTMLElement): HTMLButtonElement[] {
    return Array.from(element.querySelectorAll<HTMLButtonElement>('button')).filter(
      (candidate) => candidate.closest('app-language-switch') === null,
    );
  }

  function label(element: Element | null): string | null {
    return element?.getAttribute('aria-label') ?? null;
  }

  /** The card name on the enlarged hover card, `null` while none is shown. */
  function hoverCard(element: HTMLElement): string | null {
    const card = element.querySelector('app-hover-card app-card');
    return card === null ? null : text(card.querySelector('.name'));
  }

  async function hover(fixture: ComponentFixture<MatchTable>, item: Element, over = true) {
    item.dispatchEvent(new MouseEvent(over ? 'mouseenter' : 'mouseleave'));
    await fixture.whenStable();
  }

  it('renders the five hand cards as compact cards with name, cost, compact line and art', async () => {
    const element = await renderWithMatch();

    const cards = handCards(element);
    expect(cards.length).toBe(5);
    expect(cards[0].querySelector('app-card')?.classList).toContain('compact');
    expect(text(cards[0].querySelector('.name'))).toBe('Junior Dev');
    expect(label(cards[0].querySelector('.cost'))).toBe('Koszt 1');
    expect(text(cards[0].querySelector('.line'))).toBe('Postać');
    expect(cards[0].querySelector('img')?.getAttribute('src')).toBe('/media/cards/dev-1.webp');
    expect(text(cards[2].querySelector('.name'))).toBe('Hotfix');
    expect(label(cards[2].querySelector('.cost'))).toBe('Koszt 2');
    expect(text(cards[2].querySelector('.line'))).toBe('2 tury');
  });

  it('shows the HP of both sides, opponent card counts and budgets', async () => {
    const element = await renderWithMatch();

    const opponent = element.querySelector('.status.opponent')!;
    const player = element.querySelector('.status.player')!;
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
    expect(player.querySelectorAll('.pip').length).toBe(1);
    expect(player.querySelector('.hp-bar')).not.toBeNull();
  });

  it('gives neutral hand cards the neutral faction look', async () => {
    const element = await renderWithMatch();

    const neutralNames = handCards(element)
      .filter((card) => card.querySelector('app-card')?.getAttribute('data-faction') === 'neutral')
      .map((card) => text(card.querySelector('.name')));
    expect(neutralNames).toEqual(['Kawa', 'Stażysta']);
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

  it('links back to the lobby from the bottom of the aside', async () => {
    const element = await renderWithMatch();

    const link = element.querySelector('.aside-footer a.exit');
    expect(text(link)).toBe('Wyjdź do menu');
    expect(link?.getAttribute('href')).toBe('/');
    expect(element.querySelector('.aside-footer app-language-switch')).not.toBeNull();
  });

  it('renders in English when English is chosen', async () => {
    TestBed.inject(LanguageService).setLanguage('en');

    const element = await renderWithMatch(matchViewEn());

    expect(text(element.querySelector('.turn'))).toBe('Turn 1 — your turn');
    expect(element.querySelector('.opponent')?.getAttribute('aria-label')).toBe('Opponent');
    expect(element.querySelector('.player')?.getAttribute('aria-label')).toBe('You');
    expect(text(element.querySelector('.opponent h2'))).toBe('Opponent: Testers');
    expect(text(element.querySelector('.player h2'))).toBe('You: Developers');
    expect(text(element.querySelector('.status.opponent .hp dt'))).toBe('HP');
    expect(text(element.querySelector('.status.opponent .budget dt'))).toBe('Budget');
    expect(text(element.querySelector('.status.opponent .hand-count dt'))).toBe('Cards in hand');
    expect(text(element.querySelector('.status.opponent .deck-count dt'))).toBe('Cards in deck');
    expect(text(element.querySelector('.hand-heading'))).toBe('Your hand');
    const cards = handCards(element);
    expect(label(cards[0].querySelector('.cost'))).toBe('Cost 1');
    expect(text(cards[0].querySelector('.line'))).toBe('Character');
    expect(text(cards[2].querySelector('.line'))).toBe('2 turns');
    expect(label(playButtons(element)[0])).toBe('Play: Junior Dev');
    expect(text(element.querySelector('.end-turn'))).toBe('End turn');
    expect(text(element.querySelector('a.exit'))).toBe('Exit to menu');
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
    const cards = handCards(element);
    expect(cards.length).toBe(5);
    expect(text(cards[0].querySelector('.line'))).toBe('Character');
    await hover(fixture, cards[0]);
    expect(text(element.querySelector('app-hover-card .text'))).toBe('Writes code.');
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
    expect(handCards(element).length).toBe(5);

    TestBed.inject(LanguageService).setLanguage('en');
    await fixture.whenStable();
    httpTesting
      .expectOne({ method: 'GET', url: '/api/matches/current' })
      .flush('Server Error', { status: 500, statusText: 'Internal Server Error' });
    await fixture.whenStable();

    expect(handCards(element).length).toBe(0);
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
    expect(text(board[0].querySelector('.name'))).toBe('Junior Dev');
    expect(text(board[0].querySelector('.stat.attack'))).toBe('1');
    expect(text(board[0].querySelector('.stat.hp'))).toBe('2');
    expect(element.querySelectorAll('.player .board .slot').length).toBe(4);
    expect(handCards(element).length).toBe(4);
    expect(text(element.querySelector('.status.player .budget dd'))).toBe('0/1');
  });

  it('disables "Zagraj" for a card that costs too much', async () => {
    const element = await renderWithMatch();

    const buttons = playButtons(element);
    expect(label(buttons[0])).toBe('Zagraj: Junior Dev');
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

  it('shows stats and event duration on the hand cards, ability and effects in the preview', async () => {
    const fixture = await renderFixture(matchView());
    const element = fixture.nativeElement as HTMLElement;

    const cards = handCards(element);
    expect(text(cards[0].querySelector('.stat.attack'))).toBe('1');
    expect(text(cards[0].querySelector('.stat.hp'))).toBe('2');
    expect(text(cards[2].querySelector('.line'))).toBe('2 tury');
    expect(cards[2].querySelector('.stat')).toBeNull();
    expect(text(cards[3].querySelector('.line'))).toBe('Natychmiast');
    expect(text(cards[4].querySelector('.line'))).toBe('Ochrona');

    await hover(fixture, cards[2]);
    expect(Array.from(element.querySelectorAll('app-hover-card .effect')).map(text)).toEqual([
      'Wybrana postać: +2 ataku',
    ]);
    await hover(fixture, cards[3]);
    expect(Array.from(element.querySelectorAll('app-hover-card .effect')).map(text)).toEqual([
      'Dodaj 2 do budżetu w tej turze',
    ]);
    await hover(fixture, cards[0]);
    expect(element.querySelector('app-hover-card .effect')).toBeNull();
    await hover(fixture, cards[4]);
    expect(text(element.querySelector('app-hover-card .ability'))).toBe(
      'Ochrona: musi zostać zaatakowana pierwsza',
    );
  });

  it('renders boards with remaining HP, active events, graveyard and negative player HP', async () => {
    const match = boardView();
    const fixture = await renderFixture({
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
            cost: 3,
            faction: 'neutral',
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
            cost: 4,
            faction: 'testers',
          },
        ],
      },
    });

    const element = fixture.nativeElement as HTMLElement;
    const player = element.querySelector('.zone.player')!;
    const opponent = element.querySelector('.zone.opponent')!;
    expect(text(element.querySelector('.status.player .hp dd'))).toBe('-3/30');
    expect(text(player.querySelector('.character .stat.attack'))).toBe('4');
    expect(text(player.querySelector('.character .stat.hp'))).toBe('3');
    expect(text(element.querySelector('.status.player .graveyard dd'))).toBe('2');
    expect(text(element.querySelector('.status.opponent .graveyard dd'))).toBe('4');
    expect(text(player.querySelector('.event-name'))).toBe('Sprint');
    expect(text(player.querySelector('.event-turns'))).toBe('Pozostało tur: 2');
    expect(text(opponent.querySelector('.event-name'))).toBe('Code Freeze');
    expect(text(opponent.querySelector('.event-turns'))).toBe('Pozostało tur: 1');
    expect(text(player.querySelector('.event-target-name'))).toBe('Cel: Tech Lead');
    expect(opponent.querySelector('.event-target-name')).toBeNull();
    expect(text(player.querySelector('.events-heading'))).toBe('Aktywne wydarzenia');
    expect(player.querySelectorAll('.board .slot').length).toBe(4);
    expect(opponent.querySelectorAll('.board .slot').length).toBe(3);
    expect(player.getAttribute('data-faction')).toBe('developers');
    expect(opponent.getAttribute('data-faction')).toBe('testers');

    // The chips carry no effect text; the hover card shows the full event card.
    await hover(fixture, player.querySelector('.event')!);
    expect(hoverCard(element)).toBe('Sprint');
    expect(text(element.querySelector('app-hover-card .effect'))).toBe('Wybrana postać: +2 ataku');
    await hover(fixture, opponent.querySelector('.event')!);
    expect(text(element.querySelector('app-hover-card .effect'))).toBe('Własne postacie: Szarża');
  });

  it('attacks the opponent player after choosing an attacker', async () => {
    const fixture = await renderFixture(boardView());
    const element = fixture.nativeElement as HTMLElement;
    expect(statusTarget(element, 'opponent')).toBeNull();
    expect(characterButtons(element, 'opponent').every((b) => b.disabled)).toBe(true);

    const attacker = characterButtons(element, 'player')[0];
    expect(label(attacker)).toBe('Wybierz do ataku: Tech Lead');
    attacker.click();
    await fixture.whenStable();

    const attackPlayer = statusTarget(element, 'opponent')!;
    expect(label(attackPlayer)).toBe('Atakuj: Przeciwnik');
    expect(attackPlayer.disabled).toBe(false);
    const targets = characterButtons(element, 'opponent');
    expect(targets.map((b) => b.disabled)).toEqual([false, false]);
    expect(label(targets[0])).toBe('Atakuj: QA Junior');
    attackPlayer.click();
    await fixture.whenStable();

    const request = httpTesting.expectOne({
      method: 'POST',
      url: `/api/matches/${MATCH_ID}/attack`,
    });
    expect(request.request.body).toEqual({ turn: 5, attackerId: 10, targetId: null });
    request.flush(boardView());
    await fixture.whenStable();

    expect(statusTarget(element, 'opponent')).toBeNull();
  });

  it('attacks a character target and allows cancelling the selection', async () => {
    const fixture = await renderFixture(boardView());
    const element = fixture.nativeElement as HTMLElement;

    characterButtons(element, 'player')[0].click();
    await fixture.whenStable();
    button(element, '.cancel-attack').click();
    await fixture.whenStable();
    expect(characterButtons(element, 'opponent').every((b) => b.disabled)).toBe(true);

    characterButtons(element, 'player')[0].click();
    await fixture.whenStable();
    const targets = characterButtons(element, 'opponent');
    expect(label(targets[1])).toBe('Atakuj: QA Lead');
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

  it('selects an attacker by clicking it and cancels the selection by clicking it again', async () => {
    const fixture = await renderFixture(boardView());
    const element = fixture.nativeElement as HTMLElement;
    const own = () => element.querySelector('.player .board .character')!;

    expect(cardButton(own()).getAttribute('aria-pressed')).toBe('false');
    cardButton(own()).click();
    await fixture.whenStable();
    expect(cardButton(own()).getAttribute('aria-pressed')).toBe('true');
    expect(own().classList).toContain('selected');
    expect(own().querySelector('article.card.selected')).not.toBeNull();
    expect(element.querySelector('.cancel-attack')).not.toBeNull();

    cardButton(own()).click();
    await fixture.whenStable();
    expect(cardButton(own()).getAttribute('aria-pressed')).toBe('false');
    expect(own().classList).not.toContain('selected');
    expect(element.querySelector('.cancel-attack')).toBeNull();
    expect(statusTarget(element, 'opponent')).toBeNull();
    httpTesting.expectNone({ method: 'POST', url: `/api/matches/${MATCH_ID}/attack` });
  });

  it('renders a card with no legal action as a disabled button named after the card', async () => {
    const fixture = await renderFixture(boardView());
    const element = fixture.nativeElement as HTMLElement;

    const enemy = characterButtons(element, 'opponent')[0];
    expect(enemy.disabled).toBe(true);
    expect(label(enemy)).toBe('QA Junior');
    expect(enemy.hasAttribute('aria-pressed')).toBe(false);
    enemy.click();
    await fixture.whenStable();
    httpTesting.expectNone({ method: 'POST', url: `/api/matches/${MATCH_ID}/attack` });

    // boardView() has budget 3: "Stażysta" (cost 4) cannot be played.
    const tooExpensive = handCards(element)[4];
    expect(cardButton(tooExpensive).disabled).toBe(true);
    expect(label(cardButton(tooExpensive))).toBe('Stażysta');
    expect(tooExpensive.querySelector('article.not-playable')).not.toBeNull();
    expect(handCards(element)[0].querySelector('article.not-playable')).toBeNull();
  });

  it('names each card button with its action and the card name', async () => {
    const fixture = await renderFixture(targetView());
    const element = fixture.nativeElement as HTMLElement;

    expect(label(playButtons(element)[0])).toBe('Zagraj: Junior Dev');
    expect(label(playButtons(element)[2])).toBe('Zagraj: Hotfix');
    expect(label(characterButtons(element, 'player')[0])).toBe('Wybierz do ataku: Tech Lead');

    characterButtons(element, 'player')[0].click();
    await fixture.whenStable();
    expect(label(characterButtons(element, 'opponent')[0])).toBe('Atakuj: QA Junior');

    button(element, '.cancel-attack').click();
    await fixture.whenStable();
    playButtons(element)[2].click();
    await fixture.whenStable();
    expect(label(characterButtons(element, 'opponent')[1])).toBe('Cel: QA Lead');
    // The article inside keeps the plain card name for the Card's own label.
    expect(label(element.querySelector('.opponent .character article'))).toBe('QA Junior');
  });

  it('shows the enlarged full card of a hovered hand or board card and hides it on mouseleave', async () => {
    const fixture = await renderFixture(boardView());
    const element = fixture.nativeElement as HTMLElement;
    expect(hoverCard(element)).toBeNull();

    const handCard = handCards(element)[0];
    await hover(fixture, handCard);
    const shown = element.querySelector('app-hover-card')!;
    expect(shown.getAttribute('aria-hidden')).toBe('true');
    expect(hoverCard(element)).toBe('Junior Dev');
    const full = shown.querySelector('app-card')!;
    expect(full.classList).not.toContain('compact');
    expect(full.querySelector('img')?.getAttribute('src')).toBe('/media/cards/dev-1.webp');
    expect(text(full.querySelector('.text'))).toBe('Pisze kod.');
    expect(element.querySelectorAll('app-hover-card').length).toBe(1);
    await hover(fixture, handCard, false);
    expect(hoverCard(element)).toBeNull();

    const own = element.querySelector('.player .board .character')!;
    await hover(fixture, own);
    expect(hoverCard(element)).toBe('Tech Lead');
    await hover(fixture, own, false);
    expect(hoverCard(element)).toBeNull();
  });

  it('shows the hover card for a focused card and hides it on focusout', async () => {
    const fixture = await renderFixture(boardView());
    const element = fixture.nativeElement as HTMLElement;

    const enemy = characterButtons(element, 'opponent')[1];
    enemy.dispatchEvent(new FocusEvent('focusin', { bubbles: true }));
    await fixture.whenStable();
    expect(hoverCard(element)).toBe('QA Lead');
    expect(element.querySelector('app-hover-card app-card')?.classList).not.toContain('compact');
    enemy.dispatchEvent(new FocusEvent('focusout', { bubbles: true }));
    await fixture.whenStable();
    expect(hoverCard(element)).toBeNull();
  });

  it('swaps the hover card at once when another card is hovered', async () => {
    const fixture = await renderFixture(boardView());
    const element = fixture.nativeElement as HTMLElement;

    const [first, second] = handCards(element);
    await hover(fixture, first);
    expect(hoverCard(element)).toBe('Junior Dev');
    await hover(fixture, second);
    expect(hoverCard(element)).toBe('Senior Dev');
    expect(element.querySelectorAll('app-hover-card').length).toBe(1);
    await hover(fixture, element.querySelectorAll('.opponent .board .character')[0]);
    expect(hoverCard(element)).toBe('QA Junior');
    expect(element.querySelectorAll('app-hover-card').length).toBe(1);
  });

  it('shows no hover card for the selection alone', async () => {
    const fixture = await renderFixture(boardView());
    const element = fixture.nativeElement as HTMLElement;

    characterButtons(element, 'player')[0].click();
    await fixture.whenStable();
    expect(text(element.querySelector('.lifted .name'))).toBe('Tech Lead');
    expect(hoverCard(element)).toBeNull();
  });

  it('puts the opponent status in the left column and the player status in the right one', async () => {
    const element = await renderWithMatch();

    const left = element.querySelector('.aside.left')!;
    const right = element.querySelector('.aside.right')!;
    expect(left.querySelector('.status.opponent')).not.toBeNull();
    expect(left.querySelector('.status.player')).toBeNull();
    expect(right.querySelector('.status.player')).not.toBeNull();
    expect(right.querySelector('.status.opponent')).toBeNull();
    expect(right.querySelector('.log')).not.toBeNull();
    expect(right.querySelector('.aside-footer a.exit')).not.toBeNull();
  });

  it("renders the opponent's hand as one card back per card in hand", async () => {
    const match = matchView();
    const fixture = await renderFixture(match);
    const element = fixture.nativeElement as HTMLElement;
    const backs = () =>
      element.querySelectorAll('.aside.left .opponent-hand .hand-back app-card-back');
    expect(backs().length).toBe(6);
    expect(element.querySelector('.aside.right .opponent-hand')).toBeNull();

    playButtons(element)[0].click();
    await fixture.whenStable();
    httpTesting
      .expectOne({ method: 'POST', url: `/api/matches/${MATCH_ID}/play` })
      .flush({ ...match, opponent: { ...match.opponent, handCount: 8 } });
    await fixture.whenStable();
    expect(backs().length).toBe(8);
  });

  it('shows each deck pile with its deck count', async () => {
    const element = await renderWithMatch();

    expect(text(element.querySelector('.aside.left .deck-pile .deck-pile-count'))).toBe('24');
    expect(text(element.querySelector('.aside.right .deck-pile .deck-pile-count'))).toBe('25');
    expect(element.querySelectorAll('.aside.right .deck-pile app-card-back').length).toBe(3);
    expect(element.querySelector('app-side-cards')?.getAttribute('aria-hidden')).toBe('true');
  });

  it('animates only the hand cards drawn since the previous view', async () => {
    const match = matchView();
    const fixture = await renderFixture(match);
    const element = fixture.nativeElement as HTMLElement;
    // The opening hand is dealt: every card is drawn, staggered by its order.
    expect(handCards(element).every((card) => card.classList.contains('drawn'))).toBe(true);
    expect(handCards(element)[2].querySelector('app-hand-slot')?.getAttribute('style')).toContain(
      '--draw-i: 2',
    );
    expect(handCards(element)[0].querySelector('app-hand-slot app-card-back')).not.toBeNull();
    expect(element.querySelector('.hand')?.getAttribute('style')).toContain('--n: 5');

    button(element, '.end-turn').click();
    await fixture.whenStable();
    const drawnCard: HandCard = { ...HAND[0], instanceId: 6, name: 'Nowy Dev' };
    httpTesting
      .expectOne({ method: 'POST', url: `/api/matches/${MATCH_ID}/end-turn` })
      .flush({ ...match, player: { ...match.player, hand: [...match.player.hand, drawnCard] } });
    await fixture.whenStable();

    const cards = handCards(element);
    expect(cards.length).toBe(6);
    expect(cards.slice(0, 5).some((card) => card.classList.contains('drawn'))).toBe(false);
    expect(cards[5].classList).toContain('drawn');
    expect(cards[5].querySelector('app-hand-slot')?.getAttribute('style')).toContain('--draw-i: 0');
    expect(cards[0].querySelector('app-hand-slot app-card-back')).toBeNull();
    expect(element.querySelector('.hand')?.getAttribute('style')).toContain('--n: 6');
  });

  it('lifts the selected card into the board centre only while an attacker or event is selected', async () => {
    const fixture = await renderFixture(targetView());
    const element = fixture.nativeElement as HTMLElement;
    const main = element.querySelector('main.main')!;
    expect(element.querySelector('.lifted')).toBeNull();
    expect(main.classList).not.toContain('targeting');

    characterButtons(element, 'player')[0].click();
    await fixture.whenStable();
    const lifted = element.querySelector('.turn-bar .lifted')!;
    expect(lifted.getAttribute('aria-hidden')).toBe('true');
    expect(text(lifted.querySelector('.name'))).toBe('Tech Lead');
    expect(lifted.querySelector('article.card.selected')).not.toBeNull();
    expect(lifted.querySelector('app-card')?.classList).toContain('compact');
    expect(main.classList).toContain('targeting');
    // The original card stays in its slot with the selected ring.
    expect(element.querySelector('.player .character.selected article.selected')).not.toBeNull();

    button(element, '.cancel-attack').click();
    await fixture.whenStable();
    expect(element.querySelector('.lifted')).toBeNull();

    playButtons(element)[2].click();
    await fixture.whenStable();
    expect(text(element.querySelector('.lifted .name'))).toBe('Hotfix');

    // Over a legal target the lifted card fades; over a non-target it does not.
    const [illegal, legal] = characterButtons(element, 'opponent');
    await hover(fixture, illegal);
    expect(main.classList).not.toContain('targeting-hover');
    await hover(fixture, legal);
    expect(main.classList).toContain('targeting-hover');
    await hover(fixture, legal, false);
    expect(main.classList).not.toContain('targeting-hover');

    legal.click();
    await fixture.whenStable();
    // The move is in flight: the selection is still set, but the lifted card is gone.
    expect(element.querySelector('.lifted')).toBeNull();
    httpTesting
      .expectOne({ method: 'POST', url: `/api/matches/${MATCH_ID}/play` })
      .flush(boardView());
    await fixture.whenStable();
    expect(element.querySelector('.lifted')).toBeNull();
    expect(main.classList).not.toContain('targeting');
  });

  it('enables only the attack targets the backend allows', async () => {
    const fixture = await renderFixture(boardView(true));
    const element = fixture.nativeElement as HTMLElement;

    characterButtons(element, 'player')[0].click();
    await fixture.whenStable();

    expect(statusTarget(element, 'opponent')).toBeNull();
    const targets = characterButtons(element, 'opponent');
    expect(targets[0].disabled).toBe(true);
    expect(targets[1].disabled).toBe(false);
  });

  it('enters target mode for an event with a target and offers only its targets', async () => {
    const fixture = await renderFixture(targetView());
    const element = fixture.nativeElement as HTMLElement;
    expect(statusTarget(element, 'opponent')).toBeNull();
    expect(statusTarget(element, 'player')).toBeNull();

    playButtons(element)[2].click();
    await fixture.whenStable();

    httpTesting.expectNone({ method: 'POST', url: `/api/matches/${MATCH_ID}/play` });
    expect(text(element.querySelector('.choose-target'))).toBe('Wybierz cel wydarzenia');
    expect(text(element.querySelector('.hand > li.card.selected .name'))).toBe('Hotfix');
    expect(playButtons(element)[2].getAttribute('aria-pressed')).toBe('true');
    const own = characterButtons(element, 'player');
    const enemy = characterButtons(element, 'opponent');
    expect(own.map((b) => b.disabled)).toEqual([false]);
    expect(enemy.map((b) => b.disabled)).toEqual([true, false]);
    expect(label(own[0])).toBe('Cel: Tech Lead');
    expect(label(enemy[1])).toBe('Cel: QA Lead');
    const opponentHp = statusTarget(element, 'opponent')!;
    expect(label(opponentHp)).toBe('Cel: Przeciwnik');
    expect(opponentHp.disabled).toBe(false);
    // The player's own HP is no target of this event: the panel stays a plain region.
    expect(statusTarget(element, 'player')).toBeNull();

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
    expect(statusTarget(element, 'opponent')).toBeNull();
    expect(element.querySelector('.cancel-target')).toBeNull();
    expect(characterButtons(element, 'opponent').every((b) => b.disabled)).toBe(true);
  });

  it('plays an event on a player target with kind and side', async () => {
    const fixture = await renderFixture(targetView());
    const element = fixture.nativeElement as HTMLElement;

    playButtons(element)[2].click();
    await fixture.whenStable();
    statusTarget(element, 'opponent')!.click();
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

  it('leaves target mode on "Anuluj" and attack mode when an event is chosen', async () => {
    const fixture = await renderFixture(targetView());
    const element = fixture.nativeElement as HTMLElement;

    playButtons(element)[2].click();
    await fixture.whenStable();
    const cancel = button(element, '.cancel-target');
    expect(text(cancel)).toBe('Anuluj');
    cancel.click();
    await fixture.whenStable();
    expect(element.querySelector('.choose-target')).toBeNull();
    expect(characterButtons(element, 'opponent').every((b) => b.disabled)).toBe(true);
    expect(playButtons(element)[2].getAttribute('aria-pressed')).toBe('false');

    characterButtons(element, 'player')[0].click();
    await fixture.whenStable();
    expect(element.querySelector('.cancel-attack')).not.toBeNull();
    expect(characterButtons(element, 'opponent').map((b) => label(b))).toEqual([
      'Atakuj: QA Junior',
      'Atakuj: QA Lead',
    ]);

    playButtons(element)[2].click();
    await fixture.whenStable();
    expect(element.querySelector('.cancel-attack')).toBeNull();
    expect(element.querySelector('.choose-target')).not.toBeNull();
    expect(characterButtons(element, 'opponent').map((b) => label(b))).toEqual([
      'QA Junior',
      'Cel: QA Lead',
    ]);

    // Clicking the selected event again leaves target mode too.
    playButtons(element)[2].click();
    await fixture.whenStable();
    expect(element.querySelector('.choose-target')).toBeNull();
    httpTesting.expectNone({ method: 'POST', url: `/api/matches/${MATCH_ID}/play` });
  });

  it('disables "Zagraj", "Cel" and "Anuluj" while an event request is in flight', async () => {
    const fixture = await renderFixture(targetView());
    const element = fixture.nativeElement as HTMLElement;

    playButtons(element)[2].click();
    await fixture.whenStable();
    statusTarget(element, 'opponent')!.click();
    await fixture.whenStable();

    expect(playButtons(element).every((b) => b.disabled)).toBe(true);
    const targets = [
      ...characterButtons(element, 'player'),
      ...characterButtons(element, 'opponent'),
    ];
    expect(targets.length).toBe(3);
    expect(targets.every((b) => b.disabled)).toBe(true);
    expect(statusTarget(element, 'opponent')!.disabled).toBe(true);
    expect(button(element, '.cancel-target').disabled).toBe(true);

    httpTesting
      .expectOne({ method: 'POST', url: `/api/matches/${MATCH_ID}/play` })
      .flush(boardView());
    await fixture.whenStable();
    expect(playButtons(element)[0].disabled).toBe(false);
  });

  it('shows effective stats, trapping and effective keywords on the board cards', async () => {
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
    expect(text(own[0].querySelector('.stat.attack'))).toBe('6');
    expect(text(own[0].querySelector('.stat.hp'))).toBe('5');
    expect(own[0].querySelector('.stat.attack')?.classList).toContain('marked');
    expect(own[0].querySelector('.stat.hp')?.classList).not.toContain('marked');
    expect(text(own[0].querySelector('.line'))).toBe('Szarża');
    expect(own[0].querySelector('.line.trapped-chip')).toBeNull();
    expect(own[1].querySelector('.stat.attack')?.classList).not.toContain('marked');
    const trapped = element.querySelector('.opponent .character')!;
    // Trapped wins the compact line over the Guard keyword.
    expect(text(trapped.querySelector('.line.trapped-chip'))).toBe('Uwięziona');
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

    const cards = handCards(element);
    expect(text(cards[3].querySelector('.line'))).toBe('Instant');
    const opponentCharacter = element.querySelector('.opponent .character')!;
    expect(text(opponentCharacter.querySelector('.line'))).toBe('Trapped');
    await hover(fixture, opponentCharacter);
    expect(text(element.querySelector('app-hover-card .keyword'))).toBe('Guard');

    playButtons(element)[2].click();
    await fixture.whenStable();
    expect(text(element.querySelector('.choose-target'))).toBe('Choose the event target');
    expect(text(element.querySelector('.cancel-target'))).toBe('Cancel');
    expect(label(statusTarget(element, 'opponent'))).toBe('Target: Opponent');
    expect(label(characterButtons(element, 'player')[0])).toBe('Target: Tech Lead');
    expect(label(playButtons(element)[2])).toBe('Play: Hotfix');
  });

  it('enables the attacker click only for characters that can attack', async () => {
    const match = boardView();
    const element = await renderWithMatch({
      ...match,
      player: {
        ...match.player,
        board: [character(10, 'Tech Lead'), character(11, 'Nowy', { canAttack: false })],
      },
    });

    const characters = characterButtons(element, 'player');
    expect(characters[0].disabled).toBe(false);
    expect(label(characters[0])).toBe('Wybierz do ataku: Tech Lead');
    expect(characters[1].disabled).toBe(true);
    expect(label(characters[1])).toBe('Nowy');
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
    request.flush({
      ...matchView(),
      turn: { number: 2, active: 'PLAYER', timeLimitSeconds: null },
    });
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
      .flush({ ...matchView(), turn: { number: 3, active: 'PLAYER', timeLimitSeconds: null } });
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
    expect(handCards(element).length).toBe(5);
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

    const buttons = moveButtons(element);
    expect(buttons.length).toBeGreaterThan(0);
    expect(buttons.every((candidate) => candidate.disabled)).toBe(true);

    httpTesting
      .expectOne({ method: 'POST', url: `/api/matches/${MATCH_ID}/end-turn` })
      .flush(boardView());
    await fixture.whenStable();

    expect(button(element, '.end-turn').disabled).toBe(false);
    expect(playButtons(element)[0].disabled).toBe(false);
  });

  afterEach(() => {
    Reflect.deleteProperty(document, 'elementFromPoint');
  });

  /** jsdom has no layout: the element under the pointer is whatever the test points at. */
  function pointAt(target: Element | null): void {
    Object.defineProperty(document, 'elementFromPoint', {
      configurable: true,
      value: () => target,
    });
  }

  function pointer(target: EventTarget, type: string, x: number, y: number): void {
    target.dispatchEvent(
      new PointerEvent(type, {
        bubbles: true,
        cancelable: true,
        clientX: x,
        clientY: y,
        pointerId: 1,
        isPrimary: true,
        button: type === 'pointermove' ? -1 : 0,
      }),
    );
  }

  /** Presses the card button at (100, 100) and moves the pointer by `dx` px. */
  async function dragBy(fixture: ComponentFixture<MatchTable>, card: Element, dx = 40) {
    pointer(cardButton(card), 'pointerdown', 100, 100);
    pointer(document, 'pointermove', 100 + dx / 2, 100);
    pointer(document, 'pointermove', 100 + dx, 100);
    await fixture.whenStable();
  }

  async function release(fixture: ComponentFixture<MatchTable>, dx = 40) {
    pointer(document, 'pointerup', 100 + dx, 100);
    await fixture.whenStable();
  }

  describe('dragging a hand card', () => {
    function ghost(element: HTMLElement): Element | null {
      return element.querySelector('app-drag-ghost');
    }

    function playerZone(element: HTMLElement): Element {
      return element.querySelector('section.zone.player')!;
    }

    it('plays a character dragged from the hand onto the player zone, like a click', async () => {
      const fixture = await renderFixture(matchView());
      const element = fixture.nativeElement as HTMLElement;
      const card = handCards(element)[0];
      await hover(fixture, card);
      expect(hoverCard(element)).toBe('Junior Dev');
      pointAt(playerZone(element).querySelector('.label'));

      await dragBy(fixture, card);

      const shown = ghost(element)!;
      expect(shown.getAttribute('aria-hidden')).toBe('true');
      expect(text(shown.querySelector('.name'))).toBe('Junior Dev');
      expect(shown.querySelector('app-card')?.classList).toContain('compact');
      expect(element.querySelector('main.main')?.classList).toContain('dragging');
      expect(card.querySelector('app-hand-slot')?.classList).toContain('dragged');
      expect(element.querySelector('app-drop-zone')?.classList).toContain('over');
      expect(hoverCard(element)).toBeNull();
      httpTesting.expectNone({ method: 'POST', url: `/api/matches/${MATCH_ID}/play` });

      await release(fixture);

      const request = httpTesting.expectOne({
        method: 'POST',
        url: `/api/matches/${MATCH_ID}/play`,
      });
      expect(request.request.body).toEqual({ turn: 1, instanceId: 1, target: null });
      expect(ghost(element)).toBeNull();
      expect(element.querySelector('app-drop-zone')).toBeNull();
      expect(element.querySelector('main.main')?.classList).not.toContain('dragging');
      request.flush(matchView());
      await fixture.whenStable();
    });

    it('fires no click after a completed drag, so only one move is sent', async () => {
      const fixture = await renderFixture(matchView());
      const element = fixture.nativeElement as HTMLElement;
      pointAt(playerZone(element));

      await dragBy(fixture, handCards(element)[0]);
      await release(fixture);
      // The browser clicks the pressed button after the release.
      playButtons(element)[0].click();
      await fixture.whenStable();

      httpTesting
        .expectOne({ method: 'POST', url: `/api/matches/${MATCH_ID}/play` })
        .flush(matchView());
      await fixture.whenStable();
    });

    it('treats a press that moves less than the drag threshold as a click', async () => {
      const fixture = await renderFixture(matchView());
      const element = fixture.nativeElement as HTMLElement;
      pointAt(element.querySelector('.turn-bar'));

      await dragBy(fixture, handCards(element)[0], 5);
      expect(ghost(element)).toBeNull();
      await release(fixture, 5);
      playButtons(element)[0].click();
      await fixture.whenStable();

      const request = httpTesting.expectOne({
        method: 'POST',
        url: `/api/matches/${MATCH_ID}/play`,
      });
      expect(request.request.body).toEqual({ turn: 1, instanceId: 1, target: null });
      request.flush(matchView());
      await fixture.whenStable();
    });

    it('sends nothing when a dragged card is dropped outside any drop target', async () => {
      const fixture = await renderFixture(matchView());
      const element = fixture.nativeElement as HTMLElement;
      pointAt(element.querySelector('.zone.opponent .label'));

      await dragBy(fixture, handCards(element)[0]);
      expect(element.querySelector('app-drop-zone')?.classList).not.toContain('over');
      await release(fixture);
      playButtons(element)[0].click();
      await fixture.whenStable();

      httpTesting.expectNone({ method: 'POST', url: `/api/matches/${MATCH_ID}/play` });
      expect(ghost(element)).toBeNull();
      expect(element.querySelector('main.main')?.classList).not.toContain('dragging');
      expect(handCards(element).length).toBe(5);
    });

    it('cancels a drag on Escape and on pointercancel without sending a move', async () => {
      const fixture = await renderFixture(matchView());
      const element = fixture.nativeElement as HTMLElement;
      pointAt(playerZone(element));

      await dragBy(fixture, handCards(element)[0]);
      document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }));
      await fixture.whenStable();
      expect(ghost(element)).toBeNull();
      await release(fixture);
      playButtons(element)[0].click();
      await fixture.whenStable();
      httpTesting.expectNone({ method: 'POST', url: `/api/matches/${MATCH_ID}/play` });

      // A new task: the click swallowed after the release above is not pending any more.
      await new Promise((resolve) => setTimeout(resolve));
      await dragBy(fixture, handCards(element)[0]);
      expect(ghost(element)).not.toBeNull();
      pointer(document, 'pointercancel', 140, 100);
      await fixture.whenStable();
      expect(ghost(element)).toBeNull();
      await release(fixture);
      httpTesting.expectNone({ method: 'POST', url: `/api/matches/${MATCH_ID}/play` });
    });

    it('plays a targeted event dragged onto a legal opponent character with that target', async () => {
      const fixture = await renderFixture(targetView());
      const element = fixture.nativeElement as HTMLElement;
      const legal = element.querySelectorAll('.zone.opponent .board .character')[1];
      pointAt(legal.querySelector('.name'));

      await dragBy(fixture, handCards(element)[2]);

      // Dragging selects nothing, but the event's legal targets are offered as in target mode.
      expect(element.querySelector('.choose-target')).toBeNull();
      expect(playButtons(element)[2].getAttribute('aria-pressed')).toBe('false');
      expect(characterButtons(element, 'opponent').map((b) => b.disabled)).toEqual([true, false]);
      expect(label(characterButtons(element, 'opponent')[1])).toBe('Cel: QA Lead');
      expect(statusTarget(element, 'opponent')).not.toBeNull();
      expect(element.querySelector('app-drop-zone')).toBeNull();

      await release(fixture);

      const request = httpTesting.expectOne({
        method: 'POST',
        url: `/api/matches/${MATCH_ID}/play`,
      });
      expect(request.request.body).toEqual({
        turn: 5,
        instanceId: 3,
        target: { kind: 'CHARACTER', instanceId: 21 },
      });
      request.flush(boardView());
      await fixture.whenStable();
    });

    it('plays a targeted event dragged onto the opponent status panel on the opponent', async () => {
      const fixture = await renderFixture(targetView());
      const element = fixture.nativeElement as HTMLElement;

      await dragBy(fixture, handCards(element)[2]);
      pointAt(statusTarget(element, 'opponent'));
      await release(fixture);

      const request = httpTesting.expectOne({
        method: 'POST',
        url: `/api/matches/${MATCH_ID}/play`,
      });
      expect(request.request.body).toEqual({
        turn: 5,
        instanceId: 3,
        target: { kind: 'PLAYER', side: 'OPPONENT' },
      });
      request.flush(boardView());
      await fixture.whenStable();
    });

    it('sends nothing when a targeted event is dropped on an illegal target', async () => {
      const fixture = await renderFixture(targetView());
      const element = fixture.nativeElement as HTMLElement;
      pointAt(element.querySelector('.zone.opponent .board .character .name'));

      await dragBy(fixture, handCards(element)[2]);
      await release(fixture);

      httpTesting.expectNone({ method: 'POST', url: `/api/matches/${MATCH_ID}/play` });
      expect(element.querySelector('.choose-target')).toBeNull();
      expect(characterButtons(element, 'opponent').every((b) => b.disabled)).toBe(true);
      expect(statusTarget(element, 'opponent')).toBeNull();
    });

    it('enters target mode when a targeted event is dropped on the player zone', async () => {
      const fixture = await renderFixture(targetView());
      const element = fixture.nativeElement as HTMLElement;
      pointAt(playerZone(element).querySelector('.label'));

      await dragBy(fixture, handCards(element)[2]);
      await release(fixture);
      // The click after the release must not leave target mode again.
      playButtons(element)[2].click();
      await fixture.whenStable();

      httpTesting.expectNone({ method: 'POST', url: `/api/matches/${MATCH_ID}/play` });
      expect(text(element.querySelector('.choose-target'))).toBe('Wybierz cel wydarzenia');
      expect(playButtons(element)[2].getAttribute('aria-pressed')).toBe('true');
      expect(text(element.querySelector('.lifted .name'))).toBe('Hotfix');
    });

    it('cannot drag a card that is not playable or while a move is in flight', async () => {
      const fixture = await renderFixture(matchView());
      const element = fixture.nativeElement as HTMLElement;
      pointAt(playerZone(element));
      // Budget 1: "Senior Dev" (cost 3) cannot be played; grab cursors only on playable cards.
      expect(playButtons(element)[0].style.cursor).toBe('grab');
      expect(playButtons(element)[1].style.cursor).toBe('');

      await dragBy(fixture, handCards(element)[1]);
      expect(ghost(element)).toBeNull();
      expect(element.querySelector('main.main')?.classList).not.toContain('dragging');
      await release(fixture);
      httpTesting.expectNone({ method: 'POST', url: `/api/matches/${MATCH_ID}/play` });

      playButtons(element)[0].click();
      await fixture.whenStable();
      const request = httpTesting.expectOne({
        method: 'POST',
        url: `/api/matches/${MATCH_ID}/play`,
      });
      await dragBy(fixture, handCards(element)[0]);
      expect(ghost(element)).toBeNull();
      await release(fixture);
      httpTesting.expectNone({ method: 'POST', url: `/api/matches/${MATCH_ID}/play` });
      request.flush(matchView());
      await fixture.whenStable();
    });
  });

  describe('dragging an attacker', () => {
    function playerCharacter(element: HTMLElement): Element {
      return element.querySelector('.zone.player .board .character')!;
    }

    function arrow(element: HTMLElement): Element | null {
      return element.querySelector('app-aim-arrow');
    }

    function locked(element: HTMLElement): boolean {
      return element.querySelector('app-aim-arrow .reticle.locked') !== null;
    }

    it('attacks a legal opponent character with an attacker dragged onto it, like a click', async () => {
      const fixture = await renderFixture(boardView());
      const element = fixture.nativeElement as HTMLElement;
      pointAt(
        element.querySelectorAll('.zone.opponent .board .character')[1].querySelector('.name'),
      );

      await dragBy(fixture, playerCharacter(element));

      // Dragging selects nothing, but the attacker's targets are offered as after a click.
      expect(arrow(element)?.getAttribute('aria-hidden')).toBe('true');
      expect(locked(element)).toBe(true);
      expect(characterButtons(element, 'opponent').map((b) => b.disabled)).toEqual([false, false]);
      expect(label(characterButtons(element, 'opponent')[1])).toBe('Atakuj: QA Lead');
      expect(statusTarget(element, 'opponent')).not.toBeNull();
      expect(element.querySelector('.cancel-attack')).toBeNull();
      expect(element.querySelector('.lifted')).toBeNull();
      expect(element.querySelector('app-drag-ghost')).toBeNull();
      expect(element.querySelector('main.main')?.classList).toContain('dragging');

      await release(fixture);
      // The click after the release must not select the attacker.
      characterButtons(element, 'player')[0].click();
      await fixture.whenStable();

      const request = httpTesting.expectOne({
        method: 'POST',
        url: `/api/matches/${MATCH_ID}/attack`,
      });
      expect(request.request.body).toEqual({ turn: 5, attackerId: 10, targetId: 21 });
      expect(arrow(element)).toBeNull();
      request.flush(boardView());
      await fixture.whenStable();
      expect(arrow(element)).toBeNull();
      expect(element.querySelector('.cancel-attack')).toBeNull();
    });

    it('attacks the opponent player with an attacker dragged onto the opponent status panel', async () => {
      const fixture = await renderFixture(boardView());
      const element = fixture.nativeElement as HTMLElement;

      await dragBy(fixture, playerCharacter(element));
      pointAt(statusTarget(element, 'opponent'));
      await release(fixture);

      const request = httpTesting.expectOne({
        method: 'POST',
        url: `/api/matches/${MATCH_ID}/attack`,
      });
      expect(request.request.body).toEqual({ turn: 5, attackerId: 10, targetId: null });
      request.flush(boardView());
      await fixture.whenStable();
    });

    it('sends no attack when a dragged attacker is dropped on an illegal or empty spot', async () => {
      // Guard: only "QA Lead" may be attacked.
      const fixture = await renderFixture(boardView(true));
      const element = fixture.nativeElement as HTMLElement;
      pointAt(element.querySelector('.zone.opponent .board .character .name'));

      await dragBy(fixture, playerCharacter(element));
      expect(arrow(element)).not.toBeNull();
      expect(locked(element)).toBe(false);
      await release(fixture);
      httpTesting.expectNone({ method: 'POST', url: `/api/matches/${MATCH_ID}/attack` });
      expect(arrow(element)).toBeNull();

      await new Promise((resolve) => setTimeout(resolve));
      pointAt(element.querySelector('.turn-bar'));
      await dragBy(fixture, playerCharacter(element));
      await release(fixture);
      httpTesting.expectNone({ method: 'POST', url: `/api/matches/${MATCH_ID}/attack` });

      // Escape cancels the drag even over a legal target.
      await new Promise((resolve) => setTimeout(resolve));
      pointAt(element.querySelectorAll('.zone.opponent .board .character')[1]);
      await dragBy(fixture, playerCharacter(element));
      expect(locked(element)).toBe(true);
      document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }));
      await fixture.whenStable();
      expect(arrow(element)).toBeNull();
      await release(fixture);
      httpTesting.expectNone({ method: 'POST', url: `/api/matches/${MATCH_ID}/attack` });
      expect(element.querySelector('.cancel-attack')).toBeNull();
    });

    it('cannot drag a character that cannot attack', async () => {
      const match = boardView();
      const fixture = await renderFixture({
        ...match,
        player: { ...match.player, board: [character(11, 'Nowy', { canAttack: false })] },
      });
      const element = fixture.nativeElement as HTMLElement;
      pointAt(element.querySelector('.zone.opponent .board .character'));
      expect(characterButtons(element, 'player')[0].style.cursor).toBe('');

      await dragBy(fixture, playerCharacter(element));
      expect(arrow(element)).toBeNull();
      expect(element.querySelector('main.main')?.classList).not.toContain('dragging');
      await release(fixture);
      httpTesting.expectNone({ method: 'POST', url: `/api/matches/${MATCH_ID}/attack` });
    });

    it('aims the arrow from a click-selected attacker, locks it on legal targets and removes it on cancel and attack', async () => {
      const fixture = await renderFixture(boardView());
      const element = fixture.nativeElement as HTMLElement;
      const pointerClick = (target: Element) =>
        target.dispatchEvent(
          new MouseEvent('click', { bubbles: true, detail: 1, clientX: 100, clientY: 100 }),
        );
      expect(characterButtons(element, 'player')[0].style.cursor).toBe('grab');

      pointAt(element.querySelector('.turn-bar'));
      pointerClick(characterButtons(element, 'player')[0]);
      await fixture.whenStable();
      expect(arrow(element)).not.toBeNull();
      expect(locked(element)).toBe(false);
      // The arrow shows the aim: no lifted card crosses it.
      expect(element.querySelector('.lifted')).toBeNull();

      pointAt(
        element.querySelectorAll('.zone.opponent .board .character')[0].querySelector('.name'),
      );
      pointer(document, 'pointermove', 300, 50);
      await fixture.whenStable();
      expect(locked(element)).toBe(true);
      pointAt(element.querySelector('.turn-bar'));
      pointer(document, 'pointermove', 300, 150);
      await fixture.whenStable();
      expect(locked(element)).toBe(false);

      button(element, '.cancel-attack').click();
      await fixture.whenStable();
      expect(arrow(element)).toBeNull();
      pointer(document, 'pointermove', 300, 160);
      await fixture.whenStable();
      expect(arrow(element)).toBeNull();

      pointerClick(characterButtons(element, 'player')[0]);
      await fixture.whenStable();
      expect(arrow(element)).not.toBeNull();
      statusTarget(element, 'opponent')!.click();
      await fixture.whenStable();
      expect(arrow(element)).toBeNull();
      httpTesting
        .expectOne({ method: 'POST', url: `/api/matches/${MATCH_ID}/attack` })
        .flush(boardView());
      await fixture.whenStable();
      expect(arrow(element)).toBeNull();
    });
  });

  describe('battle log scroll', () => {
    /** jsdom has no layout: the log's sizes and scroll position are set by the test. */
    function stubLog(element: HTMLElement, height: () => number) {
      const box = element.querySelector('.log')!;
      let top = 0;
      Object.defineProperty(box, 'scrollHeight', { configurable: true, get: height });
      Object.defineProperty(box, 'clientHeight', { configurable: true, get: () => 100 });
      Object.defineProperty(box, 'scrollTop', {
        configurable: true,
        get: () => top,
        set: (value: number) => (top = value),
      });
      return {
        get top() {
          return top;
        },
        scrollTo(value: number) {
          top = value;
          box.dispatchEvent(new Event('scroll'));
        },
      };
    }

    async function newEntries(fixture: ComponentFixture<MatchTable>, count: number) {
      button(fixture.nativeElement as HTMLElement, '.end-turn').click();
      await fixture.whenStable();
      const log: LogEntry[] = Array.from({ length: count }, (_, index) => ({
        side: 'PLAYER',
        text: `Wpis ${index + 1}`,
        reason: false,
      }));
      httpTesting
        .expectOne({ method: 'POST', url: `/api/matches/${MATCH_ID}/end-turn` })
        .flush({ ...matchView(), log });
      await fixture.whenStable();
    }

    it('scrolls the battle log to the newest entry when entries are added', async () => {
      const fixture = await renderFixture({ ...matchView(), log: LOG.slice(0, 1) });
      const element = fixture.nativeElement as HTMLElement;
      let height = 500;
      const log = stubLog(element, () => height);

      await newEntries(fixture, 3);
      expect(log.top).toBe(400);

      height = 650;
      await newEntries(fixture, 5);
      expect(log.top).toBe(550);
    });

    it('keeps the log where the player scrolled to and follows again once back at the newest entry', async () => {
      const fixture = await renderFixture({ ...matchView(), log: LOG.slice(0, 1) });
      const element = fixture.nativeElement as HTMLElement;
      let height = 500;
      const log = stubLog(element, () => height);
      await newEntries(fixture, 3);
      expect(log.top).toBe(400);

      log.scrollTo(100);
      height = 600;
      await newEntries(fixture, 4);
      expect(log.top).toBe(100);

      // Within a few px of the newest entry counts as back.
      log.scrollTo(490);
      height = 700;
      await newEntries(fixture, 5);
      expect(log.top).toBe(600);
    });
  });

  describe('attack animation', () => {
    let animate: ReturnType<typeof vi.fn>;

    beforeEach(() => {
      // jsdom has no Web Animations API: record the calls instead.
      animate = vi.fn(() => ({ cancel: vi.fn(), finished: Promise.resolve() }));
      Object.defineProperty(HTMLElement.prototype, 'animate', {
        configurable: true,
        value: animate,
      });
    });

    afterEach(() => {
      Reflect.deleteProperty(HTMLElement.prototype, 'animate');
      Reflect.deleteProperty(window, 'matchMedia');
    });

    function floats(element: HTMLElement): string[] {
      return Array.from(element.querySelectorAll('app-damage-floats .damage')).map(text);
    }

    /** boardView() after Tech Lead (5 HP, 2 damage) attacked QA Lead (3 HP): 2 dealt, 1 taken. */
    function afterCharacterAttack(): MatchView {
      const match = boardView();
      return {
        ...match,
        player: {
          ...match.player,
          board: [{ ...match.player.board[0], damage: 3, canAttack: false, attackTargets: [] }],
        },
        opponent: {
          ...match.opponent,
          board: [match.opponent.board[0], { ...match.opponent.board[1], damage: 2 }],
        },
      };
    }

    it('lunges the attacker at the target and shows the damage from the HP difference on both', async () => {
      const fixture = await renderFixture(boardView());
      const element = fixture.nativeElement as HTMLElement;

      characterButtons(element, 'player')[0].click();
      await fixture.whenStable();
      characterButtons(element, 'opponent')[1].click();
      await fixture.whenStable();
      expect(element.querySelector('[data-fx]')).toBeNull();
      httpTesting
        .expectOne({ method: 'POST', url: `/api/matches/${MATCH_ID}/attack` })
        .flush(afterCharacterAttack());
      await fixture.whenStable();

      const lunging = element.querySelector('.zone.player .character[data-fx="lunge"]');
      const hit = element.querySelector('.zone.opponent .character[data-fx="hit"]');
      expect(lunging?.getAttribute('data-instance-id')).toBe('10');
      expect(hit?.getAttribute('data-instance-id')).toBe('21');
      expect(element.querySelectorAll('[data-fx]').length).toBe(2);
      expect(floats(element)).toEqual(['−2', '−1']);
      expect(animate).toHaveBeenCalledTimes(2);

      // A new view cuts the animation to the new state.
      button(element, '.end-turn').click();
      await fixture.whenStable();
      httpTesting
        .expectOne({ method: 'POST', url: `/api/matches/${MATCH_ID}/end-turn` })
        .flush(boardView());
      await fixture.whenStable();
      expect(element.querySelector('[data-fx]')).toBeNull();
      expect(floats(element)).toEqual([]);
    });

    it('shows the damage on the opponent panel when the opponent player is hit', async () => {
      const fixture = await renderFixture(boardView());
      const element = fixture.nativeElement as HTMLElement;

      characterButtons(element, 'player')[0].click();
      await fixture.whenStable();
      statusTarget(element, 'opponent')!.click();
      await fixture.whenStable();
      const after = boardView();
      httpTesting
        .expectOne({ method: 'POST', url: `/api/matches/${MATCH_ID}/attack` })
        .flush({ ...after, opponent: { ...after.opponent, hp: 26 } });
      await fixture.whenStable();

      expect(
        element.querySelector('app-status-panel.status.opponent')?.getAttribute('data-fx'),
      ).toBe('hit');
      expect(element.querySelector('.zone.player .character')?.getAttribute('data-fx')).toBe(
        'lunge',
      );
      expect(floats(element)).toEqual(['−4']);
    });

    it('shows the HP a killed character had left as its damage', async () => {
      const fixture = await renderFixture(boardView());
      const element = fixture.nativeElement as HTMLElement;

      characterButtons(element, 'player')[0].click();
      await fixture.whenStable();
      characterButtons(element, 'opponent')[1].click();
      await fixture.whenStable();
      const after = afterCharacterAttack();
      httpTesting
        .expectOne({ method: 'POST', url: `/api/matches/${MATCH_ID}/attack` })
        .flush({ ...after, opponent: { ...after.opponent, board: [after.opponent.board[0]] } });
      await fixture.whenStable();

      expect(floats(element)).toEqual(['−3', '−1']);
    });

    it('skips the lunge and the shake when reduced motion is preferred, keeping the damage', async () => {
      Object.defineProperty(window, 'matchMedia', {
        configurable: true,
        value: (query: string) => ({ matches: query.includes('reduce') }),
      });
      const fixture = await renderFixture(boardView());
      const element = fixture.nativeElement as HTMLElement;

      characterButtons(element, 'player')[0].click();
      await fixture.whenStable();
      characterButtons(element, 'opponent')[1].click();
      await fixture.whenStable();
      httpTesting
        .expectOne({ method: 'POST', url: `/api/matches/${MATCH_ID}/attack` })
        .flush(afterCharacterAttack());
      await fixture.whenStable();

      expect(animate).not.toHaveBeenCalled();
      expect(floats(element)).toEqual(['−2', '−1']);
    });
  });

  describe('Guard on the board', () => {
    function slot(item: Element): Element {
      return item.querySelector('app-board-slot')!;
    }

    it('stands a Guard character on a shield plate and names Guard in its button', async () => {
      const element = await renderWithMatch(boardView(true));

      const [junior, lead] = Array.from(element.querySelectorAll('.zone.opponent .character'));
      expect(slot(lead).classList).toContain('guard');
      expect(slot(lead).querySelector('.shield')?.getAttribute('aria-hidden')).toBe('true');
      expect(label(cardButton(lead))).toBe('QA Lead (Ochrona)');
      expect(slot(junior).querySelector('.shield')).toBeNull();
      expect(label(cardButton(junior))).toBe('QA Junior');
      expect(element.querySelector('.zone.player .character .shield')).toBeNull();
    });

    it('shows the shield for a Guard granted by an event, never on hand or hover cards', async () => {
      const match = boardView();
      const fixture = await renderFixture({
        ...match,
        // "Tech Lead" has no Guard ability: the keyword comes from an active event.
        player: {
          ...match.player,
          board: [{ ...match.player.board[0], keywords: ['GUARD'] }],
        },
      });
      const element = fixture.nativeElement as HTMLElement;

      const own = element.querySelector('.zone.player .character')!;
      expect(own.querySelector('.shield')).not.toBeNull();
      // "Stażysta" in the hand has the Guard ability.
      expect(text(handCards(element)[4].querySelector('.line'))).toBe('Ochrona');
      expect(element.querySelector('.hand .shield')).toBeNull();
      await hover(fixture, own);
      expect(hoverCard(element)).toBe('Tech Lead');
      expect(element.querySelector('app-hover-card .shield')).toBeNull();
    });

    it('lights up the legal Guard targets while an attacker is aimed', async () => {
      const fixture = await renderFixture(boardView(true));
      const element = fixture.nativeElement as HTMLElement;
      const leadSlot = () => slot(element.querySelectorAll('.zone.opponent .character')[1]);
      expect(leadSlot().classList).not.toContain('aimed');

      characterButtons(element, 'player')[0].click();
      await fixture.whenStable();
      expect(leadSlot().classList).toContain('aimed');
      expect(label(characterButtons(element, 'opponent')[1])).toBe('Atakuj: QA Lead (Ochrona)');

      button(element, '.cancel-attack').click();
      await fixture.whenStable();
      expect(leadSlot().classList).not.toContain('aimed');

      pointAt(element.querySelector('.turn-bar'));
      await dragBy(fixture, element.querySelector('.zone.player .board .character')!);
      expect(leadSlot().classList).toContain('aimed');
      await release(fixture);
      expect(leadSlot().classList).not.toContain('aimed');
    });
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
      return characterButtons(element, 'player')[0];
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
      expect(text(element.querySelector('.status.player .hp dd'))).toBe('30/30');
      expect(logTexts(element)).toEqual(LOG.slice(0, 2).map((entry) => entry.text));
      expect(playButtons(element).every((candidate) => candidate.disabled)).toBe(true);
      expect(attackButton(element).disabled).toBe(true);

      advance(fixture, STEP_MS - 1);
      expect(logTexts(element).length).toBe(2);
      advance(fixture, 1);
      expect(text(element.querySelector('.status.player .hp dd'))).toBe('26/30');
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

    it("captions each playback step with its own reason, never the previous turn's", async () => {
      const fixture = await renderBoardThenFakeClock();
      const element = fixture.nativeElement as HTMLElement;

      button(element, '.end-turn').click();
      httpTesting
        .expectOne({ method: 'POST', url: `/api/matches/${MATCH_ID}/end-turn` })
        .flush(reasonResponse());
      fixture.detectChanges();

      // Step 0: the log ends with the previous turn's reason, but no move of this turn is shown yet.
      expect(text(element.querySelector('.opponent-moving'))).toBe('Ruch przeciwnika…');
      expect(element.querySelector('.move-reason')).toBeNull();
      expect(element.querySelectorAll('.log .log-entry.reason').length).toBe(1);

      advance(fixture, STEP_MS);
      const caption = element.querySelector('.move-reason');
      expect(caption?.getAttribute('role')).toBe('status');
      expect(text(caption)).toBe('Przeciwnik: rozbudowuje stół — wystawia QA Senior');
      expect(Array.from(element.querySelectorAll('.log .log-entry.reason')).map(text)).toEqual([
        'Przeciwnik: kończy turę — żaden ruch nie poprawia jego sytuacji',
        'Przeciwnik: rozbudowuje stół — wystawia QA Senior',
      ]);

      advance(fixture, STEP_MS);
      expect(text(element.querySelector('.move-reason'))).toBe(
        'Przeciwnik: naciska — QA Senior zadaje Ci 4 obrażenia',
      );

      advance(fixture, STEP_MS);
      expect(element.querySelector('.opponent-moving')).toBeNull();
      expect(element.querySelector('.move-reason')).toBeNull();
      expect(element.querySelectorAll('.log .log-entry.reason').length).toBe(3);
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

  describe('turn countdown', () => {
    const LIMIT = 45;

    function timed(match: MatchView, limit: number | null = LIMIT): MatchView {
      return { ...match, turn: { ...match.turn, timeLimitSeconds: limit } };
    }

    /** endTurnResponse() whose final state is the player's turn with the time limit. */
    function timedEndTurnResponse(): MatchView {
      return timed(endTurnResponse());
    }

    /** Shows a match handed over by new-match under the fake clock, without GET /current. */
    function renderStarted(match: MatchView): ComponentFixture<MatchTable> {
      TestBed.inject(GameService).setStartedMatch(match);
      vi.useFakeTimers();
      const fixture = TestBed.createComponent(MatchTable);
      fixture.detectChanges();
      return fixture;
    }

    function advance(fixture: ComponentFixture<MatchTable>, ms: number): void {
      vi.advanceTimersByTime(ms);
      fixture.detectChanges();
    }

    function timer(element: HTMLElement): string | null {
      const found = element.querySelector('.turn-timer');
      return found === null ? null : text(found);
    }

    it('starts after the playback and counts down from the turn limit', () => {
      const fixture = renderStarted(timedEndTurnResponse());
      const element = fixture.nativeElement as HTMLElement;

      expect(text(element.querySelector('.opponent-moving'))).toBe('Ruch przeciwnika…');
      expect(timer(element)).toBeNull();
      advance(fixture, STEP_MS);
      expect(timer(element)).toBeNull();

      advance(fixture, STEP_MS);
      expect(element.querySelector('.opponent-moving')).toBeNull();
      expect(timer(element)).toBe('Pozostało: 45 s');
      expect(element.querySelector('.turn-timer')?.getAttribute('role')).toBe('timer');
      expect(element.querySelector('.turn-timer.urgent')).toBeNull();

      advance(fixture, 1000);
      expect(timer(element)).toBe('Pozostało: 44 s');
      advance(fixture, 34_000);
      expect(timer(element)).toBe('Pozostało: 10 s');
      expect(element.querySelector('.turn-timer.urgent')).not.toBeNull();
    });

    it('shows the countdown in English', () => {
      TestBed.inject(LanguageService).setLanguage('en');
      const fixture = renderStarted(timed(boardView()));

      expect(timer(fixture.nativeElement as HTMLElement)).toBe('Time left: 45 s');
    });

    it('sends end-turn when the countdown reaches zero', () => {
      const fixture = renderStarted(timed(boardView()));
      const element = fixture.nativeElement as HTMLElement;

      advance(fixture, (LIMIT - 1) * 1000);
      expect(timer(element)).toBe('Pozostało: 1 s');
      httpTesting.expectNone({ method: 'POST', url: `/api/matches/${MATCH_ID}/end-turn` });

      advance(fixture, 1000);
      const request = httpTesting.expectOne({
        method: 'POST',
        url: `/api/matches/${MATCH_ID}/end-turn`,
      });
      expect(request.request.body).toEqual({ turn: 5 });
      request.flush(timedEndTurnResponse());
      fixture.detectChanges();

      // The playback of the computer turn stops the countdown; the next turn starts it again.
      expect(timer(element)).toBeNull();
      advance(fixture, STEP_MS * 2);
      expect(timer(element)).toBe('Pozostało: 45 s');
    });

    it('sends end-turn at zero only after the request in flight settles', () => {
      const fixture = renderStarted(timed(matchView()));
      const element = fixture.nativeElement as HTMLElement;

      advance(fixture, (LIMIT - 1) * 1000);
      playButtons(element)[0].click();
      const play = httpTesting.expectOne({ method: 'POST', url: `/api/matches/${MATCH_ID}/play` });

      advance(fixture, 1000);
      httpTesting.expectNone({ method: 'POST', url: `/api/matches/${MATCH_ID}/end-turn` });
      expect(timer(element)).toBe('Pozostało: 0 s');

      play.flush(timed(matchView()));
      fixture.detectChanges();
      const request = httpTesting.expectOne({
        method: 'POST',
        url: `/api/matches/${MATCH_ID}/end-turn`,
      });
      expect(request.request.body).toEqual({ turn: 1 });
    });

    it('drops the owed end-turn when the request in flight at zero gets a 409', () => {
      const fixture = renderStarted(timed(matchView()));
      const element = fixture.nativeElement as HTMLElement;

      advance(fixture, (LIMIT - 1) * 1000);
      playButtons(element)[0].click();
      const play = httpTesting.expectOne({ method: 'POST', url: `/api/matches/${MATCH_ID}/play` });
      advance(fixture, 1000);

      play.flush({ message: 'stale' }, { status: 409, statusText: 'Conflict' });
      fixture.detectChanges();
      httpTesting.expectOne({ method: 'GET', url: '/api/matches/current' });
      httpTesting.expectNone({ method: 'POST', url: `/api/matches/${MATCH_ID}/end-turn` });
    });

    it('cancels the attack selection at zero and ends the turn', () => {
      const fixture = renderStarted(timed(boardView()));
      const element = fixture.nativeElement as HTMLElement;

      advance(fixture, (LIMIT - 1) * 1000);
      characterButtons(element, 'player')[0].click();
      fixture.detectChanges();
      expect(statusTarget(element, 'opponent')).not.toBeNull();

      advance(fixture, 1000);
      expect(statusTarget(element, 'opponent')).toBeNull();
      expect(element.querySelector('.cancel-attack')).toBeNull();
      expect(element.querySelector('.lifted')).toBeNull();
      httpTesting.expectOne({ method: 'POST', url: `/api/matches/${MATCH_ID}/end-turn` });
      httpTesting.expectNone({ method: 'POST', url: `/api/matches/${MATCH_ID}/attack` });
    });

    it('cancels the event target selection at zero and ends the turn', () => {
      const fixture = renderStarted(timed(targetView()));
      const element = fixture.nativeElement as HTMLElement;

      advance(fixture, (LIMIT - 1) * 1000);
      playButtons(element)[2].click();
      fixture.detectChanges();
      expect(element.querySelector('.choose-target')).not.toBeNull();

      advance(fixture, 1000);
      expect(element.querySelector('.choose-target')).toBeNull();
      expect(statusTarget(element, 'opponent')).toBeNull();
      httpTesting.expectOne({ method: 'POST', url: `/api/matches/${MATCH_ID}/end-turn` });
      httpTesting.expectNone({ method: 'POST', url: `/api/matches/${MATCH_ID}/play` });
    });

    it('keeps counting through a move in the same turn', () => {
      const fixture = renderStarted(timed(matchView()));
      const element = fixture.nativeElement as HTMLElement;

      advance(fixture, 5000);
      playButtons(element)[0].click();
      httpTesting
        .expectOne({ method: 'POST', url: `/api/matches/${MATCH_ID}/play` })
        .flush(timed(matchView()));
      fixture.detectChanges();

      expect(timer(element)).toBe('Pozostało: 40 s');
    });

    it('does not run without a time limit', () => {
      const fixture = renderStarted(timed(matchView(), null));
      const element = fixture.nativeElement as HTMLElement;
      expect(timer(element)).toBeNull();

      advance(fixture, LIMIT * 1000);
      httpTesting.expectNone({ method: 'POST', url: `/api/matches/${MATCH_ID}/end-turn` });
      fixture.destroy();
    });

    it('stops when the match ends', () => {
      const fixture = renderStarted(timed(matchView()));
      const element = fixture.nativeElement as HTMLElement;

      button(element, '.end-turn').click();
      httpTesting
        .expectOne({ method: 'POST', url: `/api/matches/${MATCH_ID}/end-turn` })
        .flush(finishedView('PLAYER', 'HP_DEPLETED'));
      fixture.detectChanges();

      expect(timer(element)).toBeNull();
      advance(fixture, LIMIT * 1000);
      httpTesting.expectNone({ method: 'POST', url: `/api/matches/${MATCH_ID}/end-turn` });
    });

    it('stops when the component is destroyed', () => {
      const fixture = renderStarted(timed(matchView()));

      fixture.destroy();
      vi.advanceTimersByTime(LIMIT * 1000);

      httpTesting.expectNone({ method: 'POST', url: `/api/matches/${MATCH_ID}/end-turn` });
    });
  });

  it('shows the win with its reason and a link to /new-match, with no move left', async () => {
    const element = await renderWithMatch(finishedView('PLAYER', 'HP_DEPLETED'));

    const result = element.querySelector('.result')!;
    expect(text(result.querySelector('h2'))).toBe('Wygrana');
    expect(text(result.querySelector('.result-reason'))).toBe(
      'Przyczyna: HP spadło do 0 lub niżej',
    );
    const link = result.querySelector('a.play-again');
    expect(text(link)).toBe('Zagraj ponownie');
    expect(link?.getAttribute('href')).toBe('/new-match');
    expect(element.querySelector('.end-turn')).toBeNull();
    expect(element.querySelector('.status-target')).toBeNull();
    const buttons = moveButtons(element);
    expect(buttons.length).toBe(5);
    expect(buttons.every((candidate) => candidate.classList.contains('card-action'))).toBe(true);
    expect(buttons.every((candidate) => candidate.disabled)).toBe(true);
  });

  it('shows the loss and the real HP below zero', async () => {
    const element = await renderWithMatch(finishedView('OPPONENT', 'EMPTY_DECK'));

    expect(text(element.querySelector('.result h2'))).toBe('Przegrana');
    expect(text(element.querySelector('.result-reason'))).toBe('Przyczyna: pusta talia');
    expect(text(element.querySelector('.status.player .hp dd'))).toBe('-3/30');
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
  const opponentTurn = { number: 6, active: 'OPPONENT', timeLimitSeconds: null } as const;
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
    reason: null,
    ...overrides,
  });
  return {
    ...before,
    turn: { number: 7, active: 'PLAYER', timeLimitSeconds: null },
    player,
    opponent,
    log: LOG,
    replay: [
      step({ logSize: 2 }),
      step({ player, opponent, logSize: 3 }),
      step({
        turn: { number: 7, active: 'PLAYER', timeLimitSeconds: null },
        player,
        opponent,
        logSize: 5,
      }),
    ],
  };
}

/**
 * endTurnResponse() shaped like a real second computer turn: the first step's log still ends with
 * the previous turn's reason, step 1 and step 2 carry the reasons of their moves, the final state
 * ends the playback.
 */
function reasonResponse(): MatchView {
  const response = endTurnResponse();
  const previousTurn = 'Przeciwnik: kończy turę — żaden ruch nie poprawia jego sytuacji';
  const develop = 'Przeciwnik: rozbudowuje stół — wystawia QA Senior';
  const pressure = 'Przeciwnik: naciska — QA Senior zadaje Ci 4 obrażenia';
  const log: LogEntry[] = [
    { side: 'OPPONENT', text: previousTurn, reason: true },
    { side: 'PLAYER', text: 'Kończysz turę', reason: false },
    { side: 'OPPONENT', text: 'Tura przeciwnika 6', reason: false },
    { side: 'OPPONENT', text: develop, reason: true },
    { side: 'OPPONENT', text: 'Przeciwnik zagrywa: QA Senior', reason: false },
    { side: 'OPPONENT', text: pressure, reason: true },
    { side: 'OPPONENT', text: 'QA Senior atakuje Cię i zadaje 4 obrażenia', reason: false },
    { side: 'PLAYER', text: 'Twoja tura 7', reason: false },
  ];
  const [first, second, last] = response.replay;
  return {
    ...response,
    log,
    replay: [
      { ...first, logSize: 3, reason: null },
      { ...first, logSize: 5, reason: develop },
      { ...second, logSize: 7, reason: pressure },
      { ...last, logSize: 8, reason: previousTurn },
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
