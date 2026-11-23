import { Component, inject } from '@angular/core';
import { LanguageService } from '../../i18n/language.service';

/** Slots drawn per zone while loading (`MatchRules.BOARD_LIMIT`). */
const SLOTS = [0, 1, 2, 3, 4];

/**
 * The board while the match is loading: both zones as empty dashed slots around a turn bar that
 * announces the load (`role="status"`). Decorative apart from the status.
 */
@Component({
  selector: 'app-board-skeleton',
  styleUrl: './board-skeleton.css',
  templateUrl: './board-skeleton.html',
})
export class BoardSkeleton {
  private readonly languageService = inject(LanguageService);

  protected readonly texts = this.languageService.texts;
  protected readonly slots = SLOTS;
}
