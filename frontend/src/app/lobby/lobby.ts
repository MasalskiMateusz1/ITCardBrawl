import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { AuthService } from '../auth.service';

@Component({
  imports: [],
  selector: 'app-lobby',
  styleUrl: './lobby.css',
  templateUrl: './lobby.html',
})
export class Lobby {
  private readonly authService = inject(AuthService);
  private readonly router = inject(Router);

  protected readonly user = this.authService.user;
  protected readonly logoutError = signal<string | null>(null);

  protected logout(): void {
    this.logoutError.set(null);
    this.authService.logout().subscribe({
      next: () => void this.router.navigateByUrl('/sign-in?logout'),
      error: (error: unknown) => {
        // A 401 is handled by authInterceptor, which redirects to /sign-in.
        if (!(error instanceof HttpErrorResponse && error.status === 401)) {
          this.logoutError.set('Nie udało się wylogować — spróbuj ponownie.');
        }
      },
    });
  }
}
