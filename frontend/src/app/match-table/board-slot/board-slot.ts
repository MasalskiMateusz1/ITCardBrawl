import { Component, input } from '@angular/core';

/**
 * Wraps one board character's card button. A Guard character (`guard`, from its effective
 * keywords) stands on a shield plate that peeks out around the card's sides and bottom; while an
 * attacker is aimed and the backend offers it as a target (`aimed`) the plate lights up, so it reads
 * "hit this first". Decorative: the button's accessible name already names Guard.
 */
@Component({
  selector: 'app-board-slot',
  styleUrl: './board-slot.css',
  template: `
    @if (guard()) {
      <span class="shield" aria-hidden="true"></span>
    }
    <ng-content />
  `,
  host: { '[class.guard]': 'guard()', '[class.aimed]': 'guard() && aimed()' },
})
export class BoardSlot {
  readonly guard = input(false);
  readonly aimed = input(false);
}
