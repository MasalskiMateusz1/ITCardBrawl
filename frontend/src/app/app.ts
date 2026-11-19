import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { LanguageSwitch } from './i18n/language-switch';

@Component({
  imports: [RouterOutlet, LanguageSwitch],
  selector: 'app-root',
  styleUrl: './app.css',
  templateUrl: './app.html',
})
export class App {}
