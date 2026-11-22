import { Routes } from '@angular/router';
import { authGuard } from './auth.guard';
import { Lobby } from './lobby/lobby';
import { MatchTable } from './match-table/match-table';
import { NewMatch } from './new-match/new-match';
import { SignIn } from './sign-in/sign-in';

export const routes: Routes = [
  { path: '', component: Lobby, canActivate: [authGuard] },
  { path: 'new-match', component: NewMatch, canActivate: [authGuard] },
  {
    path: 'match',
    component: MatchTable,
    canActivate: [authGuard],
    // The board owns the full viewport: the app shell skips its header.
    data: { fullscreen: true },
  },
  { path: 'sign-in', component: SignIn },
  { path: '**', redirectTo: '' },
];
