import { Component, computed, input } from '@angular/core';

/** The viewport rectangle (screen px) of the hovered card. */
export type HoverAnchor = { left: number; top: number; width: number; height: number };

/** Preferred side of the hovered card the enlarged card opens on. */
export type HoverPlacement = 'above' | 'below';

/** Size of the full card (ui/card `:host`) at `scale` 1. */
const CARD_W = 240;
const CARD_H = 336;
/** Distance to the hovered card and to the viewport edges. */
const GAP = 8;

/**
 * The enlarged (full) card shown while a card on the table is hovered or focused. Fixed to the
 * viewport at the hovered card: on the preferred side when it fits, else on the other one, else
 * beside it; always clamped inside the viewport. It grows out of the hovered card (scale from its
 * size, origin at the anchor). It is as large as the board's zoom (`scale`, `--ui-scale`), but its
 * position stays in screen px, so only the card inside is zoomed. Click-through and hidden from
 * assistive tech: the card button's accessible name already names the card.
 */
@Component({
  selector: 'app-hover-card',
  styleUrl: './hover-card.css',
  template: '<div class="card"><ng-content /></div>',
  host: {
    'aria-hidden': 'true',
    '[style.left.px]': 'box().left',
    '[style.top.px]': 'box().top',
    '[style.transform-origin]': 'box().origin',
    '[style.--from-scale]': 'box().scale',
  },
})
export class HoverCard {
  readonly anchor = input.required<HoverAnchor>();
  readonly placement = input<HoverPlacement>('above');
  /** The board's zoom (`--ui-scale`): the enlarged card grows with the board. */
  readonly scale = input(1);

  protected readonly box = computed(() => {
    const a = this.anchor();
    const cardW = CARD_W * this.scale();
    const cardH = CARD_H * this.scale();
    const vw = window.innerWidth;
    const vh = window.innerHeight;
    const centreX = a.left + a.width / 2;
    const centreY = a.top + a.height / 2;
    const above = a.top - GAP - cardH;
    const below = a.top + a.height + GAP;
    const fitsAbove = above >= GAP;
    const fitsBelow = below + cardH <= vh - GAP;
    const preferAbove = this.placement() === 'above';
    let left = centreX - cardW / 2;
    let top: number;
    if (preferAbove ? fitsAbove : !fitsBelow && fitsAbove) {
      top = above;
    } else if (fitsBelow) {
      top = below;
    } else {
      // No room above or below: open beside the card, on whichever side has more room.
      top = centreY - cardH / 2;
      left = vw - (a.left + a.width) >= a.left ? a.left + a.width + GAP : a.left - GAP - cardW;
    }
    left = clamp(left, GAP, vw - GAP - cardW);
    top = clamp(top, GAP, vh - GAP - cardH);
    return {
      left,
      top,
      origin: `${centreX - left}px ${centreY - top}px`,
      scale: a.width > 0 ? Math.min(1, a.width / cardW) : 0.5,
    };
  });
}

function clamp(value: number, min: number, max: number): number {
  return Math.max(min, Math.min(value, Math.max(min, max)));
}
