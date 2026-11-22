import { Component, input } from '@angular/core';
import { CardBack } from '../../ui/card-back/card-back';

/**
 * Wraps one hand card. A freshly drawn card (`drawn`) flies in from the player's deck pile
 * (`--draw-x`/`--draw-y`, screen px set by the table after render) showing its back, and flips to
 * its face on the way; `drawIndex` staggers several cards drawn at once. While the card is dragged
 * (`dragged`) the slot stays as a dimmed placeholder.
 */
@Component({
  imports: [CardBack],
  selector: 'app-hand-slot',
  styleUrl: './hand-slot.css',
  template: `
    <div class="face"><ng-content /></div>
    @if (drawn()) {
      <app-card-back class="back" />
    }
  `,
  host: {
    '[class.drawn]': 'drawn()',
    '[class.dragged]': 'dragged()',
    '[style.--draw-i]': 'drawIndex()',
  },
})
export class HandSlot {
  readonly drawn = input(false);
  readonly drawIndex = input(0);
  readonly dragged = input(false);
}
