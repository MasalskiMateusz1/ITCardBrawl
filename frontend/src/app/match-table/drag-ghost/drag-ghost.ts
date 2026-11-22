import { Component, input } from '@angular/core';

/**
 * The hand card following the pointer while it is dragged. Fixed to the viewport with its grab
 * point (`grab`, screen px from the card's top-left corner) under the pointer (`x`/`y`, screen px),
 * tilted a little towards the horizontal movement (`tilt`, degrees). Click-through, so the table can
 * hit-test what is under it, and hidden from assistive tech: dragging is a pointer-only shortcut for
 * the card button's click.
 */
@Component({
  selector: 'app-drag-ghost',
  styleUrl: './drag-ghost.css',
  template: '<div class="card"><ng-content /></div>',
  host: {
    'aria-hidden': 'true',
    '[style.translate]': '(x() - grab().x) + "px " + (y() - grab().y) + "px"',
    '[style.transform-origin]': 'grab().x + "px " + grab().y + "px"',
    '[style.rotate]': 'tilt() + "deg"',
  },
})
export class DragGhost {
  readonly x = input.required<number>();
  readonly y = input.required<number>();
  readonly grab = input({ x: 0, y: 0 });
  readonly tilt = input(0);
}
