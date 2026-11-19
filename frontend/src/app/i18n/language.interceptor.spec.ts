import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { languageInterceptor } from './language.interceptor';
import { LANGUAGE_STORAGE_KEY, LanguageService } from './language.service';

describe('languageInterceptor', () => {
  let httpTesting: HttpTestingController;

  beforeEach(() => {
    localStorage.setItem(LANGUAGE_STORAGE_KEY, 'pl');
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([languageInterceptor])),
        provideHttpClientTesting(),
      ],
    });
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpTesting.verify();
    localStorage.clear();
  });

  function get(url: string): void {
    TestBed.inject(HttpClient).get(url).subscribe();
  }

  it('sends the current language with API requests and follows setLanguage', () => {
    get('/api/factions');
    const first = httpTesting.expectOne('/api/factions');
    expect(first.request.headers.get('Accept-Language')).toBe('pl');
    first.flush([]);

    TestBed.inject(LanguageService).setLanguage('en');
    get('/api/factions');
    const second = httpTesting.expectOne('/api/factions');
    expect(second.request.headers.get('Accept-Language')).toBe('en');
    second.flush([]);
  });

  it('leaves non-API requests unchanged', () => {
    get('/assets/data.json');
    const req = httpTesting.expectOne('/assets/data.json');
    expect(req.request.headers.has('Accept-Language')).toBe(false);
    req.flush({});
  });
});
