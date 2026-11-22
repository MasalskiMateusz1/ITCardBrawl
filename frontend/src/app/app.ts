import { Component, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRouteSnapshot, NavigationEnd, Router, RouterOutlet } from '@angular/router';
import { filter, map } from 'rxjs';
import { LanguageSwitch } from './i18n/language-switch';

@Component({
  imports: [RouterOutlet, LanguageSwitch],
  selector: 'app-root',
  styleUrl: './app.css',
  templateUrl: './app.html',
})
export class App {
  private readonly router = inject(Router);

  /** The active route asks for the full viewport (`data: { fullscreen: true }`): no header then. */
  protected readonly fullscreen = toSignal(
    this.router.events.pipe(
      filter((event) => event instanceof NavigationEnd),
      map(() => isFullscreen(this.router.routerState.snapshot.root)),
    ),
    { initialValue: false },
  );
}

function isFullscreen(root: ActivatedRouteSnapshot): boolean {
  let route = root;
  while (route.firstChild !== null) {
    route = route.firstChild;
  }
  return route.data['fullscreen'] === true;
}
