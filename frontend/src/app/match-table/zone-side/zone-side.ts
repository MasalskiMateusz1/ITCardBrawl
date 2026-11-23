import { Component, inject, input, output } from '@angular/core';
import { ActiveEvent } from '../../game/match';
import { LanguageService } from '../../i18n/language.service';

/**
 * The side column of a board zone: "Aktywne wydarzenia" as turns-left chips, or "brak" when none
 * is active. A chip reports hover/focus (with its viewport rectangle) so the table can show the
 * enlarged event card next to it.
 */
@Component({
  imports: [],
  selector: 'app-zone-side',
  styleUrl: './zone-side.css',
  templateUrl: './zone-side.html',
})
export class ZoneSide {
  private readonly languageService = inject(LanguageService);

  readonly events = input.required<ActiveEvent[]>();
  /** The hovered or focused event and where its chip is, `null` when it is left. */
  readonly preview = output<{ instanceId: number; anchor: DOMRect } | null>();

  protected readonly texts = this.languageService.texts;

  protected emitPreview(instanceId: number, event: Event): void {
    const chip = event.currentTarget as Element;
    this.preview.emit({ instanceId, anchor: chip.getBoundingClientRect() });
  }
}
