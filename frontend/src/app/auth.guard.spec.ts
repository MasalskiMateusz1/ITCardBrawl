import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import {
  ActivatedRouteSnapshot,
  GuardResult,
  provideRouter,
  RouterStateSnapshot,
  UrlTree,
} from '@angular/router';
import { Observable } from 'rxjs';
import { authGuard } from './auth.guard';
import { AuthService, Player } from './auth.service';

describe('authGuard', () => {
  let httpTesting: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });

    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpTesting.verify();
  });

  function runGuard(): GuardResult[] {
    const results: GuardResult[] = [];
    const result = TestBed.runInInjectionContext(() =>
      authGuard({} as ActivatedRouteSnapshot, {} as RouterStateSnapshot),
    ) as Observable<GuardResult>;
    result.subscribe((value) => results.push(value));
    return results;
  }

  it('allows activation and stores the player when /api/me succeeds', () => {
    const player: Player = {
      provider: 'github',
      displayName: 'Ada',
      avatarUrl: 'https://example.com/ada.png',
    };

    const results = runGuard();
    httpTesting.expectOne('/api/me').flush(player);

    expect(results).toEqual([true]);
    expect(TestBed.inject(AuthService).user()).toEqual(player);
  });

  it('redirects to /sign-in when /api/me returns 401', () => {
    const results = runGuard();
    httpTesting.expectOne('/api/me').flush(null, { status: 401, statusText: 'Unauthorized' });

    expect(results.length).toBe(1);
    expect(results[0]).toBeInstanceOf(UrlTree);
    expect(results[0].toString()).toBe('/sign-in');
  });

  it('redirects to /sign-in?unavailable when /api/me returns 504', () => {
    const results = runGuard();
    httpTesting
      .expectOne('/api/me')
      .flush('Gateway Timeout', { status: 504, statusText: 'Gateway Timeout' });

    expect(results.length).toBe(1);
    const tree = results[0] as UrlTree;
    expect(tree).toBeInstanceOf(UrlTree);
    // Angular serializes a value-less query param as `?unavailable=`; SignIn checks presence only.
    expect(tree.root.children['primary'].segments.map((segment) => segment.path)).toEqual([
      'sign-in',
    ]);
    expect(tree.queryParamMap.has('unavailable')).toBe(true);
  });
});
