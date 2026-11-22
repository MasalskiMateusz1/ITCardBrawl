import { Component } from '@angular/core';

/**
 * The face-down side of a card (deck piles, the opponent's hand, a card being drawn).
 * Presentational and hidden from assistive tech: the counts it stands for are named elsewhere.
 * The default size is the compact card's; a parent may set `width`/`height` on the host.
 */
@Component({
  selector: 'app-card-back',
  styleUrl: './card-back.css',
  template: '',
  host: { 'aria-hidden': 'true' },
})
export class CardBack {}
