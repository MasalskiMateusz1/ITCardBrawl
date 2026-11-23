import { Component, inject, input } from '@angular/core';
import { ActiveEvent } from '../../game/match';
import { LanguageService } from '../../i18n/language.service';
import { Card } from '../../ui/card/card';

/**
 * A side's active events as full cards (ui/card), oldest first: each with its effects and turns
 * left on the card, and its target as a caption, if it has one. "brak" when none is active.
 */
@Component({
  imports: [Card],
  selector: 'app-active-events-list',
  styleUrl: './active-events-list.css',
  templateUrl: './active-events-list.html',
})
export class ActiveEventsList {
  private readonly languageService = inject(LanguageService);

  readonly events = input.required<ActiveEvent[]>();

  protected readonly texts = this.languageService.texts;

  protected effects(event: ActiveEvent): string[] {
    return event.effects.map((effect) => effect.text);
  }
}
