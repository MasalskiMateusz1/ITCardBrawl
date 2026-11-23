import {
  Component,
  ElementRef,
  afterNextRender,
  inject,
  input,
  output,
  viewChild,
} from '@angular/core';
import { LanguageService } from '../../i18n/language.service';

let nextId = 0;

/**
 * An overlay over the board's main column (Board.dc.html B:113-152): a titled panel for the
 * graveyard or the game over. It is a non-modal dialog: it traps nothing, and focus moves to its
 * heading once when it opens. A `closable` overlay closes with its close button or Escape (handled
 * on the overlay itself, no global listener) by emitting `closed`; the opener restores focus.
 * `dim` lets the board show through (game over) instead of the solid overlay surface.
 */
@Component({
  selector: 'app-board-overlay',
  styleUrl: './board-overlay.css',
  templateUrl: './board-overlay.html',
  host: {
    role: 'dialog',
    '[attr.aria-labelledby]': 'headingId',
    '[class.dim]': 'dim()',
    '(keydown.escape)': 'onEscape($event)',
  },
})
export class BoardOverlay {
  private readonly languageService = inject(LanguageService);

  readonly title = input.required<string>();
  readonly closable = input(true);
  readonly dim = input(false);

  readonly closed = output<void>();

  protected readonly texts = this.languageService.texts;
  protected readonly headingId = `board-overlay-${++nextId}`;
  private readonly heading = viewChild.required<ElementRef<HTMLElement>>('heading');

  constructor() {
    afterNextRender(() => this.heading().nativeElement.focus());
  }

  protected onEscape(event: Event): void {
    if (this.closable()) {
      event.preventDefault();
      event.stopPropagation();
      this.closed.emit();
    }
  }
}
