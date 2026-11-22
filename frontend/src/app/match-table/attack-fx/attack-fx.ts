import { Component, Directive, ElementRef, effect, inject, input } from '@angular/core';

/** A point or offset on screen (px). */
export type FxPoint = { x: number; y: number };

/**
 * One element's part in an attack animation: the attacker lunges along `vector` (screen px) and
 * back, the target shakes and flashes at the impact. `key` tells two attacks apart.
 */
export type FxCue = { key: number; role: 'lunge' | 'hit'; vector: FxPoint };

/** A damage number rising above a hit card or panel (`x`/`y`: its top centre, screen px). */
export type DamageFloat = { key: string; x: number; y: number; amount: number };

/** The lunge out (ease-in) and back (ease-out), and when the impact lands within it (ms). */
const LUNGE_OUT_MS = 180;
const LUNGE_BACK_MS = 220;
/** How far the lunge travels towards the target, and how much the attacker grows. */
const LUNGE_SHARE = 0.7;
const LUNGE_SCALE = 1.08;
const HIT_MS = 320;
const LEAVE_MS = 320;

/** The player asked for less motion (no matchMedia, e.g. jsdom: no). */
export function prefersReducedMotion(element: Element): boolean {
  const view = element.ownerDocument.defaultView;
  return view?.matchMedia?.('(prefers-reduced-motion: reduce)').matches ?? false;
}

/**
 * Plays an attack cue on its host (a board character or a status panel) with the Web Animations
 * API, so the board needs no extra CSS. A new cue, or none, cuts the running one; reduced motion
 * plays nothing (the damage numbers still show).
 */
@Directive({
  selector: '[appFx]',
  host: { '[attr.data-fx]': 'appFx()?.role ?? null' },
})
export class FxDirective {
  readonly appFx = input<FxCue | null>(null);

  constructor() {
    const host = inject<ElementRef<HTMLElement>>(ElementRef).nativeElement;
    effect((onCleanup) => {
      const cue = this.appFx();
      if (cue === null || typeof host.animate !== 'function' || prefersReducedMotion(host)) {
        return;
      }
      const animation = cue.role === 'lunge' ? lunge(host, cue.vector) : hit(host);
      onCleanup(() => animation.cancel());
    });
  }
}

function lunge(host: HTMLElement, vector: FxPoint): Animation {
  const x = vector.x * LUNGE_SHARE;
  const y = vector.y * LUNGE_SHARE;
  const duration = LUNGE_OUT_MS + LUNGE_BACK_MS;
  return host.animate(
    [
      { transform: 'none', zIndex: 5, easing: 'ease-in' },
      {
        offset: LUNGE_OUT_MS / duration,
        transform: `translate(${x}px, ${y}px) scale(${LUNGE_SCALE})`,
        zIndex: 5,
        easing: 'ease-out',
      },
      { transform: 'none', zIndex: 5 },
    ],
    { duration },
  );
}

function hit(host: HTMLElement): Animation {
  const flash = 'drop-shadow(0 0 12px var(--color-danger)) brightness(1.35)';
  return host.animate(
    [
      { transform: 'none', filter: 'none' },
      { offset: 0.15, transform: 'translateX(-6px) rotate(-1.5deg)', filter: flash },
      { offset: 0.35, transform: 'translateX(5px) rotate(1deg)', filter: flash },
      { offset: 0.55, transform: 'translateX(-3px)' },
      { offset: 0.75, transform: 'translateX(2px)' },
      { transform: 'none', filter: 'none' },
    ],
    { duration: HIT_MS, delay: LUNGE_OUT_MS, easing: 'ease-out' },
  );
}

/**
 * A character leaving the board (killed, as a rule) fades and shrinks out before it is removed;
 * `delay` lets an attack's impact land first. Without animation support or with reduced motion it
 * is removed at once.
 */
export function leaveBoard(target: Element, done: () => void, delay: number): void {
  if (!(target instanceof HTMLElement) || typeof target.animate !== 'function') {
    done();
    return;
  }
  if (prefersReducedMotion(target)) {
    done();
    return;
  }
  const animation = target.animate(
    [
      { opacity: 1, transform: 'none', filter: 'none' },
      { opacity: 0, transform: 'scale(0.6) rotate(-4deg)', filter: 'grayscale(1)' },
    ],
    { duration: LEAVE_MS, delay, easing: 'ease-in', fill: 'forwards' },
  );
  animation.finished.then(done, done);
}

/** When a hit lands after the attack response (ms): the end of the lunge out. */
export const IMPACT_MS = LUNGE_OUT_MS;
/** How long an attack animation, damage numbers included, stays on the board (ms). */
export const ATTACK_FX_MS = 1200;

/**
 * The damage numbers of an attack ("−N"), rising and fading above the hit cards and panels.
 * Click-through and hidden from assistive tech: the battle log reports the damage.
 */
@Component({
  selector: 'app-damage-floats',
  styleUrl: './damage-floats.css',
  template: `
    @for (f of floats(); track f.key) {
      <span class="damage" [style.left.px]="f.x" [style.top.px]="f.y">−{{ f.amount }}</span>
    }
  `,
  host: { 'aria-hidden': 'true' },
})
export class DamageFloats {
  readonly floats = input<DamageFloat[]>([]);
}
