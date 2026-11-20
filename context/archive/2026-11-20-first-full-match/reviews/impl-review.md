<!-- IMPL-REVIEW-REPORT -->
# Implementation Review: Pełna partia postaciami przeciw komputerowi

- **Plan**: context/changes/first-full-match/plan.md
- **Scope**: Full plan
- **Reviewed phases**: 1, 2, 3, 4, 5, 6
- **Date**: 2026-11-20
- **Verdict**: APPROVED
- **Findings**: 0 critical, 2 warnings, 3 observations

## Verdicts

| Dimension | Verdict |
|-----------|---------|
| Plan Adherence | PASS |
| Scope Discipline | PASS |
| Safety & Quality | WARNING |
| Architecture | PASS |
| Pattern Consistency | WARNING |
| Success Criteria | PASS |

Success criteria were re-run on 2026-11-20:
- `./mvnw -B verify`: 185 tests, BUILD SUCCESS.
- `npx ng test --watch=false`: 83 passed.
- `npm run build`: OK.

All manual rows (1.3, 5.4–5.9, 6.5–6.8) were confirmed by the user in the session.

Plan drift: every phase matches its "Changes Required". The only extras are a few additional tests and a non-null check in `MatchStore.update`, all harmless.

## Findings

### F1 — Spec cleanup order leaks fake timers between specs

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Pattern Consistency
- **Location**: frontend/src/app/match-table/match-table.spec.ts:193
- **Detail**:
  - `afterEach` calls `httpTesting.verify()` before `vi.useRealTimers()` and `localStorage.clear()`.
  - When a spec leaves an unflushed request, `verify()` throws and the rest of the cleanup never runs.
  - The fake clock (installed at lines 721 and 813) and the `pl` locale then stay active for the following specs.
  - In the Phase 6 break-check this made 4 unrelated specs fail (result/log), so one real failure showed up as five.
- **Fix**: Run `vi.useRealTimers()` and `localStorage.clear()` before `httpTesting.verify()`, or put them in a `finally`.
- **Decision**: FIXED — afterEach now runs `vi.useRealTimers()` and `localStorage.clear()` before `httpTesting.verify()` (and the spec file was Prettier-formatted). In the F2 break-check, one real failure gave exactly 1 red spec, with no cascade.

### F2 — 404 on a move leaves the player stuck on "moveFailed"

- **Severity**: ⚠️ WARNING
- **Impact**: 🔎 MEDIUM — real tradeoff; pause to reason through it
- **Dimension**: Safety & Quality
- **Location**: frontend/src/app/match-table/match-table.ts:182
- **Detail**:
  - Matches live only in memory, so after a backend restart (for example every deploy to `main`) a move returns 404.
  - `move()` handles that as `moveFailed`. Every later click hits the same 404, and the player sees "try again" with no way out other than navigating away by hand.
  - `loadMatch()` already handles the same 404 by sending the player to `/new-match` (match-table.ts:242).
  - Plan Phase 5 said "other errors → moveFailed", so this is a gap in the plan rather than drift.
- **Fix**: On a 404 from a move, call `loadMatch()`. It gets the same 404 and navigates to `/new-match`. Add a spec for "404 on a move → GET /current → /new-match".
  - Strength: Reuses the existing redirect path and keeps one place that decides what "no match" means.
  - Tradeoff: One extra GET before the redirect; negligible.
  - Confidence: HIGH — `loadMatch` already has this behaviour and is covered by a spec.
  - Blind spot: Does not cover a 404 that arrives while a replay is playing back (it can't happen, because playback is local).
- **Decision**: FIXED — `move()` handles a 404 like a 409 and calls `loadMatch()`, which sends the player to `/new-match`. New spec "sends the player to /new-match when a move gets 404"; the break-check turned it red.

### F3 — Computer turn runs inside the map lock, and every response carries the full log

- **Severity**: 💡 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Architecture
- **Location**: src/main/java/com/example/itcardbrawl/game/MatchStore.java:37, MatchService.java (move)
- **Detail**:
  - The whole computer turn (up to 100 moves, plus a copy for each replay step) runs inside `ConcurrentHashMap.computeIfPresent`.
  - That holds the bin lock and can briefly block other players' keys that land in the same bin.
  - Every response also carries the full log of the match, plus one player and opponent view per replay step.
  - The plan accepts both ("Performance Considerations", scale `small`).
- **Fix**: No change now. If traffic grows, move to a per-player lock or a compute-then-`replace` CAS loop, and trim or cap the log.
- **Decision**: FIXED (lock + log cap, user choice) — `MatchStore.update` now computes outside the map lock and saves with `replace(key, old, new)`, retrying on conflict; `MatchService.move` resets the replay holder on each attempt. `MatchView` sends only the last `LOG_LIMIT` = 100 log entries and shifts each step's `logSize` (0 for older steps); the domain still keeps the whole log. New tests: `MatchStoreTests.changeIsRetriedOnTheNewStateWhenAnotherWriteWins` and `MatchViewTests` (3); both went red on the old code.

### F4 — Duration-0 events vanish with no visible trace

- **Severity**: 💡 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Scope Discipline
- **Location**: src/main/java/com/example/itcardbrawl/game/MatchEngine.java:111, frontend/src/app/match-table/match-table.html
- **Detail**:
  - The user reported during manual testing that "after playing event card it does not show in my active events".
  - The behaviour follows the plan: 10 of the 15 events have duration 0, go straight to the graveyard and have no effect until S-04.
  - The only visible trace is the graveyard counter, and the hand card says "Czas trwania (tury): 0".
- **Fix**: Follow-up for S-04. Show "Natychmiastowe" / "Instant" for duration 0, and/or add a log line when an event goes to the graveyard.
- **Decision**: DEFERRED — queued in follow-ups/review-fixes.md for S-04 (event-cards).

### F5 — Error body message is always Polish

- **Severity**: 💡 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: src/main/java/com/example/itcardbrawl/api/MatchController.java:95-103
- **Detail**:
  - `ErrorView.message` holds the engine's Polish reason whatever `Accept-Language` says.
  - It leaks nothing internal, and the SPA ignores the body (it shows i18n keys), so it is harmless today.
- **Fix**: Keep as is. Localize or switch to error codes only if the body is ever shown to the user.
- **Decision**: ACCEPTED — harmless while the SPA shows i18n keys instead of the error body.
