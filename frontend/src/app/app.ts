import { Component, OnInit, inject, signal } from '@angular/core';
import { PingService } from './ping.service';

type ApiState = 'loading' | 'ok' | 'error';

@Component({
  imports: [],
  selector: 'app-root',
  styleUrl: './app.css',
  templateUrl: './app.html',
})
export class App implements OnInit {
  private readonly pingService = inject(PingService);

  protected readonly apiState = signal<ApiState>('loading');

  ngOnInit(): void {
    this.pingService.ping().subscribe({
      next: () => this.apiState.set('ok'),
      error: () => this.apiState.set('error'),
    });
  }
}
