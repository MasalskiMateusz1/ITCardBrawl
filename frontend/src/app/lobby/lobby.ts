import { Component, inject } from '@angular/core';
import { AuthService } from '../auth.service';

@Component({
  imports: [],
  selector: 'app-lobby',
  styleUrl: './lobby.css',
  templateUrl: './lobby.html',
})
export class Lobby {
  protected readonly user = inject(AuthService).user;
}
