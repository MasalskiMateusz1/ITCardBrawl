import { Component, input } from '@angular/core';

/**
 * The selected card lifted into the board centre while aiming. It flies in from the slot it was
 * clicked in (`from`, screen px relative to the centre), then floats in place. Click-through and
 * hidden from assistive tech: the selection is announced by `aria-pressed` and `.choose-target`.
 */
@Component({
  selector: 'app-lifted-card',
  styleUrl: './lifted-card.css',
  template: '<ng-content />',
  host: {
    'aria-hidden': 'true',
    '[style.--lift-x.px]': 'from().x',
    '[style.--lift-y.px]': 'from().y',
  },
})
export class LiftedCard {
  readonly from = input({ x: 0, y: 0 });
}
