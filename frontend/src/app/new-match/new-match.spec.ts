import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { FactionSummary, MatchView } from '../game/match';
import { NewMatch } from './new-match';

const FACTIONS: FactionSummary[] = [
  { id: 'developers', name: 'Developerzy', description: 'Budują szybko.' },
  { id: 'testers', name: 'Testerzy', description: 'Znajdują każdy błąd.' },
];

const MATCH: MatchView = {
  matchId: '00000000-0000-0000-0000-000000000001',
  turn: { number: 1, active: 'PLAYER' },
  player: {
    faction: { id: 'developers', name: 'Developerzy' },
    stress: 0,
    stressLimit: 20,
    resource: { current: 1, max: 1 },
    deckCount: 15,
    hand: [],
  },
  opponent: {
    faction: { id: 'testers', name: 'Testerzy' },
    stress: 0,
    stressLimit: 20,
    resource: { current: 0, max: 0 },
    deckCount: 15,
    handCount: 5,
  },
};

describe('NewMatch', () => {
  let httpTesting: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [NewMatch],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();

    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpTesting.verify();
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
});
