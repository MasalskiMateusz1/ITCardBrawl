import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { BoardCharacter, EndReason, HandCard, MatchView, Side } from '../game/match';
import { LANGUAGE_STORAGE_KEY, LanguageService } from '../i18n/language.service';
import { MatchTable } from './match-table';

const MATCH_ID = '00000000-0000-0000-0000-000000000001';

const GUARD = { keyword: 'GUARD', text: 'Ochrona: musi zostać zaatakowana pierwsza' } as const;

const HAND: HandCard[] = [
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
    duration: 1,
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
  },
];

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
      stress: 0,
      stressLimit: 30,
      resource: { current: 1, max: 1 },
      deckCount: 25,
      hand: HAND,
      board: [],
      activeEvents: [],
      graveyardCount: 0,
    },
    opponent: {
      faction: { id: 'testers', name: 'Testerzy' },
      stress: 0,
      stressLimit: 30,
      resource: { current: 0, max: 0 },
      deckCount: 24,
      handCount: 6,
      board: [],
      activeEvents: [],
      graveyardCount: 0,
    },
    result: null,
    log: [],
  };
}

/** A view mid-game: the player has an attacker on the board, the opponent two characters. */
function boardView(opponentGuard = false): MatchView {
  const match = matchView();
  return {
    ...match,
    turn: { number: 5, active: 'PLAYER' },
    player: {
      ...match.player,
      resource: { current: 3, max: 3 },
      board: [character(10, 'Tech Lead', { attack: 4, health: 5, damage: 2 })],
    },
    opponent: {
      ...match.opponent,
      board: [
        character(20, 'QA Junior', { canAttack: false }),
        character(21, 'QA Lead', { canAttack: false, ability: opponentGuard ? GUARD : null }),
      ],
    },
  };
}

