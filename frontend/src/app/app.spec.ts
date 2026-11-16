import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { App } from './app';

describe('App', () => {
  let httpTesting: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [App],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();

    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpTesting.verify();
  });

  it('shows "API: ok" when ping succeeds', async () => {
    const fixture = TestBed.createComponent(App);
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('h1')?.textContent).toContain('ItCardBrawl');
    expect(compiled.textContent).toContain('Łączenie z API…');

    httpTesting.expectOne('/api/ping').flush({ status: 'ok' });
    await fixture.whenStable();

    expect(compiled.textContent).toContain('API: ok');
  });

  it('shows "API niedostępne" when ping fails', async () => {
    const fixture = TestBed.createComponent(App);
    fixture.detectChanges();

    httpTesting
      .expectOne('/api/ping')
      .flush('Gateway Timeout', { status: 504, statusText: 'Gateway Timeout' });
    await fixture.whenStable();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('API niedostępne');
  });
});
