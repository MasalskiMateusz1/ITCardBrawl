<!-- IMPL-REVIEW-REPORT -->
# Implementation Review: Karty wydarzeń o przewidywalnych efektach (event-cards)

- **Plan**: context/changes/event-cards/plan.md
- **Scope**: Full plan
- **Reviewed phases**: 1, 2, 3, 4, 5
- **Date**: 2026-11-21
- **Verdict**: NEEDS ATTENTION
- **Findings**: 0 critical, 5 warnings, 5 observations

## Verdicts

| Dimension | Verdict |
|-----------|---------|
| Plan Adherence | WARNING |
| Scope Discipline | PASS |
| Safety & Quality | PASS |
| Architecture | WARNING |
| Pattern Consistency | WARNING |
| Success Criteria | WARNING |

Automated gates at review time: `./mvnw -B verify` PASS (222 tests), `npx ng test --watch=false` PASS (93), `npm run build` PASS.

## Findings

### F1 — Effect log and opponent's texts use card-owner perspective

- **Severity**: ⚠️ WARNING
- **Impact**: 🔎 MEDIUM — real tradeoff; pause to reason through it
- **Dimension**: Plan Adherence
- **Location**: src/main/java/com/example/itcardbrawl/api/MatchLogFormatter.java:74-81
- **Detail**: The plan's log sentences are written from the player's point of view („Termin nie do przesunięcia: +6 stresu przeciwnika”, „Twój stres −3”). Each `EffectApplied` line is instead `card name + ": " + effect.describe()`, i.e. the card's own text. When the computer plays `STRESS OPPONENT` or an `OWN_BOARD` card, the line says „Zadaj przeciwnikowi 6 stresu” / „Własne postacie…”, and to the player that reads as the wrong side. The opponent's `lastEvent` and active events have the same problem.
- **Fix A ⭐ Recommended**: Give the log its own result sentence that depends on the viewer (based on the effect kind, scope, `e.side()` and the target), separate from the card description.
  - Strength: This is exactly what the plan asked for, and it settles the log ambiguity for good.
  - Tradeoff: A second set of pl/en sentences per kind×scope (about 20 cases) plus tests.
  - Confidence: MED — `mine()` already exists in the formatter, so the perspective is known.
  - Blind spot: `lastEvent`/active events would still show card text. That is acceptable there, because it is a description of the card, not of the result.
- **Fix B**: Keep the card text and prefix the line with the side ("Ty:" / "Komputer:"), matching the other log entries.
  - Strength: A one-line change.
  - Tradeoff: The sentences are still card text, not results, so this is not what the plan describes.
  - Confidence: HIGH — trivial.
  - Blind spot: Need to check whether other log entries already carry a side prefix.
- **Decision**: FIXED (side prefix „Twoje wydarzenie” / „Wydarzenie przeciwnika” + test). Player stress → HP 30→0 rename queued as a separate change in follow-ups/review-fixes.md

### F2 — Success criterion 4.2 marked done without HTTP coverage

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Success Criteria
- **Location**: src/test/java/com/example/itcardbrawl/api/MatchControllerTests.java
- **Detail**: 4.2 requires `MatchControllerTests` to cover `targets`/`playable`/`attackTargets` in the response and 400 for a missing or unnecessary target. The file never mentions `targets`, `playable` or `attackTargets` (grep returns 0). The only 400 tests cover bad target *shapes*. A targeted play is only tested against a mocked `MatchService`, and `Accept-Language: en` is not tested at controller level.
- **Fix**: Add mockMvc tests: a GET showing `targets`/`playable`/`attackTargets`/`effects` (including en); a POST with a missing target and with an unnecessary target, each returning 400.
- **Decision**: FIXED (4 HTTP tests on a seeded table: legality/effects in pl+en, targeted play on player and character, 400 for missing/unnecessary target)

### F3 — Effect.describe() splits a "pl / en" string

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Pattern Consistency
- **Location**: src/main/java/com/example/itcardbrawl/game/Effect.java:16-20, EffectKind.java:85-174
- **Detail**: `EffectKind.description()` returns `"pl / en"` and `describe()` splits it at `indexOf(" / ")`. If a branch has no separator, `substring(-1)` throws, so every match view returns 500. If the Polish text itself contains " / ", the split happens in the wrong place. `AbilityKeyword.describe()` returns `LocalizedText` directly.
- **Fix**: Have `EffectKind` return `new LocalizedText(pl, en)` and remove the split.
- **Decision**: FIXED (EffectKind.description returns LocalizedText; split removed; texts unchanged)

### F4 — Progress SHAs for phases 1–3 point to rewritten commits

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Success Criteria
- **Location**: context/changes/event-cards/plan.md (Progress 1.1–3.2)
- **Detail**: Rows reference b9b821e / 6658abf / c1dffa7, but none of these are on any branch (the history was rewritten). On `main` the phases are 42f3db9 (p1), 72408d9 (p2) and 0477b1c (p3).
- **Fix**: Replace the SHAs in Progress with 42f3db9, 72408d9 and 0477b1c.
- **Decision**: FIXED (Progress SHAs → 42f3db9 / 72408d9 / 0477b1c)

### F5 — Gaps in tests required by the plan's contracts

