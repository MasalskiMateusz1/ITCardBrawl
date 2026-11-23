import { Component, computed, inject, input } from '@angular/core';
import { LanguageService } from '../../i18n/language.service';

/**
 * The countdown of the player's turn in the turn bar: the seconds left and a bar of the time left
 * (Board.dc.html timer bar 64×6). The table marks the host `.urgent` from 10 s on, which turns the
 * text and the bar to `--color-danger`.
 */
@Component({
  selector: 'app-turn-timer',
  styleUrl: './turn-timer.css',
  templateUrl: './turn-timer.html',
})
export class TurnTimer {
  private readonly languageService = inject(LanguageService);

  readonly seconds = input.required<number>();
  /** The turn's time limit (`turn.timeLimitSeconds`), the bar's full length. */
  readonly limit = input.required<number>();

  protected readonly texts = this.languageService.texts;
  protected readonly percent = computed(() => {
    const limit = this.limit();
    return limit > 0 ? Math.min(100, Math.max(0, (this.seconds() / limit) * 100)) : 0;
  });
}
