import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { SignIn } from './sign-in';

describe('SignIn', () => {
  let httpTesting: HttpTestingController;

  beforeEach(async () => {
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
  });

  async function render(url: string): Promise<HTMLElement> {
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl(url, SignIn);
    return harness.routeNativeElement as HTMLElement;
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

  it('shows no message without query params', async () => {
    const element = await render('/sign-in');

    expect(element.querySelector('.message')).toBeNull();
  });
});
