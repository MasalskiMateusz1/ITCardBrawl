import { TestBed } from '@angular/core/testing';
import { en } from './en';
import { LANGUAGE_STORAGE_KEY, LanguageService } from './language.service';
import { pl } from './pl';

describe('LanguageService', () => {
  afterEach(() => {
    localStorage.clear();
    vi.restoreAllMocks();
  });

  function browserLanguage(language: string): void {
    vi.spyOn(navigator, 'language', 'get').mockReturnValue(language);
  }

  it('defaults to Polish when nothing is stored and the browser language is pl-PL', () => {
    browserLanguage('pl-PL');

    const service = TestBed.inject(LanguageService);

    expect(service.language()).toBe('pl');
    expect(service.texts()).toBe(pl);
    expect(document.documentElement.lang).toBe('pl');
  });

  it.each(['en-US', 'de'])('defaults to English for browser language %s', (language) => {
    browserLanguage(language);

    const service = TestBed.inject(LanguageService);

    expect(service.language()).toBe('en');
    expect(service.texts()).toBe(en);
    expect(document.documentElement.lang).toBe('en');
  });

  it('prefers the stored language over the browser language', () => {
    browserLanguage('pl-PL');
    localStorage.setItem(LANGUAGE_STORAGE_KEY, 'en');

    expect(TestBed.inject(LanguageService).language()).toBe('en');
  });

  it('ignores an unknown stored value', () => {
    browserLanguage('pl-PL');
    localStorage.setItem(LANGUAGE_STORAGE_KEY, 'fr');

    expect(TestBed.inject(LanguageService).language()).toBe('pl');
  });

  it('setLanguage stores the choice and switches texts and <html lang>', () => {
    browserLanguage('pl-PL');
    const service = TestBed.inject(LanguageService);

    service.setLanguage('en');

    expect(localStorage.getItem(LANGUAGE_STORAGE_KEY)).toBe('en');
    expect(service.language()).toBe('en');
    expect(service.texts()).toBe(en);
    expect(document.documentElement.lang).toBe('en');
  });

  it('keeps working in memory when localStorage throws', () => {
    browserLanguage('pl-PL');
    vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => {
      throw new Error('denied');
    });
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
      throw new Error('denied');
    });
    const service = TestBed.inject(LanguageService);
    expect(service.language()).toBe('pl');

    service.setLanguage('en');

    expect(service.language()).toBe('en');
    expect(document.documentElement.lang).toBe('en');
  });
});