- **Severity**: ⚠️ WARNING
- **Impact**: 🔎 MEDIUM — real tradeoff; pause to reason through it
- **Dimension**: Success Criteria
- **Location**: src/test/java/com/example/itcardbrawl/game/MatchEngineTests.java, api/MatchViewTests.java, api/MatchLogFormatterTests.java
- **Detail**: Missing:
  - (a) the ordering test the plan explicitly requires: ON_PLAY_STRESS/HEAL resolves before the defeat sweep under a −HP aura;
  - (b) `legalMoves` with no move for a targeted card that has no targets;
  - (c) hitting the `RESOURCE` cap (1+8=9 never reaches 10);
  - (d) actually applying `DAMAGE TARGET` to a character, and applying a mixed card (instant + modifier) to its single target;
  - (e) TARGET-bound `ATTACK`/`HEALTH` (only TRAP is tested);
  - (f) a view test where effective stats differ from base (current test has `attack == baseAttack`);
  - (g) the player-target and no-target variants of the `EffectApplied` log line;
  - (h) a standalone test of `PolishNumberForms`.
- **Fix**: Add the missing tests in MatchEngineTests, MatchViewTests and MatchLogFormatterTests, one per item.
  - Strength: Closes the gaps between the checked-off criteria (2.2, 3.2, 4.3) and what is actually covered.
  - Tradeoff: About 8–10 tests; no production change unless one of them goes red.
  - Confidence: HIGH — each gap was confirmed by reading the tests.
  - Blind spot: A new test may expose a real bug (e.g. around (a)).
- **Decision**: FIXED (tests a–h added: MatchEngineTests ×5, MatchViewTests, MatchLogFormatterTests, new PolishNumberFormsTests; all green, no production bug found)

### F6 — "Draw 1 cards" in EN

- **Severity**: 💡 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Plan Adherence
- **Location**: src/main/java/com/example/itcardbrawl/game/EffectKind.java:94
- **Detail**: `"Draw %d cards"` is hard-coded, so `neu-daily-standup` (DRAW 1) reads "Draw 1 cards".
- **Fix**: Use "card" when amount == 1.
- **Decision**: FIXED (singular "card" for 1)

### F7 — Log entries after MatchEnded

- **Severity**: 💡 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Plan Adherence
- **Location**: src/main/java/com/example/itcardbrawl/game/MatchEngine.java:194-206, 354-356, 377
- **Detail**: `DAMAGE ENEMY_ALL` adds stress first and keeps damaging characters even if the match has already ended. After `break` on `finished()`, the card still goes into `activeEvents` and `defeatCharacters` runs again. Both can log `CharacterDefeated` after `MatchEnded`. No current card uses ENEMY_ALL with a duration, but the plan says the end of the match interrupts the rest of the effects.
- **Fix**: Return early in `applyDamage`/`applyHeal` and skip the active-event bookkeeping and sweep once `state.finished()` is true.
- **Decision**: FIXED (defeatCharacters no-ops after finish; ENEMY_ALL stops after match-ending stress; test matchEndingAreaDamageLogsNothingAfterMatchEnded, break-checked)

### F8 — Duplicated Match/State helpers in MatchEngine

- **Severity**: 💡 OBSERVATION
- **Impact**: 🔎 MEDIUM — real tradeoff; pause to reason through it
- **Dimension**: Architecture
- **Location**: src/main/java/com/example/itcardbrawl/game/MatchEngine.java:91-133, 273-299
- **Detail**: `eventTargets`, `stats`, `canAttack` and `isGuarded` each exist twice, once for `Match` (`legalMoves`) and once for `State` (`apply`). If the copies diverge, the server will offer moves it then rejects, or the other way round. `eventTargets`' `actor` parameter is unused.
- **Fix**: Build `legalMoves` from `new State(match)` and keep a single set of helpers.
  - Strength: One source of rules, which is the main promise of the "legalność z serwera" decision.
  - Tradeoff: A refactor of the engine's core; a `State` copy is made on every view.
  - Confidence: MED — depends on how expensive it is to build `State` from `Match`.
  - Blind spot: Not checked whether `State` can be built read-only without side effects.
- **Decision**: FIXED (legalMoves builds a State copy and uses the same helpers as apply; Match-based duplicates and unused actor parameter removed)

### F9 — Base-only rule methods left on BoardCharacter

- **Severity**: 💡 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Plan Adherence
- **Location**: src/main/java/com/example/itcardbrawl/game/BoardCharacter.java:34-36
- **Detail**: The plan said `canAttack` would be replaced, and that `has()`, `attack()` and `remainingHealth()` must not be used for rules without event context. `canAttack()`, `has()`, `attack()`, `remainingHealth()`, `isDefeated()` and the 3-arg constructor are still there. Main code no longer calls them; they are a trap for future callers.
- **Fix**: Remove the unused methods and constructor, or rename the rest to `base*`.
- **Decision**: FIXED (base-only methods and 3-arg constructor removed; javadoc points to CharacterStats; tests use the 4-arg record)

### F10 — Minor documentation drifts

- **Severity**: 💡 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Plan Adherence
- **Location**: src/main/java/com/example/itcardbrawl/api/MatchView.java:158; context/changes/event-cards/
- **Detail**: The view's `canAttack` is derived from `!attackTargets.isEmpty()`, not from the effective formula (no visible bug, since the SPA uses it only for the player's characters on the player's own turn). The plan points to `research.md` (D1–D18), but that file is not in the change folder.
- **Fix**: Note both in the change's `change.md` Notes, or restore research.md if it exists outside the repo.
- **Decision**: FIXED (both noted in change.md Notes)
