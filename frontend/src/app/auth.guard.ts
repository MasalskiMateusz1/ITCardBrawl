import { HttpErrorResponse } from '@angular/common/http';
import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { catchError, map, of } from 'rxjs';
import { AuthService } from './auth.service';

export const authGuard: CanActivateFn = () => {
  const authService = inject(AuthService);
  const router = inject(Router);

  return authService.loadMe().pipe(
    map(() => true),
    catchError((error: unknown) =>
      of(
        error instanceof HttpErrorResponse && error.status === 401
          ? router.parseUrl('/sign-in')
          : router.parseUrl('/sign-in?unavailable'),
      ),
    ),
  );
};
