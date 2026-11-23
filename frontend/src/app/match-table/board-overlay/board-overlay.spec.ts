import { Component, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { LANGUAGE_STORAGE_KEY } from '../../i18n/language.service';
import { BoardOverlay } from './board-overlay';

@Component({
  imports: [BoardOverlay],
  template: `
    <app-board-overlay
      [title]="title()"
      [closable]="closable()"
      (closed)="closedCount = closedCount + 1"
    >
      <p class="content">Treść</p>
    </app-board-overlay>
  `,
})
class Host {
  readonly title = signal('Twój cmentarz');
  readonly closable = signal(true);
  closedCount = 0;
}

describe('BoardOverlay', () => {
  beforeEach(async () => {
    // Polish before LanguageService is created (jsdom reports navigator.language = en-US).
    localStorage.setItem(LANGUAGE_STORAGE_KEY, 'pl');
    await TestBed.configureTestingModule({ imports: [Host] }).compileComponents();
  });

  afterEach(() => {
    localStorage.clear();
  });

  async function render(closable = true): Promise<ComponentFixture<Host>> {
    const fixture = TestBed.createComponent(Host);
    fixture.componentInstance.closable.set(closable);
    await fixture.whenStable();
    return fixture;
  }

  function overlay(fixture: ComponentFixture<Host>): HTMLElement {
    return (fixture.nativeElement as HTMLElement).querySelector('app-board-overlay')!;
  }

  function escape(target: Element): void {
    target.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }));
  }

  it('renders a dialog labelled by its heading, with the projected content', async () => {
    const fixture = await render();
    const host = overlay(fixture);

    const heading = host.querySelector('h2')!;
    expect(host.getAttribute('role')).toBe('dialog');
    expect(heading.textContent?.trim()).toBe('Twój cmentarz');
    expect(heading.id).not.toBe('');
    expect(host.getAttribute('aria-labelledby')).toBe(heading.id);
    expect(host.querySelector('.body .content')?.textContent).toBe('Treść');
  });

  it('moves focus to its heading when it opens', async () => {
    const fixture = await render();

    expect(document.activeElement).toBe(overlay(fixture).querySelector('h2'));
  });

  it('emits closed from its close button', async () => {
    const fixture = await render();
    const close = overlay(fixture).querySelector<HTMLButtonElement>('.overlay-close')!;

    expect(close.textContent?.trim()).toBe('Zamknij');
    close.click();

    expect(fixture.componentInstance.closedCount).toBe(1);
  });

  it('emits closed on Escape inside the overlay', async () => {
    const fixture = await render();

    escape(overlay(fixture).querySelector('h2')!);

    expect(fixture.componentInstance.closedCount).toBe(1);
  });

  it('has no close button and ignores Escape when not closable', async () => {
    const fixture = await render(false);

    expect(overlay(fixture).querySelector('.overlay-close')).toBeNull();
    escape(overlay(fixture));
    expect(fixture.componentInstance.closedCount).toBe(0);
  });
});
