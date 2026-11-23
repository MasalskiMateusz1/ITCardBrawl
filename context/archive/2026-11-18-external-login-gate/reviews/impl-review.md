<!-- IMPL-REVIEW-REPORT -->
# Implementation Review: Logowanie zewnętrznym kontem (external-login-gate)

- **Plan**: context/changes/external-login-gate/plan.md
- **Scope**: Full plan
- **Reviewed phases**: 1, 2, 3
- **Date**: 2026-11-18
- **Verdict**: NEEDS ATTENTION
- **Findings**: 0 critical, 3 warnings, 6 observations

## Verdicts

| Dimension | Verdict |
|-----------|---------|
| Plan Adherence | WARNING |
| Scope Discipline | PASS |
| Safety & Quality | WARNING |
| Architecture | PASS |
| Pattern Consistency | WARNING |
| Success Criteria | WARNING |

## Findings

### F1 — /api/me no longer calls CsrfToken.getToken(); plan not updated

- **Severity**: ⚠️ WARNING
- **Impact**: 🔎 MEDIUM — real tradeoff; pause to reason through it
- **Dimension**: Plan Adherence
- **Location**: src/main/java/com/example/itcardbrawl/api/MeController.java:16
- **Detail**: The plan (Critical Implementation Details, plan.md:52; Phase 1 §4 Contract) requires a `CsrfToken` parameter and a `getToken()` call so the deferred token gets a cookie after login. The implementation drops both. The commit message for 03df857 justifies this: "Security 7.1.2 csrf().spa() already issues XSRF-TOKEN on every response (verified on running app)". The research (§6, deferred token) says otherwise, and the plan was never updated. `MeControllerTests.responseIssuesXsrfTokenCookie` protects the current behaviour, and manual check 3.4 confirmed the header is sent and logout returns 204.
- **Fix A ⭐ Recommended**: Add a note to plan.md (§Critical Implementation Details and Phase 1 §4) recording the deviation, why it was made, and which test protects it.
  - Strength: Leaves code that is verified (automated test plus manual 3.4) unchanged, and makes the plan match the code before archiving.
  - Tradeoff: Relies on framework behaviour that the research thought would not happen; the test only fails once it changes.
  - Confidence: HIGH — the test asserts the cookie on the response from /api/me.
  - Blind spot: Not checked whether the test cookie comes from the same mechanism in a real post-login session (the manual check suggests it does).
- **Fix B**: Restore the `CsrfToken csrfToken` parameter and call `csrfToken.getToken()`, as the plan says.
  - Strength: Explicit, and does not depend on spa() behaviour that the docs describe as deferred.
  - Tradeoff: One redundant line, if the commit message is right.
  - Confidence: HIGH — this is the pattern from the Spring Security SPA documentation.
  - Blind spot: None significant.
- **Decision**: FIXED (Fix A — deviation note added to plan.md)

### F2 — Exact-URL exclusion of /api/me in the interceptor

- **Severity**: ⚠️ WARNING
- **Impact**: 🔎 MEDIUM — real tradeoff; pause to reason through it
- **Dimension**: Safety & Quality
- **Location**: frontend/src/app/auth.interceptor.ts:12
- **Detail**: `req.url !== '/api/me'` is a string literal duplicated from AuthService. Adding a query string, using an absolute URL or renaming the endpoint would make the interceptor redirect on the probe's 401 too, so it would race the guard (which has its own redirect target, e.g. ?unavailable).
- **Fix A ⭐ Recommended**: An `HttpContextToken<boolean>` (e.g. `SKIP_AUTH_REDIRECT`) set by `AuthService.loadMe()` and read by the interceptor.
  - Strength: The standard Angular way to annotate a request; it does not depend on the URL format, and the intent is visible at the call site.
  - Tradeoff: A new concept, a few lines, and the interceptor test needs updating (it would send the token instead of the URL).
  - Confidence: HIGH — public `@angular/common/http` API.
  - Blind spot: None significant.
- **Fix B**: An exported `ME_URL` constant shared by AuthService and the interceptor.
  - Strength: Minimal change.
  - Tradeoff: Still compares URLs exactly (query string, absolute URL).
  - Confidence: HIGH.
  - Blind spot: None significant.
- **Decision**: FIXED (Fix A — SKIP_AUTH_REDIRECT HttpContextToken)

