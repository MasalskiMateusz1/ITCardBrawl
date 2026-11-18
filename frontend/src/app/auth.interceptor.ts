import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const router = inject(Router);

  return next(req).pipe(
    catchError((error: unknown) => {
      // The /api/me probe is handled by authGuard, which picks its own redirect.
      if (error instanceof HttpErrorResponse && error.status === 401 && req.url !== '/api/me') {
        void router.navigateByUrl('/sign-in');
      }
      return throwError(() => error);
    }),
  );
};
