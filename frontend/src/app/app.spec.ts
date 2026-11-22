import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { App } from './app';
import { routes } from './app.routes';
import { LANGUAGE_STORAGE_KEY } from './i18n/language.service';

@Component({ selector: 'app-stub', template: '' })
class Stub {}

describe('App', () => {
  let httpTesting: HttpTestingController;

  beforeEach(async () => {
    localStorage.setItem(LANGUAGE_STORAGE_KEY, 'pl');
    await TestBed.configureTestingModule({
      imports: [App],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([
          { path: '', component: Stub },
          { path: 'match', component: Stub, data: { fullscreen: true } },
        ]),
      ],
    }).compileComponents();

    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpTesting.verify();
    localStorage.clear();
  });

  it('renders the header with the brand and the language switch, then the router outlet', async () => {
    const fixture = TestBed.createComponent(App);
    await TestBed.inject(Router).navigateByUrl('/');
    await fixture.whenStable();

    const compiled = fixture.nativeElement as HTMLElement;
    const header = compiled.querySelector('header');
    expect(header?.querySelector('h1')?.textContent).toContain('ItCardBrawl');
    const switchButtons = Array.from(header?.querySelectorAll('app-language-switch button') ?? []);
    expect(switchButtons.map((button) => button.textContent?.trim())).toEqual(['PL', 'EN']);
    expect(compiled.querySelector('router-outlet')).not.toBeNull();
  });

  it('skips the header on /match, which owns the full viewport', async () => {
    expect(routes.find((route) => route.path === 'match')?.data).toEqual({ fullscreen: true });
    const fixture = TestBed.createComponent(App);
    const router = TestBed.inject(Router);

    await router.navigateByUrl('/match');
    await fixture.whenStable();
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('header')).toBeNull();
    expect(compiled.querySelector('router-outlet')).not.toBeNull();

    await router.navigateByUrl('/');
    await fixture.whenStable();
    expect(compiled.querySelector('header h1')?.textContent).toContain('ItCardBrawl');
  });
});
