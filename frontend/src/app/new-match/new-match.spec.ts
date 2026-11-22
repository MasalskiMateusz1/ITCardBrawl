import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { GameService } from '../game/game.service';
import { FactionSummary, MatchView } from '../game/match';
import { LANGUAGE_STORAGE_KEY, LanguageService } from '../i18n/language.service';
import { NewMatch } from './new-match';

const FACTIONS: FactionSummary[] = [
  { id: 'developers', name: 'Developerzy', description: 'Budują szybko.' },
  { id: 'testers', name: 'Testerzy', description: 'Znajdują każdy błąd.' },
];

const FACTIONS_EN: FactionSummary[] = [
  { id: 'developers', name: 'Developers', description: 'They build fast.' },
  { id: 'testers', name: 'Testers', description: 'They find every bug.' },
];

const MATCH: MatchView = {
  matchId: '00000000-0000-0000-0000-000000000001',
  status: 'IN_PROGRESS',
  turn: { number: 1, active: 'PLAYER', timeLimitSeconds: null },
  player: {
    faction: { id: 'developers', name: 'Developerzy' },
    hp: 30,
    maxHp: 30,
    budget: { current: 1, max: 1 },
    deckCount: 25,
    hand: [],
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
    deckCount: 25,
    handCount: 5,
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

describe('NewMatch', () => {
  let httpTesting: HttpTestingController;

  beforeEach(async () => {
    // Polish before LanguageService is created (jsdom reports navigator.language = en-US).
    localStorage.setItem(LANGUAGE_STORAGE_KEY, 'pl');
    await TestBed.configureTestingModule({
      imports: [NewMatch],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();

    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpTesting.verify();
    localStorage.clear();
  });

  async function render(): Promise<ComponentFixture<NewMatch>> {
    const fixture = TestBed.createComponent(NewMatch);
    await fixture.whenStable();
    return fixture;
  }

  async function renderWithFactions(): Promise<ComponentFixture<NewMatch>> {
    const fixture = await render();
    httpTesting.expectOne({ method: 'GET', url: '/api/factions' }).flush(FACTIONS);
    await fixture.whenStable();
    return fixture;
  }

  function startButton(fixture: ComponentFixture<NewMatch>): HTMLButtonElement {
    const element = fixture.nativeElement as HTMLElement;
    return Array.from(element.querySelectorAll('button')).find((candidate) =>
      candidate.textContent?.includes('Rozpocznij partię'),
    )!;
  }

  async function chooseFaction(fixture: ComponentFixture<NewMatch>, id: string): Promise<void> {
    const element = fixture.nativeElement as HTMLElement;
    element.querySelector<HTMLInputElement>(`input[type="radio"][value="${id}"]`)!.click();
    await fixture.whenStable();
  }

  it('renders the factions with name and description', async () => {
    const element = (await renderWithFactions()).nativeElement as HTMLElement;

    expect(element.querySelectorAll('input[type="radio"]').length).toBe(2);
    expect(element.textContent).toContain('Developerzy');
    expect(element.textContent).toContain('Budują szybko.');
    expect(element.textContent).toContain('Testerzy');
    expect(element.textContent).toContain('Znajdują każdy błąd.');
  });

  it('disables the start button until a faction is chosen', async () => {
    const fixture = await renderWithFactions();

    expect(startButton(fixture).disabled).toBe(true);

    await chooseFaction(fixture, 'testers');
    expect(startButton(fixture).disabled).toBe(false);
  });

  it('starts the match with the chosen faction and navigates to /match', async () => {
    const navigateByUrl = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    const fixture = await renderWithFactions();

    await chooseFaction(fixture, 'developers');
    startButton(fixture).click();
    const request = httpTesting.expectOne({ method: 'POST', url: '/api/matches' });
    expect(request.request.body).toEqual({ factionId: 'developers' });
    request.flush(MATCH, { status: 201, statusText: 'Created' });

    expect(navigateByUrl).toHaveBeenCalledWith('/match');
  });

  it('hands the start response to the table before navigating', async () => {
    const navigateByUrl = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    const setStartedMatch = vi.spyOn(TestBed.inject(GameService), 'setStartedMatch');
    const fixture = await renderWithFactions();

    await chooseFaction(fixture, 'developers');
    startButton(fixture).click();
    httpTesting
      .expectOne({ method: 'POST', url: '/api/matches' })
      .flush(MATCH, { status: 201, statusText: 'Created' });

    expect(setStartedMatch).toHaveBeenCalledWith(MATCH);
    expect(setStartedMatch.mock.invocationCallOrder[0]).toBeLessThan(
      navigateByUrl.mock.invocationCallOrder[0],
    );
    expect(TestBed.inject(GameService).takeStartedMatch()).toEqual(MATCH);
  });

  it('disables the start button while the request is in flight', async () => {
    vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    const fixture = await renderWithFactions();

    await chooseFaction(fixture, 'developers');
    startButton(fixture).click();
    await fixture.whenStable();
    expect(startButton(fixture).disabled).toBe(true);

    httpTesting
      .expectOne({ method: 'POST', url: '/api/matches' })
      .flush('Server Error', { status: 500, statusText: 'Internal Server Error' });
    await fixture.whenStable();
    expect(startButton(fixture).disabled).toBe(false);
  });

  it('shows an error and does not navigate when starting fails with 500', async () => {
    const navigateByUrl = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    const fixture = await renderWithFactions();

    await chooseFaction(fixture, 'developers');
    startButton(fixture).click();
    httpTesting
      .expectOne({ method: 'POST', url: '/api/matches' })
      .flush('Server Error', { status: 500, statusText: 'Internal Server Error' });
    await fixture.whenStable();

    expect(navigateByUrl).not.toHaveBeenCalled();
    expect((fixture.nativeElement as HTMLElement).textContent).toContain(
      'Nie udało się rozpocząć partii — spróbuj ponownie.',
    );
  });

  it('shows an error when the factions fail to load with 500', async () => {
    const fixture = await render();

    httpTesting
      .expectOne({ method: 'GET', url: '/api/factions' })
      .flush('Server Error', { status: 500, statusText: 'Internal Server Error' });
    await fixture.whenStable();

    expect((fixture.nativeElement as HTMLElement).textContent).toContain(
      'Nie udało się wczytać frakcji — odśwież stronę.',
    );
  });

  it('links back to the lobby', async () => {
    const element = (await renderWithFactions()).nativeElement as HTMLElement;

    const link = Array.from(element.querySelectorAll('a')).find((candidate) =>
      candidate.textContent?.includes('Wróć do lobby'),
    );
    expect(link?.getAttribute('href')).toBe('/');
  });

  it('renders in English when English is chosen', async () => {
    TestBed.inject(LanguageService).setLanguage('en');
    const fixture = await render();
    httpTesting.expectOne({ method: 'GET', url: '/api/factions' }).flush(FACTIONS_EN);
    await fixture.whenStable();
    const element = fixture.nativeElement as HTMLElement;

    expect(element.querySelector('h1')?.textContent).toContain('New match');
    expect(element.querySelector('legend')?.textContent).toContain('Choose a faction');
    expect(element.querySelector('button')?.textContent).toContain('Start match');
    expect(element.querySelector('a')?.textContent).toContain('Back to lobby');
    expect(element.textContent).toContain('Developers');
  });

  it('refetches the factions once on a language change and keeps the chosen faction', async () => {
    const fixture = await renderWithFactions();
    const element = fixture.nativeElement as HTMLElement;
    await chooseFaction(fixture, 'testers');

    TestBed.inject(LanguageService).setLanguage('en');
    await fixture.whenStable();
    httpTesting.expectOne({ method: 'GET', url: '/api/factions' }).flush(FACTIONS_EN);
    await fixture.whenStable();

    expect(element.querySelector('h1')?.textContent).toContain('New match');
    expect(element.querySelector('button')?.textContent).toContain('Start match');
    expect(element.textContent).toContain('Testers');
    expect(element.textContent).toContain('They find every bug.');
    expect(element.textContent).not.toContain('Testerzy');
    expect(
      element.querySelector<HTMLInputElement>('input[type="radio"][value="testers"]')!.checked,
    ).toBe(true);
    expect(element.querySelector('button')!.disabled).toBe(false);
  });

  it('cancels in-flight factions requests on rapid language switches and renders only the last', async () => {
    const fixture = await render();
    const element = fixture.nativeElement as HTMLElement;
    const language = TestBed.inject(LanguageService);

    language.setLanguage('en');
    await fixture.whenStable();
    language.setLanguage('pl');
    await fixture.whenStable();

    const requests = httpTesting.match({ method: 'GET', url: '/api/factions' });
    expect(requests.length).toBe(3);
    expect(requests[0].cancelled).toBe(true);
    expect(requests[1].cancelled).toBe(true);
    expect(requests[2].cancelled).toBe(false);
    requests[2].flush(FACTIONS);
    await fixture.whenStable();

    expect(element.textContent).toContain('Developerzy');
    expect(element.textContent).not.toContain('Developers');
  });

  it('drops the Polish factions and disables start when the refetch after switching fails', async () => {
    const fixture = await renderWithFactions();
    const element = fixture.nativeElement as HTMLElement;
    await chooseFaction(fixture, 'testers');

    TestBed.inject(LanguageService).setLanguage('en');
    await fixture.whenStable();
    httpTesting
      .expectOne({ method: 'GET', url: '/api/factions' })
      .flush('Server Error', { status: 500, statusText: 'Internal Server Error' });
    await fixture.whenStable();

    expect(element.querySelectorAll('input[type="radio"]').length).toBe(0);
    expect(element.textContent).not.toContain('Testerzy');
    expect(element.querySelector('button')!.disabled).toBe(true);
    expect(element.textContent).toContain('Could not load factions — refresh the page.');
  });

  it('shows a factions load error in English after switching from Polish', async () => {
    const fixture = await render();
    const element = fixture.nativeElement as HTMLElement;
    httpTesting
      .expectOne({ method: 'GET', url: '/api/factions' })
      .flush('Server Error', { status: 500, statusText: 'Internal Server Error' });
    await fixture.whenStable();
    expect(element.textContent).toContain('Nie udało się wczytać frakcji — odśwież stronę.');

    TestBed.inject(LanguageService).setLanguage('en');
    await fixture.whenStable();

    expect(element.textContent).toContain('Could not load factions — refresh the page.');
    httpTesting
      .expectOne({ method: 'GET', url: '/api/factions' })
      .flush('Server Error', { status: 500, statusText: 'Internal Server Error' });
    await fixture.whenStable();
    expect(element.textContent).toContain('Could not load factions — refresh the page.');
  });
});
