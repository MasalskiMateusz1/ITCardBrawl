import { DOCUMENT } from '@angular/common';
import { Injectable, computed, inject, signal } from '@angular/core';
import { en } from './en';
import { Messages } from './messages';
import { pl } from './pl';

export type Language = 'pl' | 'en';

export const LANGUAGE_STORAGE_KEY = 'itcardbrawl.language';

const DICTIONARIES: Record<Language, Messages> = { pl, en };

function isLanguage(value: unknown): value is Language {
  return value === 'pl' || value === 'en';
}

/** Single source of the current UI language: browser default, remembered in localStorage, mirrored on <html lang>. */
@Injectable({ providedIn: 'root' })
export class LanguageService {
  private readonly document = inject(DOCUMENT);
  private readonly current = signal<Language>(this.initialLanguage());

  readonly language = this.current.asReadonly();
  readonly texts = computed<Messages>(() => DICTIONARIES[this.current()]);

  constructor() {
    this.document.documentElement.lang = this.current();
  }

  setLanguage(lang: Language): void {
    try {
      localStorage.setItem(LANGUAGE_STORAGE_KEY, lang);
    } catch {
      // Storage unavailable (e.g. privacy mode) — the choice still applies for this page.
    }
    this.current.set(lang);
    this.document.documentElement.lang = lang;
  }

  private initialLanguage(): Language {
    try {
      const stored = localStorage.getItem(LANGUAGE_STORAGE_KEY);
      if (isLanguage(stored)) {
        return stored;
      }
    } catch {
      // Storage unavailable — fall back to the browser language.
    }
    const browser = typeof navigator === 'undefined' ? '' : (navigator.language ?? '');
    return browser.toLowerCase().startsWith('pl') ? 'pl' : 'en';
  }
}
