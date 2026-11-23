import { Component, computed, inject, input, output } from '@angular/core';
import { ActiveEvent } from '../../game/match';
import { LanguageService } from '../../i18n/language.service';
import { Card } from '../../ui/card/card';

/** Event cards drawn in the stack: enough to read as a fan, the newest always on top. */
const STACK_LAYERS = 3;

/**
 * The side column of a board zone: "Aktywne wydarzenia" as a fanned stack of compact event cards
 * (the newest, last in `events`, on top) with a count badge, or "brak" when none is active. The
 * whole stack is one button that asks the table to open (or close) the active events overlay.
 */
@Component({
  imports: [Card],
  selector: 'app-zone-side',
  styleUrl: './zone-side.css',
  templateUrl: './zone-side.html',
})
export class ZoneSide {
  private readonly languageService = inject(LanguageService);

  readonly events = input.required<ActiveEvent[]>();
  /** The active events overlay of this zone is open. */
  readonly expanded = input(false);
  readonly toggled = output<void>();

  protected readonly texts = this.languageService.texts;
  /** The cards drawn in the stack, oldest first: the newest is painted last, on top. */
  protected readonly layers = computed(() => this.events().slice(-STACK_LAYERS));
}
