import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, inject, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { GameService } from '../game/game.service';
import { CardType, MatchView } from '../game/match';

const CARD_TYPE_LABELS: Record<CardType, string> = {
  CHARACTER: 'Postać',
  EVENT: 'Wydarzenie',
};

@Component({
  imports: [RouterLink],
  selector: 'app-match-table',
  styleUrl: './match-table.css',
  templateUrl: './match-table.html',
})
export class MatchTable implements OnInit {
  private readonly gameService = inject(GameService);
  private readonly router = inject(Router);

  protected readonly match = signal<MatchView | null>(null);
  protected readonly error = signal<string | null>(null);

  ngOnInit(): void {
    this.gameService.currentMatch().subscribe({
      next: (match) => this.match.set(match),
      error: (error: unknown) => {
        if (error instanceof HttpErrorResponse && error.status === 404) {
          // No match in progress (e.g. after a backend restart) — pick a faction again.
          void this.router.navigateByUrl('/new-match');
        } else if (!(error instanceof HttpErrorResponse && error.status === 401)) {
          // A 401 is handled by authInterceptor, which redirects to /sign-in.
          this.error.set('Nie udało się wczytać partii — spróbuj ponownie.');
        }
      },
    });
  }

  protected cardTypeLabel(type: CardType): string {
    return CARD_TYPE_LABELS[type];
  }
}
