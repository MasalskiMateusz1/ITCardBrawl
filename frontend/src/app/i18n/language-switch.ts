import { Component, inject } from '@angular/core';
import { Language, LanguageService } from './language.service';

@Component({
  imports: [],
  selector: 'app-language-switch',
  styleUrl: './language-switch.css',
  templateUrl: './language-switch.html',
})
export class LanguageSwitch {
  private readonly languageService = inject(LanguageService);

  protected readonly language = this.languageService.language;
  protected readonly texts = this.languageService.texts;

  protected select(lang: Language): void {
    this.languageService.setLanguage(lang);
  }
}
