import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { HandCard, MatchView, Side } from '../game/match';
import { LANGUAGE_STORAGE_KEY, LanguageService } from '../i18n/language.service';
import { MatchTable } from './match-table';

const HAND: HandCard[] = [
  {
    instanceId: 1,
    cardId: 'dev-1',
    name: 'Junior Dev',
    type: 'CHARACTER',
    cost: 1,
    text: 'Pisze kod.',
    neutral: false,
  },
  {
    instanceId: 2,
    cardId: 'dev-2',
    name: 'Senior Dev',
    type: 'CHARACTER',
    cost: 3,
    text: 'Refaktoryzuje.',
    neutral: false,
  },
  {
    instanceId: 3,
    cardId: 'dev-3',
    name: 'Hotfix',
    type: 'EVENT',
    cost: 2,
    text: 'Gasi pożar.',
    neutral: false,
  },
  {
    instanceId: 4,
    cardId: 'neu-1',
    name: 'Kawa',
    type: 'EVENT',
    cost: 5,
    text: 'Dodaje energii.',
    neutral: true,
  },
  {
    instanceId: 5,
    cardId: 'neu-2',
    name: 'Stażysta',
    type: 'CHARACTER',
    cost: 4,
    text: 'Uczy się.',
    neutral: true,
  },
];

function matchView(active: Side = 'PLAYER'): MatchView {
  return {
    matchId: '00000000-0000-0000-0000-000000000001',
    turn: { number: 1, active },
    player: {
      faction: { id: 'developers', name: 'Developerzy' },
      stress: 0,
      stressLimit: 30,
      resource: { current: 1, max: 1 },
      deckCount: 25,
      hand: HAND,
    },
    opponent: {
      faction: { id: 'testers', name: 'Testerzy' },
      stress: 0,
      stressLimit: 30,
      resource: { current: 0, max: 0 },
      deckCount: 24,
      handCount: 6,
    },
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

  async function renderWithMatch(match: MatchView = matchView()): Promise<HTMLElement> {
    const fixture = await render();
    httpTesting.expectOne({ method: 'GET', url: '/api/matches/current' }).flush(match);
    await fixture.whenStable();
    return fixture.nativeElement as HTMLElement;
  }

  function text(element: Element | null): string {
    return element?.textContent?.replace(/\s+/g, ' ').trim() ?? '';
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
    expect(text(element.querySelector('.player h3'))).toBe('Your hand');
    const cards = Array.from(element.querySelectorAll('.player .card'));
    expect(text(cards[0].querySelector('.card-cost'))).toBe('Cost: 1');
    expect(text(cards[0].querySelector('.card-type'))).toBe('Character');
    expect(text(cards[2].querySelector('.card-type'))).toBe('Event');
    expect(text(element.querySelector('.card-neutral'))).toBe('Neutral');
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
