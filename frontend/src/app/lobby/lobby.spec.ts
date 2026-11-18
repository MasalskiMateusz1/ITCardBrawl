import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { AuthService, Player } from '../auth.service';
import { Lobby } from './lobby';

describe('Lobby', () => {
  let httpTesting: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Lobby],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();

    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpTesting.verify();
  });

  function signIn(player: Player): void {
    TestBed.inject(AuthService).loadMe().subscribe();
    httpTesting.expectOne('/api/me').flush(player);
  }

  async function render(): Promise<ComponentFixture<Lobby>> {
    const fixture = TestBed.createComponent(Lobby);
    await fixture.whenStable();
    return fixture;
  }

  function clickLogout(fixture: ComponentFixture<Lobby>): void {
    const element = fixture.nativeElement as HTMLElement;
    const button = Array.from(element.querySelectorAll('button')).find((candidate) =>
      candidate.textContent?.includes('Wyloguj'),
    );
    button?.click();
  }

  it('greets the player and shows the avatar', async () => {
    signIn({ provider: 'github', displayName: 'Ada', avatarUrl: 'https://example.com/ada.png' });

    const element = (await render()).nativeElement as HTMLElement;

    expect(element.textContent).toContain('Witaj, Ada');
    expect(element.querySelector('img')?.getAttribute('src')).toBe('https://example.com/ada.png');
  });

  it('renders no avatar when avatarUrl is null', async () => {
    signIn({ provider: 'google', displayName: 'Grace', avatarUrl: null });

    const element = (await render()).nativeElement as HTMLElement;

    expect(element.textContent).toContain('Witaj, Grace');
    expect(element.querySelector('img')).toBeNull();
  });

  it('logs out, clears the player and navigates to /sign-in?logout', async () => {
    signIn({ provider: 'github', displayName: 'Ada', avatarUrl: null });
    const navigateByUrl = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    const fixture = await render();

    clickLogout(fixture);
    httpTesting
      .expectOne({ method: 'POST', url: '/api/logout' })
      .flush(null, { status: 204, statusText: 'No Content' });

    expect(navigateByUrl).toHaveBeenCalledWith('/sign-in?logout');
    expect(TestBed.inject(AuthService).user()).toBeNull();
  });

  it('shows an error and stays in the lobby when logout fails with 500', async () => {
    signIn({ provider: 'github', displayName: 'Ada', avatarUrl: null });
    const navigateByUrl = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    const fixture = await render();

    clickLogout(fixture);
    httpTesting
      .expectOne({ method: 'POST', url: '/api/logout' })
      .flush('Server Error', { status: 500, statusText: 'Internal Server Error' });
    await fixture.whenStable();

    expect(navigateByUrl).not.toHaveBeenCalled();
    expect((fixture.nativeElement as HTMLElement).textContent).toContain(
      'Nie udało się wylogować — spróbuj ponownie.',
    );
  });
});
