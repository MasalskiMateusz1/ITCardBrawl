import { Component, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { GraveyardCard } from '../../game/match';
import { LANGUAGE_STORAGE_KEY } from '../../i18n/language.service';
import { GraveyardList } from './graveyard-list';

const BACKENDER: GraveyardCard = {
  instanceId: 1,
  cardId: 'be-backender',
  name: 'Backendowiec',
  type: 'CHARACTER',
  cost: 3,
  faction: 'backend',
  attack: 3,
  health: 4,
  ability: { keyword: 'GUARD', text: 'Przeciwnik musi najpierw zaatakować tę postać' },
  duration: null,
  effects: [],
  text: 'Trzyma API przy życiu',
};

const HOTFIX: GraveyardCard = {
  instanceId: 2,
  cardId: 'be-hotfix',
  name: 'Hotfix',
  type: 'EVENT',
  cost: 2,
  faction: 'backend',
  attack: null,
  health: null,
  ability: null,
  duration: 0,
  effects: [{ kind: 'DAMAGE', scope: 'TARGET', amount: 2, text: 'Zadaj 2 obrażenia' }],
  text: '',
};

@Component({
  imports: [GraveyardList],
  template: `<app-graveyard-list [cards]="cards()" />`,
})
class Host {
  readonly cards = signal<GraveyardCard[]>([]);
}

describe('GraveyardList', () => {
  beforeEach(async () => {
    // Polish before LanguageService is created (jsdom reports navigator.language = en-US).
    localStorage.setItem(LANGUAGE_STORAGE_KEY, 'pl');
    await TestBed.configureTestingModule({ imports: [Host] }).compileComponents();
  });

  afterEach(() => {
    localStorage.clear();
  });

  async function render(cards: GraveyardCard[]): Promise<ComponentFixture<Host>> {
    const fixture = TestBed.createComponent(Host);
    fixture.componentInstance.cards.set(cards);
    await fixture.whenStable();
    return fixture;
  }

  function element(fixture: ComponentFixture<Host>): HTMLElement {
    return fixture.nativeElement as HTMLElement;
  }

  function text(node: Element | null): string {
    return node?.textContent?.trim() ?? '';
  }

  function toggles(fixture: ComponentFixture<Host>): HTMLButtonElement[] {
    return Array.from(element(fixture).querySelectorAll<HTMLButtonElement>('.card-toggle'));
  }

  it('shows "brak" for an empty graveyard', async () => {
    const fixture = await render([]);

    expect(text(element(fixture).querySelector('.none'))).toBe('brak');
    expect(element(fixture).querySelector('app-card')).toBeNull();
  });

  it('lists every card as a compact toggle, oldest first, none shown in full', async () => {
    const fixture = await render([BACKENDER, HOTFIX]);

    const items = Array.from(element(fixture).querySelectorAll('.graveyard-card'));
    expect(items.length).toBe(2);
    expect(items.every((item) => item.querySelector('app-card.compact') !== null)).toBe(true);
    expect(toggles(fixture).map((toggle) => toggle.getAttribute('aria-label'))).toEqual([
      'Pokaż kartę: Backendowiec',
      'Pokaż kartę: Hotfix',
    ]);
    expect(toggles(fixture).map((toggle) => toggle.getAttribute('aria-pressed'))).toEqual([
      'false',
      'false',
    ]);
    expect(element(fixture).querySelector('.details')).toBeNull();
  });

  it('shows the clicked card in full and hides it on a second click', async () => {
    const fixture = await render([BACKENDER, HOTFIX]);

    toggles(fixture)[0].click();
    await fixture.whenStable();
    const details = () => element(fixture).querySelector('app-card.details');
    expect(details()?.classList.contains('compact')).toBe(false);
    expect(details()?.querySelector('article')?.getAttribute('aria-label')).toBe('Backendowiec');
    expect(text(details()!.querySelector('.keyword'))).toBe('Ochrona');
    expect(text(details()!.querySelector('.ability'))).toBe(
      'Przeciwnik musi najpierw zaatakować tę postać',
    );
    expect(text(details()!.querySelector('.text'))).toBe('Trzyma API przy życiu');
    expect(toggles(fixture)[0].getAttribute('aria-pressed')).toBe('true');

    toggles(fixture)[1].click();
    await fixture.whenStable();
    expect(details()?.querySelector('article')?.getAttribute('aria-label')).toBe('Hotfix');
    expect(Array.from(details()!.querySelectorAll('.effect')).map(text)).toEqual([
      'Zadaj 2 obrażenia',
    ]);
    expect(toggles(fixture)[0].getAttribute('aria-pressed')).toBe('false');

    toggles(fixture)[1].click();
    await fixture.whenStable();
    expect(details()).toBeNull();
  });

  it('drops the shown card once it leaves the graveyard', async () => {
    const fixture = await render([BACKENDER, HOTFIX]);
    toggles(fixture)[0].click();
    await fixture.whenStable();

    fixture.componentInstance.cards.set([HOTFIX]);
    await fixture.whenStable();

    expect(element(fixture).querySelector('.details')).toBeNull();
  });
});
