import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { AuthService, Player } from '../auth.service';
import { Lobby } from './lobby';

describe('Lobby', () => {
  let httpTesting: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Lobby],
      providers: [provideHttpClient(), provideHttpClientTesting()],
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

  async function render(): Promise<HTMLElement> {
    const fixture = TestBed.createComponent(Lobby);
    await fixture.whenStable();
    return fixture.nativeElement as HTMLElement;
  }

  it('greets the player and shows the avatar', async () => {
    signIn({ provider: 'github', displayName: 'Ada', avatarUrl: 'https://example.com/ada.png' });

    const element = await render();

    expect(element.textContent).toContain('Witaj, Ada');
    expect(element.querySelector('img')?.getAttribute('src')).toBe('https://example.com/ada.png');
  });

  it('renders no avatar when avatarUrl is null', async () => {
    signIn({ provider: 'google', displayName: 'Grace', avatarUrl: null });

    const element = await render();

    expect(element.textContent).toContain('Witaj, Grace');
    expect(element.querySelector('img')).toBeNull();
  });
});
