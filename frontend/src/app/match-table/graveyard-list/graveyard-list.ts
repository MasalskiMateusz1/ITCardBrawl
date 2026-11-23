import { Component, computed, inject, input, signal } from '@angular/core';
import { GraveyardCard } from '../../game/match';
import { LanguageService } from '../../i18n/language.service';
import { Card } from '../../ui/card/card';

/**
 * A side's graveyard as compact cards (ui/card), oldest first, wrapping in rows; "brak" when it is
 * empty. Each card is a toggle button: the pressed one is shown beside the grid as a full card, with
 * its ability, effects and text. Clicking it again hides it.
 */
@Component({
  imports: [Card],
  selector: 'app-graveyard-list',
  styleUrl: './graveyard-list.css',
  templateUrl: './graveyard-list.html',
})
export class GraveyardList {
  private readonly languageService = inject(LanguageService);

  readonly cards = input.required<GraveyardCard[]>();

  protected readonly texts = this.languageService.texts;
  protected readonly selectedId = signal<number | null>(null);
  /** The shown card; gone when it is no longer in the graveyard (other side, new view). */
  protected readonly selected = computed(
    () => this.cards().find((card) => card.instanceId === this.selectedId()) ?? null,
  );

  protected toggle(card: GraveyardCard): void {
    this.selectedId.update((id) => (id === card.instanceId ? null : card.instanceId));
  }

  protected keyword(card: GraveyardCard): string | null {
    return card.ability ? this.texts().matchTable.keyword[card.ability.keyword] : null;
  }

  protected effects(card: GraveyardCard): string[] {
    return card.effects.map((effect) => effect.text);
  }
}
