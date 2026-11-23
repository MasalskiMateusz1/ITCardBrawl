import { Component, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { LANGUAGE_STORAGE_KEY } from '../../i18n/language.service';
import { TurnTimer } from './turn-timer';

@Component({
  imports: [TurnTimer],
  template: `<app-turn-timer [seconds]="seconds()" [limit]="limit()" />`,
})
class Host {
  readonly seconds = signal(45);
  readonly limit = signal(60);
}

describe('TurnTimer', () => {
  beforeEach(async () => {
    // Polish before LanguageService is created (jsdom reports navigator.language = en-US).
    localStorage.setItem(LANGUAGE_STORAGE_KEY, 'pl');
    await TestBed.configureTestingModule({ imports: [Host] }).compileComponents();
  });

  afterEach(() => {
    localStorage.clear();
  });

  async function render(seconds: number, limit: number): Promise<ComponentFixture<Host>> {
    const fixture = TestBed.createComponent(Host);
    fixture.componentInstance.seconds.set(seconds);
    fixture.componentInstance.limit.set(limit);
    await fixture.whenStable();
    return fixture;
  }

  function timer(fixture: ComponentFixture<Host>): HTMLElement {
    return (fixture.nativeElement as HTMLElement).querySelector('app-turn-timer')!;
  }

  function barWidth(fixture: ComponentFixture<Host>): string {
    return timer(fixture).querySelector<HTMLElement>('.bar span')!.style.width;
  }

  it('shows the seconds left and the bar as the share of the time limit', async () => {
    const fixture = await render(45, 60);

    expect(timer(fixture).textContent?.trim()).toBe('Pozostało: 45 s');
    expect(barWidth(fixture)).toBe('75%');
  });

  it('follows the countdown', async () => {
    const fixture = await render(45, 60);

    fixture.componentInstance.seconds.set(6);
    await fixture.whenStable();

    expect(timer(fixture).textContent?.trim()).toBe('Pozostało: 6 s');
    expect(barWidth(fixture)).toBe('10%');
  });

  it('clamps the bar to 0–100% and empties it without a limit', async () => {
    expect(barWidth(await render(90, 60))).toBe('100%');
    expect(barWidth(await render(-1, 60))).toBe('0%');
    expect(barWidth(await render(10, 0))).toBe('0%');
  });
});
