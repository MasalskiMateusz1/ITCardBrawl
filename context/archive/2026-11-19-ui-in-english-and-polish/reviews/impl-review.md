<!-- IMPL-REVIEW-REPORT -->
# Implementation Review: Interfejs i treść gry po angielsku i po polsku (ui-in-english-and-polish)

- **Plan**: context/changes/ui-in-english-and-polish/plan.md
- **Scope**: Full plan
- **Reviewed phases**: 1, 2, 3
- **Date**: 2026-11-19
- **Verdict**: APPROVED
- **Findings**: 0 critical, 2 warnings, 4 observations

## Verdicts

| Dimension | Verdict |
|-----------|---------|
| Plan Adherence | PASS |
| Scope Discipline | PASS |
| Safety & Quality | WARNING |
| Architecture | PASS |
| Pattern Consistency | PASS |
| Success Criteria | PASS |

Evidence: every planned file was changed and no unplanned files were touched. The drift review found no DRIFT or MISSING items. Its EXTRA items are all harmless additions: extra tests, `lang` attributes on the switch buttons, and cancelling an in-flight request. Automated criteria pass on HEAD: `./mvnw -B verify` (101 tests), `ng test` (10 files, 55 tests), `npm run build`. The user confirmed all manual Progress rows. A malformed `Accept-Language` header does not cause a 500; it resolves to PL. There is no `innerHTML`, and all dictionary and server text is interpolated.

## Findings

### F1 — The language interceptor's `/api` prefix check is looser than the `/api/**` rule

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: frontend/src/app/i18n/language.interceptor.ts:7
- **Detail**: `req.url.startsWith('/api')` also matches `/apix` and `/api-docs`. It skips absolute URLs (`http://host/api/...`), so if a base URL is ever introduced, requests would quietly fall back to PL. Harmless today, because all calls use relative `/api/...` paths.
- **Fix**: Match `req.url === '/api' || req.url.startsWith('/api/')`, matching the team's `/api/**` rule.
- **Decision**: FIXED

### F2 — No test that a stale response is dropped on rapid language switches

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: frontend/src/app/match-table/match-table.ts:28, frontend/src/app/new-match/new-match.ts:42
- **Detail**: The effect's `onCleanup` unsubscribes the request still in flight, so a late response cannot overwrite a newer one. No spec covers this: the specs test only one switch, made after the first response has been flushed. The break-check confirmed the language-tracking path is guarded, but not the cancellation.
- **Fix**: Add a spec per screen that switches pl→en→pl before flushing. It should assert that the earlier request is `cancelled` and only the last response is rendered.
- **Decision**: FIXED

### F3 — Frontend and backend default to different languages

- **Severity**: 💡 OBSERVATION
- **Impact**: 🔎 MEDIUM — real tradeoff; pause to reason through it
- **Dimension**: Architecture
- **Location**: frontend/src/app/i18n/language.service.ts:50, src/main/java/com/example/itcardbrawl/config/LocaleConfig.java
- **Detail**: The frontend falls back to EN for any browser language that is not `pl*`. The backend falls back to PL when the header is missing or unsupported. Both follow the plan, and there is no bug today because the interceptor always sends `pl` or `en`. But any request that bypasses the interceptor would get PL while the UI is in EN: raw `fetch`, OAuth redirects, `/error`.
- **Fix**: Document the deliberate asymmetry in the javadoc of `Language` (one sentence: "the SPA always sends a supported value; PL is the fallback for clients without a header").
  - Strength: Keeps the plan's decision (PL stays the default for existing Polish users) and makes it explicit for future readers.
  - Tradeoff: The behaviour stays the same; this is documentation only.
  - Confidence: HIGH — the interceptor covers every `/api` call in the SPA today.
  - Blind spot: Future non-SPA clients.
- **Decision**: FIXED

### F4 — A failed refetch after a switch leaves old-language data on screen

- **Severity**: 💡 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: frontend/src/app/match-table/match-table.ts:47, frontend/src/app/new-match/new-match.ts
- **Detail**: If the refetch after a language switch fails, the previous-language factions or cards stay visible next to a new-language error. The `loadFactions` text ("refresh the page") still makes sense there. Acceptable as is.
- **Fix**: Accept as is (no change), or clear the stale data on refetch failure.
- **Decision**: FIXED

### F5 — `index.html` has a static `lang="pl"` until the app starts

- **Severity**: 💡 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: frontend/src/index.html:2
- **Detail**: For English users, `<html lang>` says `pl` until the `LanguageService` constructor runs. The window is short and only before bootstrap; screen readers read the page after the app renders.
- **Fix**: Accept as is, or add a small inline script in `index.html` that sets `lang` from `localStorage`/`navigator` before bootstrap.
- **Decision**: FIXED

### F6 — Localized responses have no `Vary: Accept-Language`

- **Severity**: 💡 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: src/main/java/com/example/itcardbrawl/api/MatchController.java
- **Detail**: `/api/factions` and `/api/matches/*` now vary by `Accept-Language` but don't send `Vary`. Spring Security's default `Cache-Control: no-store` on these authenticated endpoints prevents any cache mix-up, so it is safe today.
- **Fix**: No change now; add `Vary: Accept-Language` if a public, cacheable localized endpoint appears.
- **Decision**: FIXED
