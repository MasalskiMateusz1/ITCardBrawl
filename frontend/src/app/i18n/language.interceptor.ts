import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { LanguageService } from './language.service';

/** Sends the current UI language with every API request, so the backend returns content in it. */
export const languageInterceptor: HttpInterceptorFn = (req, next) => {
  if (!req.url.startsWith('/api')) {
    return next(req);
  }
  const language = inject(LanguageService).language();
  return next(req.clone({ setHeaders: { 'Accept-Language': language } }));
};
