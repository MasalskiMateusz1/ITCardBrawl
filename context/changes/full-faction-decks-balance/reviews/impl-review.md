<!-- IMPL-REVIEW-REPORT -->
# Implementation Review: Pełne talie frakcji i balans (S-06)

- **Plan**: context/changes/full-faction-decks-balance/plan.md
- **Scope**: Full plan
- **Reviewed phases**: 1, 2, 3, 4, 5, 6, 7
- **Date**: 2026-11-22
- **Verdict**: NEEDS ATTENTION
- **Findings**: 0 critical, 3 warnings, 6 observations

## Verdicts

| Dimension | Verdict |
|-----------|---------|
| Plan Adherence | WARNING |
| Scope Discipline | WARNING |
| Safety & Quality | WARNING |
| Architecture | PASS |
| Pattern Consistency | WARNING |
| Success Criteria | WARNING |

Success criteria re-run on 2026-11-22: `./mvnw -B verify`: 323 tests, BUILD SUCCESS. `BalanceSimulationTests`: 1000 matches, every cell inside 40–60%, p90 284 s. Two runs gave a byte-identical `matches.csv`. The SPA tests (104) and the build pass.

Documented decisions, not findings:
- The bonus card is `neu-rubber-duck`, not `neu-coffee`. The computer never plays coffee; see `card-pricing.md:348-362`.
- In Phase 6, two cards were repriced after the author's playtests: `dev-fullstack` and `dev-force-push`.

## Findings

### F1 — SPA sends a stale end-turn after a 409/404 when time ran out during a request

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: frontend/src/app/match-table/match-table.ts:241 (finalize), :256 (409/404 → loadMatch), :364 (endTurnIfTimeUp)
- **Detail**: Suppose the countdown hits zero while a move is in flight and that move returns 409 or 404. The error branch starts `loadMatch()`, and then `finalize` calls `endTurnIfTimeUp()`. At that point `endTurnWhenIdle` is still true, and `busy()` does not count the open `loadRequest`. So a POST end-turn with an old turn number goes out next to the GET. It returns 409 and triggers a second `loadMatch()`, which cancels the first GET. If that first GET was the one that auto-ended the turn, the computer's replay is lost. After a 404, the POST also races the redirect to `/new-match`.
- **Fix**: In the 409/404 branch, clear `endTurnWhenIdle` before `loadMatch()`, plus a spec for the "time up + 409" case.
- **Decision**: FIXED (2026-11-22) — endTurnWhenIdle cleared in the 409/404 branch; spec "drops the owed end-turn…"

### F2 — A move after the deadline drops the computer turn replay

- **Severity**: ⚠️ WARNING
- **Impact**: 🔎 MEDIUM — real tradeoff; pause to reason through it
- **Dimension**: Safety & Quality
- **Location**: src/main/java/com/example/itcardbrawl/game/MatchService.java:88-108
- **Detail**: `move()` sees the expired deadline, ends the turn and plays the computer turn inside `store.update`, then throws `StaleMoveException`. The replay it just computed is thrown away. The SPA then calls `loadMatch()`, and GET `/current` returns `replay: []`, so the computer's whole turn appears at once with no playback. This happens even when the late request is an `EndTurn` for that very turn, for example a click in the last second plus network delay. The plan accepted a 409 here ("SPA odświeży widok po 409"), but it did not foresee that the playback would be lost.
- **Fix A ⭐ Recommended**: Keep the auto-end replay in `MatchStore.Entry` (a `pendingReplay` field), and have the next `current()` return it once and clear it.
  - Strength: One mechanism covers every late request (play, attack, end-turn), and the SPA's 409 → `loadMatch` path stays unchanged.
  - Tradeoff: More state in `Entry`; you need to decide when to discard an unread replay (e.g. on a new match).
  - Confidence: MED — the shape follows from the existing `current()` returning `Optional<MatchOutcome>`.
  - Blind spot: Behavior with two tabs open; only one would see the replay.
- **Fix B**: For an `EndTurn` with the right id and turn number after the deadline, return the auto-end outcome as a normal 200 with replay; leave the 409 for other moves.
  - Strength: Small change; covers the most common case (a click right at the end of the time).
  - Tradeoff: A late play or attack still loses the playback.
  - Confidence: HIGH — a local change in `move()`.
  - Blind spot: None significant.
- **Decision**: FIXED via Fix A (2026-11-22) — MatchStore.Entry.pendingReplay; current() replays it once (MatchServiceTests, MatchControllerTests)

### F3 — Phase 5 changed the structure of PositionEvaluator, not only the weights (undocumented)

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Plan Adherence
- **Location**: src/main/java/com/example/itcardbrawl/game/PositionEvaluator.java (~l.180-220)
- **Detail**: The Phase 5 contract says the weights are retuned, and the constant names and the `Score` structure stay unchanged. What actually changed:
  - a new constant `SETUP_DAMAGE_SHARE`;
  - a new heuristic `finishable()`, which looks at the active side's follow-up attacks;
  - a new signature `characterValue(stats, damage, damageShare, extraGuard)`;
  - a new meaning for `DAMAGE_DISCOUNT` (0.5 subtracted → 0.1 as a multiplier).

  This serves the plan's goal (FAVORABLE_TRADE > 0) and `Score` is unchanged. The explanation is only in the javadoc and the commit message of 044f20a; neither `card-pricing.md` nor the plan mentions it.
