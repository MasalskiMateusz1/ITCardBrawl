import { Component, computed, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute } from '@angular/router';

@Component({
  imports: [],
  selector: 'app-sign-in',
  styleUrl: './sign-in.css',
  templateUrl: './sign-in.html',
})
export class SignIn {
  private readonly queryParamMap = toSignal(inject(ActivatedRoute).queryParamMap);

  protected readonly message = computed(() => {
    const params = this.queryParamMap();
    if (params?.has('error')) {
      return 'Logowanie nie powiodło się — spróbuj ponownie.';
    }
    if (params?.has('unavailable')) {
      return 'Serwer gry niedostępny — spróbuj później.';
    }
    return null;
  });
}
