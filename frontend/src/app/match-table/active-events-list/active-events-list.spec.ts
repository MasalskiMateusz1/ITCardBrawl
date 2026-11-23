import { Component, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { ActiveEvent } from '../../game/match';
import { LANGUAGE_STORAGE_KEY } from '../../i18n/language.service';
import { ActiveEventsList } from './active-events-list';

const FLAKY: ActiveEvent = {
  instanceId: 1,
  cardId: 'tst-flaky-test',
  name: 'Niestabilny test',
  turnsLeft: 3,
  effects: [{ kind: 'TRAP', scope: 'TARGET', amount: 0, text: 'Uwięź wybraną postać' }],
  targetName: 'Backendowiec',
  cost: 4,
  faction: 'testers',
};

const REGRESSION: ActiveEvent = {
  instanceId: 2,
  cardId: 'tst-regression',
  name: 'Testy regresji',
  turnsLeft: 1,
  effects: [
    { kind: 'HEALTH', scope: 'OWN_BOARD', amount: 2, text: 'Twoje postacie: +2 zdrowia' },
    { kind: 'GUARD', scope: 'OWN_BOARD', amount: 0, text: 'Twoje postacie: Ochrona' },
  ],
  targetName: null,
  cost: 4,
  faction: 'testers',
};

@Component({
  imports: [ActiveEventsList],
  template: `<app-active-events-list [events]="events()" />`,
})
class Host {
  readonly events = signal<ActiveEvent[]>([]);
}

describe('ActiveEventsList', () => {
  beforeEach(async () => {
    // Polish before LanguageService is created (jsdom reports navigator.language = en-US).
    localStorage.setItem(LANGUAGE_STORAGE_KEY, 'pl');
    await TestBed.configureTestingModule({ imports: [Host] }).compileComponents();
  });

  afterEach(() => {
    localStorage.clear();
  });

  async function render(events: ActiveEvent[]): Promise<HTMLElement> {
    const fixture = TestBed.createComponent(Host);
    fixture.componentInstance.events.set(events);
    await fixture.whenStable();
    return fixture.nativeElement as HTMLElement;
  }

  function text(element: Element | null): string {
    return element?.textContent?.trim() ?? '';
  }

  it('shows "brak" without active events', async () => {
    const element = await render([]);

    expect(text(element.querySelector('.none'))).toBe('brak');
    expect(element.querySelector('app-card')).toBeNull();
  });

  it('lists every event as a full card with its effects, turns left and target', async () => {
    const element = await render([FLAKY, REGRESSION]);

    const items = Array.from(element.querySelectorAll('.active-event'));
    expect(items.length).toBe(2);
    expect(element.querySelector('app-card.compact')).toBeNull();
    expect(items.map((item) => item.querySelector('article')?.getAttribute('aria-label'))).toEqual([
      'Niestabilny test',
      'Testy regresji',
    ]);
    expect(items[0].querySelector('app-card')?.getAttribute('data-faction')).toBe('testers');
    expect(Array.from(items[1].querySelectorAll('.effect')).map(text)).toEqual([
      'Twoje postacie: +2 zdrowia',
      'Twoje postacie: Ochrona',
    ]);
    expect(text(items[0].querySelector('.chip.time'))).toBe('Zostało: 3 tury');
    expect(text(items[1].querySelector('.chip.time'))).toBe('Zostało: 1 tura');
    // Turns left only on the card's chip; the caption names the target, if any.
    expect(element.querySelector('.turns')).toBeNull();
    expect(text(items[0].querySelector('.target'))).toBe('Cel: Backendowiec');
    expect(items[1].querySelector('.target')).toBeNull();
  });
});