function finishedView(winner: Side, reason: EndReason): MatchView {
  const match = matchView();
  return {
    ...match,
    status: 'FINISHED',
    player: { ...match.player, stress: winner === 'PLAYER' ? 4 : 33 },
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
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();

    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpTesting.verify();
    localStorage.clear();
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

  it('shows the stress of both sides, opponent card counts and resources', async () => {
    const element = await renderWithMatch();

    const opponent = element.querySelector('.opponent')!;
    const player = element.querySelector('.player')!;
    expect(text(opponent)).toContain('Testerzy');
    expect(text(opponent.querySelector('.stress dd'))).toBe('0/30');
    expect(text(opponent.querySelector('.resource dd'))).toBe('0/0');
    expect(text(opponent.querySelector('.hand-count dd'))).toBe('6');
    expect(text(opponent.querySelector('.deck-count dd'))).toBe('24');
    expect(text(player)).toContain('Developerzy');
    expect(text(player.querySelector('.stress dd'))).toBe('0/30');
    expect(text(player.querySelector('.resource dd'))).toBe('1/1');
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
    expect(text(element.querySelector('.opponent .stress dt'))).toBe('Stress');
    expect(text(element.querySelector('.opponent .resource dt'))).toBe('Resource');
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
    expect(request.request.body).toEqual({ turn: 1, instanceId: 1 });
    const after = matchView();
    request.flush({
      ...after,
      player: {
        ...after.player,
        resource: { current: 0, max: 1 },
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

  it('disables "Zagraj" for a character when the board is full', async () => {
    const match = matchView();
    const element = await renderWithMatch({
      ...match,
      player: {
        ...match.player,
        resource: { current: 5, max: 5 },
        board: [1, 2, 3, 4, 5].map((n) => character(100 + n, `Postać ${n}`)),
      },
    });

    const buttons = playButtons(element);
    expect(buttons[0].disabled).toBe(true); // character
    expect(buttons[2].disabled).toBe(false); // event
  });

  it('shows card stats, ability and event duration in the hand', async () => {
    const element = await renderWithMatch();

    const cards = Array.from(element.querySelectorAll('.player .card'));
    expect(text(cards[0].querySelector('.card-stats'))).toBe('Atak 1 / HP 2');
    expect(cards[0].querySelector('.card-duration')).toBeNull();
    expect(text(cards[2].querySelector('.card-duration'))).toBe('Czas trwania (tury): 2');
    expect(cards[2].querySelector('.card-stats')).toBeNull();
    expect(text(cards[4].querySelector('.ability'))).toBe(
      'Ochrona: musi zostać zaatakowana pierwsza',
    );
  });

  it('renders boards with remaining HP, active events, graveyard and real stress', async () => {
    const match = boardView();
    const element = await renderWithMatch({
      ...match,
      player: {
        ...match.player,
        stress: 33,
        graveyardCount: 2,
        activeEvents: [{ instanceId: 30, cardId: 'ev', name: 'Sprint', turnsLeft: 2 }],
      },
      opponent: {
        ...match.opponent,
        graveyardCount: 4,
        activeEvents: [{ instanceId: 31, cardId: 'ev2', name: 'Code Freeze', turnsLeft: 1 }],
      },
    });

    const player = element.querySelector('.player')!;
    const opponent = element.querySelector('.opponent')!;
    expect(text(player.querySelector('.stress dd'))).toBe('33/30');
    expect(text(player.querySelector('.character-stats'))).toBe('Atak 4 / HP 3');
    expect(text(player.querySelector('.graveyard dd'))).toBe('2');
    expect(text(opponent.querySelector('.graveyard dd'))).toBe('4');
    expect(text(player.querySelector('.event-name'))).toBe('Sprint');
    expect(text(player.querySelector('.event-turns'))).toBe('Pozostało tur: 2');
    expect(text(opponent.querySelector('.event-name'))).toBe('Code Freeze');
    expect(text(opponent.querySelector('.event-turns'))).toBe('Pozostało tur: 1');
    expect(text(player.querySelector('.events-heading'))).toBe('Aktywne wydarzenia');
  });

  it("attacks the opponent's stress after choosing an attacker", async () => {
    const fixture = await renderFixture(boardView());
    const element = fixture.nativeElement as HTMLElement;
    expect(element.querySelector('.attack-stress')).toBeNull();
    expect(element.querySelector('.opponent .target')).toBeNull();

    const attackButton = button(element, '.player .character button.attack');
    expect(text(attackButton)).toBe('Atakuj');
    attackButton.click();
    await fixture.whenStable();

    const stress = button(element, '.attack-stress');
    expect(text(stress)).toBe('Atakuj stres');
    expect(stress.disabled).toBe(false);
    expect(element.querySelectorAll('.opponent .target').length).toBe(2);
    stress.click();
    await fixture.whenStable();

    const request = httpTesting.expectOne({
      method: 'POST',
      url: `/api/matches/${MATCH_ID}/attack`,
    });
    expect(request.request.body).toEqual({ turn: 5, attackerId: 10, targetId: null });
    request.flush(boardView());
    await fixture.whenStable();

    expect(element.querySelector('.attack-stress')).toBeNull();
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

  it('allows only Guard targets and no stress attack when the opponent has Guard', async () => {
    const fixture = await renderFixture(boardView(true));
    const element = fixture.nativeElement as HTMLElement;

    button(element, '.player .character button.attack').click();
    await fixture.whenStable();

    expect(button(element, '.attack-stress').disabled).toBe(true);
    const targets = Array.from(element.querySelectorAll<HTMLButtonElement>('.opponent .target'));
    expect(targets[0].disabled).toBe(true);
    expect(targets[1].disabled).toBe(false);
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

  it('shows "moveRejected" on 400 and keeps the table', async () => {
    const fixture = await renderFixture(matchView());
    const element = fixture.nativeElement as HTMLElement;

    playButtons(element)[0].click();
    await fixture.whenStable();
    httpTesting
      .expectOne({ method: 'POST', url: `/api/matches/${MATCH_ID}/play` })
      .flush({ message: 'Za mało zasobu' }, { status: 400, statusText: 'Bad Request' });
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

  it('shows the win with its reason and a link to /new-match, without move buttons', async () => {
    const element = await renderWithMatch(finishedView('PLAYER', 'STRESS_LIMIT'));

    const result = element.querySelector('.result')!;
    expect(text(result.querySelector('h2'))).toBe('Wygrana');
    expect(text(result.querySelector('.result-reason'))).toBe('Przyczyna: limit stresu');
    const link = result.querySelector('a.play-again');
    expect(text(link)).toBe('Zagraj ponownie');
    expect(link?.getAttribute('href')).toBe('/new-match');
    expect(element.querySelector('button')).toBeNull();
  });

  it('shows the loss and the real stress over the limit', async () => {
    const element = await renderWithMatch(finishedView('OPPONENT', 'EMPTY_DECK'));

    expect(text(element.querySelector('.result h2'))).toBe('Przegrana');
    expect(text(element.querySelector('.result-reason'))).toBe('Przyczyna: pusta talia');
    expect(text(element.querySelector('.player .stress dd'))).toBe('33/30');
  });

  it('shows the result in English', async () => {
    TestBed.inject(LanguageService).setLanguage('en');

    const element = await renderWithMatch(finishedView('OPPONENT', 'STRESS_LIMIT'));

    expect(text(element.querySelector('.result h2'))).toBe('Defeat');
    expect(text(element.querySelector('.result-reason'))).toBe('Reason: stress limit');
    expect(text(element.querySelector('.play-again'))).toBe('Play again');
  });

  it('renders log entries in chronological order', async () => {
    const element = await renderWithMatch({
      ...matchView(),
      log: [
        { side: 'PLAYER', text: 'Zagrywasz: Junior Dev' },
        { side: 'OPPONENT', text: 'Przeciwnik zagrywa: Tech Lead' },
        { side: 'OPPONENT', text: 'Tech Lead atakuje Twój stres: +8 (24/30)' },
      ],
    });

    const entries = Array.from(element.querySelectorAll('.log .log-entry')).map(text);
    expect(entries).toEqual([
      'Zagrywasz: Junior Dev',
      'Przeciwnik zagrywa: Tech Lead',
      'Tech Lead atakuje Twój stres: +8 (24/30)',
    ]);
    expect(text(element.querySelector('.log h2'))).toBe('Przebieg partii');
  });
});

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
