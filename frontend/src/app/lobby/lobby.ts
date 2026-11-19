import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { finalize } from 'rxjs';
import { AuthService } from '../auth.service';
import { LanguageService } from '../i18n/language.service';

@Component({
  imports: [RouterLink],
  selector: 'app-lobby',
  styleUrl: './lobby.css',
  templateUrl: './lobby.html',
})
export class Lobby {
  private readonly authService = inject(AuthService);
  private readonly router = inject(Router);

  protected readonly texts = inject(LanguageService).texts;
  protected readonly user = this.authService.user;
  /** A flag, not text — the message is taken from the dictionary at render time. */
  protected readonly logoutFailed = signal(false);
  protected readonly loggingOut = signal(false);

  protected logout(): void {
    this.logoutFailed.set(false);
    this.loggingOut.set(true);
    this.authService
      .logout()
      .pipe(finalize(() => this.loggingOut.set(false)))
      .subscribe({
        next: () => void this.router.navigateByUrl('/sign-in?logout'),
        error: (error: unknown) => {
          // A 401 is handled by authInterceptor, which redirects to /sign-in.
          if (!(error instanceof HttpErrorResponse && error.status === 401)) {
            this.logoutFailed.set(true);
          }
        },
      });
  }
}
