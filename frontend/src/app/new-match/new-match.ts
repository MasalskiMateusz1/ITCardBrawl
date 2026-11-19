import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, inject, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { finalize } from 'rxjs';
import { GameService } from '../game/game.service';
import { FactionSummary } from '../game/match';

@Component({
  imports: [RouterLink],
  selector: 'app-new-match',
  styleUrl: './new-match.css',
  templateUrl: './new-match.html',
})
export class NewMatch implements OnInit {
  private readonly gameService = inject(GameService);
  private readonly router = inject(Router);

  protected readonly factions = signal<FactionSummary[]>([]);
  protected readonly selectedFactionId = signal<string | null>(null);
  protected readonly starting = signal(false);
  protected readonly error = signal<string | null>(null);

  ngOnInit(): void {
    this.gameService.factions().subscribe({
      next: (factions) => this.factions.set(factions),
      error: (error: unknown) => {
        // A 401 is handled by authInterceptor, which redirects to /sign-in.
        if (!(error instanceof HttpErrorResponse && error.status === 401)) {
          this.error.set('Nie udało się wczytać frakcji — odśwież stronę.');
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
            this.error.set('Nie udało się rozpocząć partii — spróbuj ponownie.');
          }
        },
      });
  }
}
