import { Component, computed, inject, input, output } from '@angular/core';
import { BudgetView } from '../../game/match';
import { LanguageService } from '../../i18n/language.service';

/**
 * A side's status in the board aside: name, stats, and for the player budget pips and an HP bar.
 * While the side is a legal attack or event target, `targetLabel` is set and the panel renders a
 * button over itself; otherwise it is a plain region.
 */
@Component({
  imports: [],
  selector: 'app-status-panel',
  styleUrl: './status-panel.css',
  templateUrl: './status-panel.html',
  host: { '[class.targeting]': 'targeting()', '[class.targetable]': 'targetLabel() !== null' },
})
export class StatusPanel {
  private readonly languageService = inject(LanguageService);

  readonly label = input.required<string>();
  readonly factionId = input.required<string>();
  readonly factionName = input.required<string>();
  readonly hp = input.required<number>();
  readonly maxHp = input.required<number>();
  readonly budget = input.required<BudgetView>();
  readonly deckCount = input.required<number>();
  /** Shown for the opponent only. */
  readonly handCount = input<number | null>(null);
  readonly graveyardCount = input.required<number>();
  /** The player's panel adds budget pips and an HP bar. */
  readonly detailed = input(false);
  /** Accessible name of the target button; `null` renders no button. */
  readonly targetLabel = input<string | null>(null);
  readonly disabled = input(false);
  /** The player is aiming an attack or event (viewfinder cursor). */
  readonly targeting = input(false);

  readonly targeted = output<void>();
  /** The pointer enters (`true`) or leaves (`false`) the target button. */
  readonly targetHover = output<boolean>();

  protected readonly texts = this.languageService.texts;
  protected readonly hpPercent = computed(() => {
    const max = this.maxHp();
    return max > 0 ? Math.min(100, Math.max(0, (this.hp() / max) * 100)) : 0;
  });
  protected readonly pips = computed(() =>
    Array.from({ length: this.budget().max }, (_, index) => index < this.budget().current),
  );
}