- **Fix**: Add a short "Wagi AI (faza 5)" note to `card-pricing.md` describing `finishable()`, `SETUP_DAMAGE_SHARE` and the new meaning of `DAMAGE_DISCOUNT`.
- **Decision**: FIXED (2026-11-22) — section "Wagi AI (faza 5)" in card-pricing.md

### F4 — Progress 6.2 is ticked, but one cell is below the 45–55% target

- **Severity**: 💡 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Success Criteria
- **Location**: context/changes/full-faction-decks-balance/plan.md:545; context/foundation/card-pricing.md:306, 396
- **Detail**: Cell developers / OPPONENT starts: 42.4% (95% CI 36.4–48.6%). It passes the 40–60% gate, but not the target of criterion 6.2. `card-pricing.md` records the author's decision that 45–55% is only a guide (CI ±6 p.p. at 250 matches per cell). The tick on 6.2 suggests more than was actually achieved.
- **Fix**: Mention this in the PR description, pointing to card-pricing.md:396.
- **Decision**: ACCEPTED (2026-11-22) — to be called out in the PR description (cell 42.4%, card-pricing.md:396)

### F5 — No unit test for ComputerOpponent playing the PLAYER side

- **Severity**: 💡 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Plan Adherence
- **Location**: src/test/java/com/example/itcardbrawl/game/ComputerOpponentTests.java
- **Detail**: The Testing Strategy (plan:435) required a test that the constructor with `Side.PLAYER` plays correctly (moves and reasons with `side == PLAYER`). `ComputerOpponentTests` uses only the 3-argument constructor; PLAYER-side play is covered only indirectly by `BalanceSimulationTests`.
- **Fix**: Add a test in `ComputerOpponentTests`: the PLAYER-side constructor on a match where PLAYER is active → every `MoveExplained` has `side == PLAYER`, and the moves are legal.
- **Decision**: FIXED (2026-11-22) — ComputerOpponentTests.playsThePlayerSideWhenConstructedForIt

### F6 — Out-of-scope CI commit, and `if: always()` also runs on cancel

- **Severity**: 💡 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Scope Discipline
- **Location**: .github/workflows/ci.yml:22-49 (commit 592e626)
- **Detail**:
  - Commit 592e626 bumps actions in all three jobs to versions running on Node 24, including the build/deploy jobs, which have nothing to do with S-06. The plan required `upload-artifact@v4`.
  - `actions/*` actions use mutable tags (`@v7`), while `docker/*` in the same file are pinned by SHA.
  - The report steps use `if: always()`, so they also run when the workflow is cancelled.
- **Fix**: Mention 592e626 in the PR description as a separate CI change, and switch the report steps to `if: ${{ !cancelled() }}`.
- **Decision**: FIXED (2026-11-22) — report steps use `if: ${{ !cancelled() }}`; 592e626 to be called out in the PR description

### F7 — GET /api/matches/current changes state (lazy turn expiry)

- **Severity**: 💡 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: src/main/java/com/example/itcardbrawl/api/MatchController.java:74-78; MatchService.java:117
- **Detail**: After the deadline, GET ends the player's turn and plays the computer turn. GET is outside CSRF protection, so a cross-site request carrying the session cookie can trigger it. The impact is minimal: it does only what the next request would do anyway after the deadline. The plan chose this on purpose (lazy expiry, no background jobs).
- **Fix**: A comment on `MatchController.current` saying the side effect is a deliberate lazy expiry and is idempotent after the deadline.
- **Decision**: FIXED (2026-11-22) — comment on MatchController.current (deliberate lazy expiry)

### F8 — After a reload, the countdown starts again from 45 s

- **Severity**: 💡 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: src/main/java/com/example/itcardbrawl/api/MatchView.java:89-92; frontend/src/app/match-table/match-table.ts:343
- **Detail**: `timeLimitSeconds` is always 45 and is not tied to `playerTurnDeadline`. After a reload, a new tab, or a slowed `setInterval` in a background tab, the counter starts from the beginning while the server deadline keeps running.
- **Fix**: None — the author's decision of 2026-11-22: the limit is there to end AFK turns, not to measure time precisely.
- **Decision**: ACCEPTED — author decision (2026-11-22): the 45 s limit ends AFK turns; resetting after a reload is acceptable.

### F9 — Small duplications and a stale comment

- **Severity**: 💡 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Pattern Consistency
- **Location**: src/test/java/com/example/itcardbrawl/game/BalanceSimulationTests.java:55-57, :92; MatchView.java:90 vs MatchService.playerTurn()
- **Detail**:
  - `REPLAY_STEP_SECONDS = 0.7` duplicates `MatchRules.REPLAY_STEP` (added in Phase 7).
  - The "player's turn in a match in progress" check is written twice: in `TurnView.of` and in `MatchService.playerTurn`.
  - The javadoc says "1000 matches take about 0.8 s"; `card-pricing.md` says 1.2–1.4 s.
- **Fix**: Derive `REPLAY_STEP_SECONDS` from `MatchRules.REPLAY_STEP`, move the predicate into one place (e.g. `Match.isPlayerTurn()`), and fix the javadoc number.
- **Decision**: FIXED (2026-11-22) — Match.isPlayerTurn(), REPLAY_STEP_SECONDS from MatchRules.REPLAY_STEP, javadoc runtime corrected
