import { Component, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActiveEvent } from '../../game/match';
import { LANGUAGE_STORAGE_KEY } from '../../i18n/language.service';
import { ZoneSide } from './zone-side';

function activeEvent(instanceId: number, name: string): ActiveEvent {
  return {
    instanceId,
    cardId: `ev-${instanceId}`,
    name,
    turnsLeft: 2,
    effects: [{ kind: 'ATTACK', scope: 'OWN_BOARD', amount: 2, text: 'Twoje postacie: +2 ataku' }],
    targetName: null,
    cost: 3,
    faction: 'developers',
  };
}

@Component({
  imports: [ZoneSide],
  template: `
    <app-zone-side
      [events]="events()"
      [expanded]="expanded()"
      (toggled)="toggledCount = toggledCount + 1"
    />
  `,
})
class Host {
  readonly events = signal<ActiveEvent[]>([]);
  readonly expanded = signal(false);
  toggledCount = 0;
}

describe('ZoneSide', () => {
  beforeEach(async () => {
    // Polish before LanguageService is created (jsdom reports navigator.language = en-US).
    localStorage.setItem(LANGUAGE_STORAGE_KEY, 'pl');
    await TestBed.configureTestingModule({ imports: [Host] }).compileComponents();
  });

  afterEach(() => {
    localStorage.clear();
  });

  async function render(events: ActiveEvent[], expanded = false): Promise<ComponentFixture<Host>> {
    const fixture = TestBed.createComponent(Host);
    fixture.componentInstance.events.set(events);
    fixture.componentInstance.expanded.set(expanded);
    await fixture.whenStable();
    return fixture;
  }

  function stack(fixture: ComponentFixture<Host>): HTMLButtonElement | null {
    return (fixture.nativeElement as HTMLElement).querySelector('.events-open');
  }

  it('shows "brak" and no button without active events', async () => {
    const fixture = await render([]);
    const element = fixture.nativeElement as HTMLElement;

    expect(element.querySelector('.events-heading')?.textContent?.trim()).toBe(
      'Aktywne wydarzenia',
    );
    expect(element.querySelector('.none')?.textContent?.trim()).toBe('brak');
    expect(stack(fixture)).toBeNull();
  });

  it('draws at most three compact cards, the newest last (on top), and the full count', async () => {
    const fixture = await render([
      activeEvent(1, 'Pierwsze'),
      activeEvent(2, 'Drugie'),
      activeEvent(3, 'Trzecie'),
      activeEvent(4, 'Czwarte'),
    ]);
    const open = stack(fixture)!;

    expect(open.getAttribute('aria-label')).toBe('Aktywne wydarzenia 4 — pokaż karty');
    expect(open.getAttribute('aria-expanded')).toBe('false');
    expect(open.querySelector('.events-count')?.textContent?.trim()).toBe('4');
    const names = Array.from(open.querySelectorAll('app-card.compact article')).map((card) =>
      card.getAttribute('aria-label'),
    );
    expect(names).toEqual(['Drugie', 'Trzecie', 'Czwarte']);
    expect(open.querySelector('app-card .line')?.textContent?.trim()).toBe('Zostało: 2 tury');
  });

  it('reports a click and reflects the open overlay', async () => {
    const fixture = await render([activeEvent(1, 'Pierwsze')], true);

    expect(stack(fixture)!.getAttribute('aria-expanded')).toBe('true');
    stack(fixture)!.click();
    expect(fixture.componentInstance.toggledCount).toBe(1);
  });
});
