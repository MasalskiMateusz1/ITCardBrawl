import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, effect, inject, signal, untracked } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { Subscription, finalize } from 'rxjs';
import { GameService } from '../game/game.service';
import { FactionSummary } from '../game/match';
import { LanguageService } from '../i18n/language.service';

type NewMatchError = 'loadFactions' | 'startMatch';

@Component({
  imports: [RouterLink],
  selector: 'app-new-match',
  styleUrl: './new-match.css',
  templateUrl: './new-match.html',
})
export class NewMatch {
  private readonly gameService = inject(GameService);
  private readonly router = inject(Router);
  private readonly languageService = inject(LanguageService);

  protected readonly texts = this.languageService.texts;
  protected readonly factions = signal<FactionSummary[]>([]);
  protected readonly selectedFactionId = signal<string | null>(null);
  protected readonly starting = signal(false);
  /** A key, not text — the message is taken from the dictionary at render time. */
  protected readonly error = signal<NewMatchError | null>(null);
  protected readonly errorText = computed(() => {
    switch (this.error()) {
      case 'loadFactions':
        return this.texts().newMatch.loadFactionsFailed;
      case 'startMatch':
        return this.texts().newMatch.startFailed;
      default:
        return null;
    }
  });

  constructor() {
    // The first run is the entry request; each later run follows a language change.
    // Cleanup cancels a request still in flight, so a stale response cannot overwrite a newer one.
    effect((onCleanup) => {
      this.languageService.language();
      const request = untracked(() => this.loadFactions());
      onCleanup(() => request.unsubscribe());
    });
  }

  private loadFactions(): Subscription {
    return this.gameService.factions().subscribe({
      next: (factions) => {
        this.factions.set(factions);
        if (this.error() === 'loadFactions') {
          this.error.set(null);
        }
      },
      error: (error: unknown) => {
        // A 401 is handled by authInterceptor, which redirects to /sign-in.
        if (!(error instanceof HttpErrorResponse && error.status === 401)) {
          this.error.set('loadFactions');
        }
      },
    });
  }

  protected select(factionId: string): void {
    this.selectedFactionId.set(factionId);
  }

  protected start(): void {
    const factionId = this.selectedFactionId();
    if (factionId === null) {
      return;
    }
    this.error.set(null);
    this.starting.set(true);
    this.gameService
      .startMatch(factionId)
      .pipe(finalize(() => this.starting.set(false)))
      .subscribe({
        next: () => void this.router.navigateByUrl('/match'),
        error: (error: unknown) => {
          // A 401 is handled by authInterceptor, which redirects to /sign-in.
          if (!(error instanceof HttpErrorResponse && error.status === 401)) {
            this.error.set('startMatch');
          }
        },
      });
  }
}
