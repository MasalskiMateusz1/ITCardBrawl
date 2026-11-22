import { ComponentFixture, TestBed } from '@angular/core/testing';
import { LANGUAGE_STORAGE_KEY } from '../../i18n/language.service';
import { Card } from './card';

type Inputs = Record<string, unknown>;

const CHARACTER: Inputs = {
  cardId: 'dev-junior',
  faction: 'developers',
  type: 'CHARACTER',
  name: 'Junior Dev',
  cost: 1,
  attack: 1,
  health: 2,
  baseAttack: 1,
  baseHealth: 2,
  ability: 'Pisze kod.',
  text: 'Pierwszy dzień w pracy.',
};

const EVENT: Inputs = {
  cardId: 'neu-coffee',
  faction: 'neutral',
  type: 'EVENT',
  name: 'Kawa z automatu',
  cost: 1,
  duration: 2,
  effects: ['Dodaj 2 do budżetu w tej turze', 'Dobierz kartę'],
};

describe('Card', () => {
  let fixture: ComponentFixture<Card>;

  beforeEach(async () => {
    localStorage.setItem(LANGUAGE_STORAGE_KEY, 'pl');
    await TestBed.configureTestingModule({ imports: [Card] }).compileComponents();
  });

  afterEach(() => {
    localStorage.clear();
  });

  async function render(inputs: Inputs): Promise<HTMLElement> {
    fixture = TestBed.createComponent(Card);
    for (const [key, value] of Object.entries(inputs)) {
      fixture.componentRef.setInput(key, value);
    }
    await fixture.whenStable();
    return fixture.nativeElement as HTMLElement;
  }

  async function update(inputs: Inputs): Promise<void> {
    for (const [key, value] of Object.entries(inputs)) {
      fixture.componentRef.setInput(key, value);
    }
    await fixture.whenStable();
  }

  function text(el: Element | null): string {
    return el?.textContent?.replace(/\s+/g, ' ').trim() ?? '';
  }

  it('sets data-faction on the host and names the article after the card', async () => {
    const host = await render(CHARACTER);

    expect(host.getAttribute('data-faction')).toBe('developers');
    expect(host.querySelector('article')?.getAttribute('aria-label')).toBe('Junior Dev');

    await update({ faction: 'testers' });
    expect(host.getAttribute('data-faction')).toBe('testers');
  });

  it('renders a full character with cost, stats, type line, ability and text', async () => {
    const host = await render(CHARACTER);

    expect(host.classList).not.toContain('compact');
    expect(host.querySelector('.cost')?.getAttribute('aria-label')).toBe('Koszt 1');
    expect(text(host.querySelector('h3'))).toBe('Junior Dev');
    expect(host.querySelector('.stat.attack')?.getAttribute('aria-label')).toBe('Atak 1');
    expect(host.querySelector('.stat.hp')?.getAttribute('aria-label')).toBe('HP 2');
    expect(text(host.querySelector('.type-line'))).toBe('Postać · Developerzy');
    expect(text(host.querySelector('.ability'))).toBe('Pisze kod.');
    expect(text(host.querySelector('.text'))).toBe('Pierwszy dzień w pracy.');
    expect(text(host.querySelector('.art-label'))).toBe('Ilustracja');
    expect(host.querySelector('.chip.time')).toBeNull();
    expect(host.querySelector('.line')).toBeNull();
  });

  it('renders a full event with effects and duration, without stat badges', async () => {
    const host = await render(EVENT);

    expect(host.querySelector('.stat')).toBeNull();
    expect(text(host.querySelector('.type-line'))).toBe('Wydarzenie · Neutralne');
    expect(Array.from(host.querySelectorAll('.effect')).map(text)).toEqual([
      'Dodaj 2 do budżetu w tej turze',
      'Dobierz kartę',
    ]);
    expect(text(host.querySelector('.chip.time'))).toBe('2 tury');
  });

  it('shows instant for an event with duration 0 and turns left for an active event', async () => {
    const host = await render({ ...EVENT, duration: 0 });
    expect(text(host.querySelector('.chip.time'))).toBe('Natychmiast');

    await update({ duration: 5, turnsLeft: 1 });
    expect(text(host.querySelector('.chip.time'))).toBe('Zostało: 1 tura');
  });

  it('renders the compact variant with the compact line instead of the full body', async () => {
    const host = await render({ ...CHARACTER, variant: 'compact' });

    expect(host.classList).toContain('compact');
    expect(host.querySelector('.type-line')).toBeNull();
    expect(host.querySelector('.ability')).toBeNull();
    expect(host.querySelector('.art-label')).toBeNull();
    expect(host.querySelector('.stat.attack')).not.toBeNull();
    expect(text(host.querySelector('.line'))).toBe('Postać');
  });

  it('orders the compact line: trapped, then keyword, then event time', async () => {
    const host = await render({ ...CHARACTER, variant: 'compact', keyword: 'Ochrona' });
    expect(text(host.querySelector('.line'))).toBe('Ochrona');

    await update({ trapped: true });
    expect(text(host.querySelector('.line'))).toBe('Uwięziona');
    expect(host.querySelector('.line')?.classList).toContain('trapped-chip');

    const event = await render({ ...EVENT, variant: 'compact', turnsLeft: 3 });
    expect(text(event.querySelector('.line'))).toBe('Zostało: 3 tury');
  });

  it('shows a trapped chip on the full card', async () => {
    const host = await render(CHARACTER);
    expect(host.querySelector('.trapped-chip')).toBeNull();

    await update({ trapped: true });
    expect(text(host.querySelector('.trapped-chip'))).toBe('Uwięziona');
  });

  it('marks a modified attack and a damaged or modified health', async () => {
    const host = await render(CHARACTER);
    expect(host.querySelector('.stat.attack')?.classList).not.toContain('marked');
    expect(host.querySelector('.stat.hp')?.classList).not.toContain('marked');

    await update({ attack: 3 });
    expect(host.querySelector('.stat.attack')?.classList).toContain('marked');
    expect(text(host.querySelector('.stat.attack'))).toBe('3');
    expect(host.querySelector('.stat.hp')?.classList).not.toContain('marked');

    await update({ attack: 1, damaged: true });
    expect(host.querySelector('.stat.attack')?.classList).not.toContain('marked');
    expect(host.querySelector('.stat.hp')?.classList).toContain('marked');

    await update({ damaged: false, health: 4 });
    expect(host.querySelector('.stat.hp')?.classList).toContain('marked');
  });

  it('dims a card that is not playable and rings a selected one', async () => {
    const host = await render(CHARACTER);
    const article = host.querySelector('article')!;
    expect(article.classList).not.toContain('not-playable');
    expect(article.classList).not.toContain('selected');

    await update({ playable: false, selected: true });
    expect(article.classList).toContain('not-playable');
    expect(article.classList).toContain('selected');
  });

  it('loads the art from the cardId with an empty alt and the placeholder behind it', async () => {
    const host = await render(CHARACTER);
    const img = host.querySelector('.art img') as HTMLImageElement;

    expect(img.getAttribute('src')).toBe('/media/cards/dev-junior.webp');
    expect(img.getAttribute('alt')).toBe('');
    expect(img.getAttribute('loading')).toBe('lazy');
    expect(host.querySelector('.art .placeholder')).not.toBeNull();
  });

  it('falls back to the placeholder after an image error and resets on a new cardId', async () => {
    const host = await render(CHARACTER);
    host.querySelector('.art img')!.dispatchEvent(new Event('error'));
    await fixture.whenStable();

    expect(host.querySelector('.art img')).toBeNull();
    expect(host.querySelector('.art .placeholder')).not.toBeNull();

    await update({ cardId: 'dev-senior' });
    expect(host.querySelector('.art img')?.getAttribute('src')).toBe(
      '/media/cards/dev-senior.webp',
    );
  });

  it('uses English labels when the language is English', async () => {
    localStorage.setItem(LANGUAGE_STORAGE_KEY, 'en');
    // The language is read once per injector, so start a fresh one.
    TestBed.resetTestingModule();
    await TestBed.configureTestingModule({ imports: [Card] }).compileComponents();
    const host = await render({ ...EVENT, duration: 1 });

    expect(host.querySelector('.cost')?.getAttribute('aria-label')).toBe('Cost 1');
    expect(text(host.querySelector('.type-line'))).toBe('Event · Neutral');
    expect(text(host.querySelector('.chip.time'))).toBe('1 turn');
  });
});
