import { Component, computed, input } from '@angular/core';
import { CardBack } from '../../ui/card-back/card-back';

/** Card backs drawn on the pile: enough to read as a stack, never more than the deck holds. */
const PILE_LAYERS = 3;

/**
 * A side's face-down cards in its status column: the deck pile with its count and, for the
 * opponent, the hand as a row of card backs (`handCount`; `null` renders none). Decorative: the
 * status panel names the same counts for assistive tech.
 */
@Component({
  imports: [CardBack],
  selector: 'app-side-cards',
  styleUrl: './side-cards.css',
  templateUrl: './side-cards.html',
  host: { 'aria-hidden': 'true' },
})
export class SideCards {
  readonly deckCount = input.required<number>();
  readonly handCount = input<number | null>(null);

  protected readonly pileLayers = computed(() =>
    Array.from({ length: Math.min(PILE_LAYERS, this.deckCount()) }, (_, index) => index),
  );
  protected readonly handBacks = computed(() =>
    Array.from({ length: this.handCount() ?? 0 }, (_, index) => index),
  );
}
