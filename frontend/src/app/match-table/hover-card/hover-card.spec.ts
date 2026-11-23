import { Component, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HoverAnchor, HoverCard, HoverPlacement } from './hover-card';

@Component({
  imports: [HoverCard],
  template: `
    <app-hover-card [anchor]="anchor()" [placement]="placement()" [scale]="scale()">
      <p class="content">Backendowiec</p>
    </app-hover-card>
  `,
})
class Host {
  readonly anchor = signal<HoverAnchor>({ left: 400, top: 500, width: 120, height: 168 });
  readonly placement = signal<HoverPlacement>('above');
  readonly scale = signal(1);
}

describe('HoverCard', () => {
  const viewport = { width: window.innerWidth, height: window.innerHeight };

  beforeEach(async () => {
    resize(1000, 800);
    await TestBed.configureTestingModule({ imports: [Host] }).compileComponents();
  });

  afterEach(() => {
    resize(viewport.width, viewport.height);
  });

  function resize(width: number, height: number): void {
    Object.defineProperty(window, 'innerWidth', { configurable: true, value: width });
    Object.defineProperty(window, 'innerHeight', { configurable: true, value: height });
  }

  async function render(setup: (host: Host) => void = () => {}): Promise<HTMLElement> {
    const fixture: ComponentFixture<Host> = TestBed.createComponent(Host);
    setup(fixture.componentInstance);
    await fixture.whenStable();
    return (fixture.nativeElement as HTMLElement).querySelector('app-hover-card')!;
  }

  it('renders the projected card, hidden from assistive tech', async () => {
    const host = await render();

    expect(host.getAttribute('aria-hidden')).toBe('true');
    expect(host.querySelector('.card .content')?.textContent).toBe('Backendowiec');
  });

  it('opens above the hovered card, centred on it, growing out of it', async () => {
    const host = await render();

    // Centre x 460 - 240/2 = 340; top 500 - 8 - 336 = 156.
    expect(host.style.left).toBe('340px');
    expect(host.style.top).toBe('156px');
    expect(host.style.transformOrigin).toBe('120px 428px');
    expect(host.style.getPropertyValue('--from-scale')).toBe('0.5');
  });

  it('opens below when preferred and it fits', async () => {
    const host = await render((h) => {
      h.placement.set('below');
      h.anchor.set({ left: 400, top: 100, width: 120, height: 168 });
    });

    expect(host.style.top).toBe('276px');
  });

  it('falls back below when there is no room above', async () => {
    const host = await render((h) => h.anchor.set({ left: 400, top: 50, width: 120, height: 168 }));

    expect(host.style.top).toBe('226px');
  });

  it('opens beside the card when neither side fits, clamped to the viewport', async () => {
    const host = await render((h) => h.anchor.set({ left: 0, top: 300, width: 120, height: 168 }));

    // More room on the right: 0 + 120 + 8; centre y 384 - 168 = 216.
    expect(host.style.left).toBe('128px');
    expect(host.style.top).toBe('216px');
  });

  it('grows with the board zoom', async () => {
    const host = await render((h) => h.scale.set(0.5));

    // Card 120×168: centre x 460 - 60 = 400; top 500 - 8 - 168 = 324; scale min(1, 120/120).
    expect(host.style.left).toBe('400px');
    expect(host.style.top).toBe('324px');
    expect(host.style.getPropertyValue('--from-scale')).toBe('1');
  });
});
