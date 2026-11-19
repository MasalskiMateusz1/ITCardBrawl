import { ComponentFixture, TestBed } from '@angular/core/testing';
import { LanguageSwitch } from './language-switch';
import { LANGUAGE_STORAGE_KEY, LanguageService } from './language.service';

describe('LanguageSwitch', () => {
  let fixture: ComponentFixture<LanguageSwitch>;

  beforeEach(async () => {
    localStorage.setItem(LANGUAGE_STORAGE_KEY, 'pl');
    await TestBed.configureTestingModule({ imports: [LanguageSwitch] }).compileComponents();
    fixture = TestBed.createComponent(LanguageSwitch);
    await fixture.whenStable();
  });

  afterEach(() => {
    localStorage.clear();
  });

  function element(): HTMLElement {
    return fixture.nativeElement as HTMLElement;
  }

  function buttons(): HTMLButtonElement[] {
    return Array.from(element().querySelectorAll('button'));
  }

  it('renders PL and EN in a labelled group with the current language pressed', () => {
    expect(element().querySelector('[role="group"]')?.getAttribute('aria-label')).toBe('Język');
    expect(buttons().map((b) => b.textContent?.trim())).toEqual(['PL', 'EN']);
    expect(buttons().map((b) => b.getAttribute('aria-pressed'))).toEqual(['true', 'false']);
  });

  it('switches to English on EN click', async () => {
    buttons()[1].click();
    await fixture.whenStable();

    expect(TestBed.inject(LanguageService).language()).toBe('en');
    expect(buttons().map((b) => b.getAttribute('aria-pressed'))).toEqual(['false', 'true']);
    expect(element().querySelector('[role="group"]')?.getAttribute('aria-label')).toBe('Language');
  });
});
