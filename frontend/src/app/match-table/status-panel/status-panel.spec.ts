import { Component, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { BudgetView } from '../../game/match';
import { LANGUAGE_STORAGE_KEY } from '../../i18n/language.service';
import { StatusPanel } from './status-panel';

@Component({
  imports: [StatusPanel],
  template: `
    <app-status-panel
      label="Twój status"
      factionId="backend"
      factionName="Backendowcy"
      [hp]="hp()"
      [maxHp]="30"
      [budget]="budget()"
      [deckCount]="12"
      [handCount]="handCount()"
      [graveyardCount]="3"
      [graveyardOpen]="graveyardOpen()"
      [detailed]="detailed()"
      [targetLabel]="targetLabel()"
      (targeted)="targetedCount = targetedCount + 1"
      (targetHover)="hovers.push($event)"
      (graveyardToggle)="toggleCount = toggleCount + 1"
    />
  `,
})
class Host {
  readonly hp = signal(24);
  readonly budget = signal<BudgetView>({ current: 2, max: 4 });
  readonly handCount = signal<number | null>(null);
  readonly graveyardOpen = signal(false);
  readonly detailed = signal(false);
  readonly targetLabel = signal<string | null>(null);
  targetedCount = 0;
  toggleCount = 0;
  hovers: boolean[] = [];
}

describe('StatusPanel', () => {
  beforeEach(async () => {
    // Polish before LanguageService is created (jsdom reports navigator.language = en-US).
    localStorage.setItem(LANGUAGE_STORAGE_KEY, 'pl');
    await TestBed.configureTestingModule({ imports: [Host] }).compileComponents();
  });

  afterEach(() => {
    localStorage.clear();
  });

  async function render(setup: (host: Host) => void = () => {}): Promise<ComponentFixture<Host>> {
    const fixture = TestBed.createComponent(Host);
    setup(fixture.componentInstance);
    await fixture.whenStable();
    return fixture;
  }

  function panel(fixture: ComponentFixture<Host>): HTMLElement {
    return (fixture.nativeElement as HTMLElement).querySelector('app-status-panel')!;
  }

  function text(element: Element | null): string {
    return element?.textContent?.trim() ?? '';
  }

  it('shows the name, HP, budget, deck and graveyard counts', async () => {
    const host = panel(await render());

    const section = host.querySelector('section')!;
    expect(section.getAttribute('aria-label')).toBe('Twój status');
    expect(section.getAttribute('data-faction')).toBe('backend');
    expect(text(host.querySelector('.name'))).toBe('Backendowcy');
    expect(text(host.querySelector('.hp dd'))).toBe('24/30');
    expect(text(host.querySelector('.budget dd'))).toBe('2/4');
    expect(text(host.querySelector('.deck-count dd'))).toBe('12');
    expect(text(host.querySelector('.graveyard dd'))).toBe('3');
    // Hand count only for the opponent; pips and HP bar only for the detailed (player) panel.
    expect(host.querySelector('.hand-count')).toBeNull();
    expect(host.querySelector('.hp-bar')).toBeNull();
    expect(host.querySelector('.pips')).toBeNull();
  });

  it('shows the hand count when given', async () => {
    const host = panel(await render((h) => h.handCount.set(5)));

    expect(text(host.querySelector('.hand-count dd'))).toBe('5');
  });

  it('adds budget pips and an HP bar when detailed', async () => {
    const host = panel(
      await render((h) => {
        h.detailed.set(true);
        h.hp.set(15);
      }),
    );

    const pips = Array.from(host.querySelectorAll('.pip'));
    expect(pips.map((pip) => pip.classList.contains('filled'))).toEqual([true, true, false, false]);
    expect(host.querySelector<HTMLElement>('.hp-bar span')!.style.width).toBe('50%');
  });

  it('clamps the HP bar at 0% when HP is negative', async () => {
    const host = panel(
      await render((h) => {
        h.detailed.set(true);
        h.hp.set(-3);
      }),
    );

    expect(host.querySelector<HTMLElement>('.hp-bar span')!.style.width).toBe('0%');
  });

  it('renders the target button only while targetable and emits from it', async () => {
    const fixture = await render();
    expect(panel(fixture).querySelector('.status-target')).toBeNull();
    expect(panel(fixture).classList.contains('targetable')).toBe(false);

    fixture.componentInstance.targetLabel.set('Zaatakuj przeciwnika');
    await fixture.whenStable();

    const target = panel(fixture).querySelector<HTMLButtonElement>('.status-target')!;
    expect(target.getAttribute('aria-label')).toBe('Zaatakuj przeciwnika');
    expect(panel(fixture).classList.contains('targetable')).toBe(true);
    target.dispatchEvent(new MouseEvent('mouseenter'));
    target.click();
    target.dispatchEvent(new MouseEvent('mouseleave'));
    expect(fixture.componentInstance.targetedCount).toBe(1);
    expect(fixture.componentInstance.hovers).toEqual([true, false]);
  });

  it('opens the graveyard from its count button, with aria-expanded', async () => {
    const fixture = await render();
    const opener = () => panel(fixture).querySelector<HTMLButtonElement>('.graveyard-open')!;

    expect(opener().getAttribute('aria-label')).toBe('Cmentarz 3 — pokaż karty');
    expect(opener().getAttribute('aria-expanded')).toBe('false');
    opener().click();
    expect(fixture.componentInstance.toggleCount).toBe(1);

    fixture.componentInstance.graveyardOpen.set(true);
    await fixture.whenStable();
    expect(opener().getAttribute('aria-expanded')).toBe('true');
  });
});
