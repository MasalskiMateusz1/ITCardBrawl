import { Component, computed, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute } from '@angular/router';
import { LanguageService } from '../i18n/language.service';

type SignInMessage = 'loginFailed' | 'serverUnavailable' | 'loggedOut';

@Component({
  imports: [],
  selector: 'app-sign-in',
  styleUrl: './sign-in.css',
  templateUrl: './sign-in.html',
})
export class SignIn {
  private readonly queryParamMap = toSignal(inject(ActivatedRoute).queryParamMap);

  protected readonly texts = inject(LanguageService).texts;

  private readonly messageKey = computed<SignInMessage | null>(() => {
    const params = this.queryParamMap();
    if (params?.has('error')) {
      return 'loginFailed';
    }
    if (params?.has('unavailable')) {
      return 'serverUnavailable';
    }
    if (params?.has('logout')) {
      return 'loggedOut';
    }
    return null;
  });

  protected readonly message = computed(() => {
    const key = this.messageKey();
    return key === null ? null : this.texts().signIn[key];
  });
}
