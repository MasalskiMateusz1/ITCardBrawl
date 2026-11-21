<!-- IMPL-REVIEW-REPORT -->
# Implementation Review: Sensowny przeciwnik komputerowy

- **Plan**: context/changes/sensible-computer-opponent/plan.md
- **Scope**: Full plan
- **Reviewed phases**: 1, 2, 3
- **Date**: 2026-11-21
- **Verdict**: NEEDS ATTENTION
- **Findings**: 0 critical, 2 warnings, 4 observations

## Verdicts

| Dimension | Verdict |
|-----------|---------|
| Plan Adherence | PASS |
| Scope Discipline | PASS |
| Safety & Quality | WARNING |
| Architecture | PASS |
| Pattern Consistency | WARNING |
| Success Criteria | PASS |

Success criteria re-run on 2026-11-21: `grep -rn RandomOpponent src/main` returns nothing; targeted backend tests pass; `./mvnw -B verify` passes (261 tests); `npx ng test --watch=false` passes (94/94); `npm run build` passes. Every manual row is `[x]`, and the diff backs each one up.

Fairness (public information only) was traced and holds. The evaluator reads hand and deck only as `size()` / `isEmpty()`. A DRAW simulation changes the score only through the hand count, and the DRAW_CARDS reason only counts draw events. No `innerHTML` is used in the SPA.

## Findings

### F1 — Stale reason caption on the first playback step

- **Severity**: ⚠️ WARNING
- **Impact**: 🔎 MEDIUM — real tradeoff; pause to reason through it
- **Dimension**: Safety & Quality
- **Location**: frontend/src/app/match-table/match-table.ts:84
- **Detail**: `moveReason` uses the last `reason` entry in the visible log. `replay[0]` is the state right after the player ends their turn (`MatchService.java:78`, `withFirst(after, steps)`). Its log still holds the previous computer turn's reasons. So from the second computer turn on, the first frame captions last turn's reason, usually "kończy turę — …". The spec fixture `reasonResponse()` gives step 0 a `logSize` that already includes a new reason, which the backend never sends, so the bug stays hidden.
- **Fix A ⭐ Recommended**: In the SPA, only consider reason entries past the previous step's `logSize`; step 0 has no caption. Add a spec where step 0's log contains an older reason and assert there is no caption.
  - Strength: Small, frontend-only change. It matches the plan's rule ("ostatni powód z bieżącego kroku") and needs no API change.
  - Tradeoff: The component has to keep the previous step's `logSize` alongside the current step.
  - Confidence: HIGH — the step-0 state is verified in `MatchService.move`.
  - Blind spot: The `start` path, where the computer moves first (`MatchService.java:47`), behaves the same way and is covered by the same rule.
- **Fix B**: Have the backend `ReplayStepView` carry the step's reason text explicitly, and have the SPA show that.
  - Strength: The meaning is explicit, with no index arithmetic in the SPA.
  - Tradeoff: It changes the API contract and touches backend and frontend tests.
  - Confidence: MED — it needs a new view field and serialization tests.
  - Blind spot: The text would be duplicated between the log and the step.
- **Decision**: FIXED (Fix B — `ReplayStepView.reason`, null on step 0; SPA caption reads the shown step's reason; tests in MatchViewTests, MatchControllerTests, match-table.spec)

### F2 — Caption doubles the prefix: "Dlaczego: Przeciwnik: …"

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Pattern Consistency
- **Location**: frontend/src/app/i18n/pl.ts:52, en.ts:52 (with MatchLogFormatter.java:73-126)
- **Detail**: Every backend reason already starts with "Przeciwnik:" / "Opponent:". The SPA label wraps it again as "Dlaczego: ${reason}" / "Why: ${reason}". The spec uses invented texts without the prefix, so it never shows this.
- **Fix**: Show the backend text as is (drop the `moveReason` label wrapper) and use realistic, prefixed texts in the spec.
- **Decision**: FIXED (caption shows the backend reason text as is; `matchTable.moveReason` label removed from messages/pl/en; spec expects realistic prefixed texts)

### F3 — Reason kinds looser than their definitions (FAVORABLE_TRADE, DEFEND)

- **Severity**: 💬 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Plan Adherence
- **Location**: src/main/java/com/example/itcardbrawl/game/ComputerOpponent.java:200
- **Detail**: `FAVORABLE_TRADE` goes to any attack on a character that neither kills it nor hits a Guard. It never checks that the player's board loses more than the computer's own board, as the plan defines. A Guard given at low own HP shows up as `STRENGTHEN`, not `DEFEND`. The score filter keeps this roughly true, but the reasons can overstate it.
- **Fix**: For a character attack, check that the enemyBoard gain exceeds the ownBoard loss before choosing FAVORABLE_TRADE (otherwise fall back to the largest component). Map an ownBoard gain at low own HP to DEFEND.
- **Decision**: FIXED (FAVORABLE_TRADE only when the combined board swing > 0, else WEAKEN; ownBoard gain at own HP < OWN_HP_LOW → DEFEND; test `guardAtLowOwnHpIsExplainedAsDefence`. Note: with current weights a non-lethal hit on a character never beats a direct attack on the player, so FAVORABLE_TRADE is practically unreachable — no scenario test; revisit weights in S-06)

### F4 — Safety cap explained as END_NO_GAIN

- **Severity**: 💬 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: src/main/java/com/example/itcardbrawl/game/ComputerOpponent.java:63
- **Detail**: When `MAX_MOVES_PER_TURN` trips, the turn ends with "żaden ruch nie poprawia jego sytuacji", which may be false at that point. It should never happen in practice.
- **Fix**: Add a short comment at the cap that documents the accepted wording (no new reason kind).
- **Decision**: FIXED (comment at the MAX_MOVES_PER_TURN cap documents the accepted END_NO_GAIN wording)

### F5 — `actingCard` returns null instead of failing loudly

- **Severity**: 💬 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: src/main/java/com/example/itcardbrawl/game/ComputerOpponent.java:212
- **Detail**: `orElse(null)` is dereferenced later (lines 151 and 262). `legalMoves` guarantees the card is in hand, so this can only matter if that invariant breaks, and then it shows up as an NPE inside a switch guard.
- **Fix**: Replace `orElse(null)` with `orElseThrow()`.
- **Decision**: FIXED (`actingCard` uses `orElseThrow()`)

### F6 — `moveExplained` formatting ignores `e.side()`

- **Severity**: 💬 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Pattern Consistency
- **Location**: src/main/java/com/example/itcardbrawl/api/MatchLogFormatter.java:73
- **Detail**: Every other side-dependent case branches on `mine(e.side())`. This one always speaks as "Przeciwnik", which is correct only because `ComputerOpponent` is the only producer of `MoveExplained`.
- **Fix**: Add a Polish comment stating the assumption (only the computer emits MoveExplained).
- **Decision**: FIXED (comment: only ComputerOpponent emits MoveExplained, so side is not checked)
