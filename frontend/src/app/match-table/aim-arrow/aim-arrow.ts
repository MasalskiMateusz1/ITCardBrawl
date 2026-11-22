import { Component, computed, input } from '@angular/core';

/** A point on screen (px). */
export type AimPoint = { x: number; y: number };

/** How high the arrow arcs above its ends: a share of their distance, within limits (px). */
const ARC_SHARE = 0.3;
const ARC_MIN = 40;
const ARC_MAX = 200;

/**
 * The attack aim: a dashed arc from the attacker's card (`from`) to the pointer or the target it is
 * locked on (`to`, screen px), ending in a reticle that grows and lights up while `locked` (over a
 * legal target) and stays muted otherwise. A full-viewport overlay, click-through so the table can
 * hit-test under it, and hidden from assistive tech: the target buttons' names carry the meaning.
 */
@Component({
  selector: 'app-aim-arrow',
  styleUrl: './aim-arrow.css',
  template: `
    <svg>
      <path class="under" [attr.d]="path()" />
      <path class="line" [attr.d]="path()" />
      <circle class="origin" r="6" [attr.cx]="from().x" [attr.cy]="from().y" />
      <g [attr.transform]="'translate(' + to().x + ' ' + to().y + ')'">
        <g class="reticle" [class.locked]="locked()">
          <circle r="16" />
          <path d="M -7 -7 L 7 7 M 7 -7 L -7 7" />
        </g>
      </g>
    </svg>
  `,
  host: { 'aria-hidden': 'true', '[class.locked]': 'locked()' },
})
export class AimArrow {
  readonly from = input.required<AimPoint>();
  readonly to = input.required<AimPoint>();
  readonly locked = input(false);

  /** A quadratic arc whose control point sits above the midpoint, so the arrow bends upwards. */
  protected readonly path = computed(() => {
    const a = this.from();
    const b = this.to();
    const arc = Math.min(ARC_MAX, Math.max(ARC_MIN, Math.hypot(b.x - a.x, b.y - a.y) * ARC_SHARE));
    const cx = (a.x + b.x) / 2;
    const cy = Math.min(a.y, b.y) - arc;
    return `M ${a.x} ${a.y} Q ${cx} ${cy} ${b.x} ${b.y}`;
  });
}
