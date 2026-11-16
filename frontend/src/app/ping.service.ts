import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

@Injectable({ providedIn: 'root' })
export class PingService {
  private readonly http = inject(HttpClient);

  ping(): Observable<{ status: string }> {
    return this.http.get<{ status: string }>('/api/ping');
  }
}
