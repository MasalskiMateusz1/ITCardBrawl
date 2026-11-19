import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { App } from './app';
import { LANGUAGE_STORAGE_KEY } from './i18n/language.service';

describe('App', () => {
  let httpTesting: HttpTestingController;

  beforeEach(async () => {
    localStorage.setItem(LANGUAGE_STORAGE_KEY, 'pl');
    await TestBed.configureTestingModule({
      imports: [App],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();

    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpTesting.verify();
    localStorage.clear();
  });

  it('renders the header with the brand and the language switch, then the router outlet', async () => {
    const fixture = TestBed.createComponent(App);
    await fixture.whenStable();

    const compiled = fixture.nativeElement as HTMLElement;
    const header = compiled.querySelector('header');
    expect(header?.querySelector('h1')?.textContent).toContain('ItCardBrawl');
    const switchButtons = Array.from(header?.querySelectorAll('app-language-switch button') ?? []);
    expect(switchButtons.map((button) => button.textContent?.trim())).toEqual(['PL', 'EN']);
    expect(compiled.querySelector('router-outlet')).not.toBeNull();
  });
});
