import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { LANGUAGE_STORAGE_KEY, LanguageService } from '../i18n/language.service';
import { SignIn } from './sign-in';

describe('SignIn', () => {
  let httpTesting: HttpTestingController;

  beforeEach(async () => {
    // Polish before LanguageService is created (jsdom reports navigator.language = en-US).
    localStorage.setItem(LANGUAGE_STORAGE_KEY, 'pl');
    await TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([{ path: 'sign-in', component: SignIn }]),
      ],
    }).compileComponents();

    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpTesting.verify();
    localStorage.clear();
  });

  async function renderHarness(url: string): Promise<RouterTestingHarness> {
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl(url, SignIn);
    return harness;
  }

  async function render(url: string): Promise<HTMLElement> {
    return (await renderHarness(url)).routeNativeElement as HTMLElement;
  }

  it('links to both OAuth providers', async () => {
    const element = await render('/sign-in');

    const links = Array.from(element.querySelectorAll('a'));
    expect(links.map((link) => link.getAttribute('href'))).toEqual([
      '/oauth2/authorization/github',
      '/oauth2/authorization/google',
    ]);
    expect(links[0].textContent).toContain('Zaloguj przez GitHub');
    expect(links[1].textContent).toContain('Zaloguj przez Google');
  });

  it('shows the login failure message for ?error', async () => {
    const element = await render('/sign-in?error');

    expect(element.textContent).toContain('Logowanie nie powiodło się — spróbuj ponownie.');
  });

  it('shows the server unavailable message for ?unavailable', async () => {
    const element = await render('/sign-in?unavailable');

    expect(element.textContent).toContain('Serwer gry niedostępny — spróbuj później.');
  });

  it('shows the logout confirmation for ?logout', async () => {
    const element = await render('/sign-in?logout');

    expect(element.textContent).toContain('Wylogowano.');
  });

  it('shows no message without query params', async () => {
    const element = await render('/sign-in');

    expect(element.querySelector('.message')).toBeNull();
  });

  it('renders in English when English is chosen', async () => {
    TestBed.inject(LanguageService).setLanguage('en');

    const element = await render('/sign-in?error');

    const links = Array.from(element.querySelectorAll('a'));
    expect(links[0].textContent).toContain('Sign in with GitHub');
    expect(links[1].textContent).toContain('Sign in with Google');
    expect(element.textContent).toContain('Sign-in failed — please try again.');
  });

  it('switches the texts and the message live when the language changes', async () => {
    const harness = await renderHarness('/sign-in?logout');
    const element = harness.routeNativeElement as HTMLElement;
    expect(element.textContent).toContain('Wylogowano.');

    TestBed.inject(LanguageService).setLanguage('en');
    await harness.fixture.whenStable();

    expect(element.textContent).toContain('Signed out.');
    expect(element.textContent).toContain('Sign in with GitHub');
    expect(element.textContent).not.toContain('Zaloguj przez GitHub');
  });
});