### F3 — "Wyloguj" can be clicked again while logout is in flight

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: frontend/src/app/lobby/lobby.ts:19
- **Detail**: Nothing blocks a second click. A second POST that arrives after the first has invalidated the session and CSRF token gets a 403, so "Nie udało się wylogować" briefly appears while the app is already navigating to /sign-in?logout.
- **Fix**: A `loggingOut` signal that disables the button, reset in `finalize`.
- **Decision**: FIXED (loggingOut signal + [disabled] + finalize; test added)

### F4 — Guard reports 403 as "server unavailable"

- **Severity**: ℹ️ OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: frontend/src/app/auth.guard.ts:13
- **Detail**: Any error other than 401 (including 403) leads to ?unavailable. This matches the plan ("każdy inny błąd"), but a 403 would mean a configuration problem, not an outage.
- **Fix**: Leave as is (the plan's decision); revisit if /api/me ever returns 403.
- **Decision**: ACCEPTED (behaviour as in the plan; no change)

### F5 — Spring may still generate GET /login on :8080

- **Severity**: ℹ️ OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: src/main/java/com/example/itcardbrawl/config/SecurityConfig.java:23
- **Detail**: Without `loginPage(...)`, `oauth2Login` may register the default login page. This contradicts research §4 ("jawny entry point wyłącza generowaną stronę /login") and was not verified at runtime. The page is unreachable through the SPA proxy (there is no /login key), so it does no harm.
- **Fix**: Check `curl -i http://localhost:8080/login`; if it returns 200 HTML, add `.loginPage("/sign-in")` to oauth2Login.
- **Decision**: FIXED (verified: GET /login → 401, no generated page; research §4 was correct. Regression test SecurityFlowTests.noGeneratedLoginPage added, no config change)

### F6 — Avatar sends the Referer to the provider's CDN

- **Severity**: ℹ️ OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: frontend/src/app/lobby/lobby.html:3
- **Detail**: `<img [src]>` loads from avatars.githubusercontent.com / googleusercontent.com with the default referrer policy. Angular sanitises the URL, so there is no XSS risk.
- **Fix**: Add `referrerpolicy="no-referrer"` to the `<img>`.
- **Decision**: FIXED (referrerpolicy="no-referrer")

### F7 — No tests for defaultSuccessUrl / failureUrl

- **Severity**: ℹ️ OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: src/test/java/com/example/itcardbrawl/config/SecurityFlowTests.java
- **Detail**: No test checks that a failed callback (e.g. `/login/oauth2/code/github?error=access_denied`) redirects to `/sign-in?error`. The plan did not require one; it was covered by manual check 2.8.
- **Fix**: Add a MockMvc test: a callback with `error=access_denied` gives 3xx with Location `/sign-in?error`.
- **Decision**: FIXED (SecurityFlowTests.deniedConsentRedirectsToSignInWithError; the success path was not tested — it needs a mocked token exchange)

### F8 — Prettier flags files because of CRLF line endings

- **Severity**: ℹ️ OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Pattern Consistency
- **Location**: frontend/ (auth.guard.ts, auth.interceptor.ts, auth.service.ts, index.html, main.ts, styles.css)
- **Detail**: `npx prettier --check src` reports 6 files. They are LF in the repo and CRLF in the working tree (core.autocrlf). The committed content is correct; the noise is local only.
- **Fix**: Add `* text=auto eol=lf` to `.gitattributes` (or `"endOfLine": "auto"` to the Prettier config).
- **Decision**: FIXED (.gitattributes: frontend/** text=auto eol=lf; working tree converted to LF. Remaining Prettier flags: index.html and main.ts — actual formatting from the scaffold, outside this change)

### F9 — Criterion 1.3 (secrets grep) matches the plan's own text

- **Severity**: ℹ️ OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Success Criteria
- **Location**: context/changes/external-login-gate/plan.md:135
- **Detail**: `git grep -nE "client-secret=[^$]"` does not return empty: it matches the plan's own lines 135 and 347 (the command's description). There are no real secrets in the repo; the criterion as written is not literally met.
- **Fix**: In future plans, exclude the context folder: `git grep -nE "client-secret=[^$]" -- ':!context'`.
- **Decision**: ACCEPTED (no real secrets; future plans should exclude context/ from the grep)
