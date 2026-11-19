import { HttpErrorResponse } from '@angular/common/http';
import { Component, effect, inject, signal, untracked } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { Subscription } from 'rxjs';
import { GameService } from '../game/game.service';
import { MatchView } from '../game/match';
import { LanguageService } from '../i18n/language.service';

@Component({
  imports: [RouterLink],
  selector: 'app-match-table',
  styleUrl: './match-table.css',
  templateUrl: './match-table.html',
})
export class MatchTable {
  private readonly gameService = inject(GameService);
  private readonly router = inject(Router);
  private readonly languageService = inject(LanguageService);

  protected readonly texts = this.languageService.texts;
  protected readonly match = signal<MatchView | null>(null);
  /** A key, not text — the message is taken from the dictionary at render time. */
  protected readonly error = signal<'loadMatch' | null>(null);

  constructor() {
    // The first run is the entry request; each later run follows a language change.
    // Cleanup cancels a request still in flight, so a stale response cannot overwrite a newer one.
    effect((onCleanup) => {
      this.languageService.language();
      const request = untracked(() => this.loadMatch());
      onCleanup(() => request.unsubscribe());
    });
  }

  private loadMatch(): Subscription {
    return this.gameService.currentMatch().subscribe({
      next: (match) => {
        this.match.set(match);
        this.error.set(null);
      },
      error: (error: unknown) => {
        if (error instanceof HttpErrorResponse && error.status === 404) {
          // No match in progress (e.g. after a backend restart) — pick a faction again.
          void this.router.navigateByUrl('/new-match');
        } else if (!(error instanceof HttpErrorResponse && error.status === 401)) {
          // A 401 is handled by authInterceptor, which redirects to /sign-in.
          this.error.set('loadMatch');
        }
      },
    });
  }
}
