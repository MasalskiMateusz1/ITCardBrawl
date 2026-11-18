import {
  HttpClient,
  HttpContext,
  HttpErrorResponse,
  provideHttpClient,
  withInterceptors,
} from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { authInterceptor, SKIP_AUTH_REDIRECT } from './auth.interceptor';
import { AuthService } from './auth.service';

describe('authInterceptor', () => {
  let httpTesting: HttpTestingController;
  let navigateByUrl: ReturnType<typeof vi.spyOn>;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
        provideRouter([]),
      ],
    });

    httpTesting = TestBed.inject(HttpTestingController);
    navigateByUrl = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
  });

  afterEach(() => {
    httpTesting.verify();
  });

  function request(url: string): unknown[] {
    const errors: unknown[] = [];
    TestBed.inject(HttpClient)
      .get(url)
      .subscribe({ error: (error: unknown) => errors.push(error) });
    return errors;
  }

  it('redirects to /sign-in on 401 from another API endpoint and propagates the error', () => {
    const errors = request('/api/other');
    httpTesting.expectOne('/api/other').flush(null, { status: 401, statusText: 'Unauthorized' });

    expect(navigateByUrl).toHaveBeenCalledWith('/sign-in');
    expect(errors.length).toBe(1);
    expect((errors[0] as HttpErrorResponse).status).toBe(401);
  });

  it('does not redirect on 401 when the request sets SKIP_AUTH_REDIRECT and propagates the error', () => {
    const errors: unknown[] = [];
    TestBed.inject(HttpClient)
      .get('/api/other', { context: new HttpContext().set(SKIP_AUTH_REDIRECT, true) })
      .subscribe({ error: (error: unknown) => errors.push(error) });
    httpTesting.expectOne('/api/other').flush(null, { status: 401, statusText: 'Unauthorized' });

    expect(navigateByUrl).not.toHaveBeenCalled();
    expect(errors.length).toBe(1);
    expect((errors[0] as HttpErrorResponse).status).toBe(401);
  });

  it('does not redirect on 401 from the AuthService /api/me probe', () => {
    const errors: unknown[] = [];
    TestBed.inject(AuthService)
      .loadMe()
      .subscribe({ error: (error: unknown) => errors.push(error) });
    httpTesting.expectOne('/api/me').flush(null, { status: 401, statusText: 'Unauthorized' });

    expect(navigateByUrl).not.toHaveBeenCalled();
    expect(errors.length).toBe(1);
    expect((errors[0] as HttpErrorResponse).status).toBe(401);
  });

  it('does not redirect on 500 and propagates the error', () => {
    const errors = request('/api/other');
    httpTesting
      .expectOne('/api/other')
      .flush('Server Error', { status: 500, statusText: 'Internal Server Error' });

    expect(navigateByUrl).not.toHaveBeenCalled();
    expect(errors.length).toBe(1);
    expect((errors[0] as HttpErrorResponse).status).toBe(500);
  });
});
