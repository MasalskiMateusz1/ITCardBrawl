import { Component, input } from '@angular/core';
import { HoverAnchor } from '../hover-card/hover-card';

/**
 * The drop area over the player's zone while a hand card that needs no target is dragged: fixed to
 * the zone's viewport rectangle (`anchor`), stronger while the pointer is over it (`over`).
 * Click-through and hidden from assistive tech, like the ghost card it belongs to.
 */
@Component({
  selector: 'app-drop-zone',
  styleUrl: './drop-zone.css',
  template: '',
  host: {
    'aria-hidden': 'true',
    '[class.over]': 'over()',
    '[style.left.px]': 'anchor().left',
    '[style.top.px]': 'anchor().top',
    '[style.width.px]': 'anchor().width',
    '[style.height.px]': 'anchor().height',
  },
})
export class DropZone {
  readonly anchor = input.required<HoverAnchor>();
  readonly over = input(false);
}
