<!-- IMPL-REVIEW-REPORT -->
# Implementation Review: Wybór frakcji i stół partii (match-setup-table)

- **Plan**: context/changes/match-setup-table/plan.md
- **Scope**: Full plan
- **Reviewed phases**: 1, 2, 3, 4
- **Date**: 2026-11-19
- **Verdict**: APPROVED
- **Findings**: 0 critical, 0 warnings, 5 observations

## Verdicts

| Dimension | Verdict |
|-----------|---------|
| Plan Adherence | PASS |
| Scope Discipline | PASS |
| Safety & Quality | PASS |
| Architecture | PASS |
| Pattern Consistency | PASS |
| Success Criteria | PASS |

Automated: `./mvnw -B verify` (77 tests), `npx ng test --watch=false` (34/34), `npm run build` — all green on 2026-11-19. Manual 1.4, 2.4, 3.3, 4.4–4.7 confirmed by the user during implementation (4.7: Firefox + Edge/Chromium).

## Findings

### F1 — Predictable instanceId maps to a known card

- **Severity**: 💡 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: src/main/java/com/example/itcardbrawl/game/MatchFactory.java:39-55
- **Detail**: `instanceId` is assigned in catalog order *before* `Collections.shuffle` (player 1..30, opponent 31..60), so an id reveals which card it is. No leak today (MatchView exposes only the player's own hand), but it becomes one as soon as S-03+ sends ids of hidden cards (opponent's played/face-down cards).
- **Fix**: Assign `instanceId` after shuffling (or draw from the shared `RandomGenerator`) and add a `MatchFactoryTests` case asserting ids don't follow catalog order.
- **Decision**: FIXED — ids assigned after shuffle; `instanceIdsFollowDealtOrderNotCatalogOrder` (red on old code)

### F2 — Catalog validation can NPE / over-allocate before aggregated error

- **Severity**: 💡 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: src/main/java/com/example/itcardbrawl/game/CardCatalog.java:167, 176-179
- **Detail**: A card with `faction: null` is still indexed; if a deck references it, `card.faction().equals(factionId)` throws NPE instead of the promised aggregated `IllegalStateException`. Copies are expanded before the 1–2 range check, so a typo like `copies: 2000000000` exhausts memory before the readable error. Trusted classpath file, startup fails either way.
- **Fix**: Use `factionId.equals(card.faction())` and expand copies only when `MIN_COPIES <= copies <= MAX_COPIES`.
- **Decision**: FIXED — null-safe compare + range-guarded expansion; tests `deckWithFactionlessCardFailsWithReadableError`, `hugeCopiesFailsWithReadableErrorWithoutExpanding` (both red on old code)

### F3 — MatchStore will need atomic update when moves arrive (S-03)

- **Severity**: 💡 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Architecture
- **Location**: src/main/java/com/example/itcardbrawl/game/MatchStore.java:19-21
- **Detail**: Safe today (immutable records, last-wins replace is intended). Once S-03 adds moves, a `findCurrent` → modify → `save` sequence loses updates under concurrent requests from the same player (double-click, two tabs).
- **Fix**: Record as an input for S-03 planning: add `update(playerKey, UnaryOperator<Match>)` on `computeIfPresent` (or compare-and-set on matchId/version); don't expose get-then-save.
- **Decision**: FIXED — queued for S-03 planning in `follow-ups/review-fixes.md` (no code change; no moves yet)

### F4 — Extra catalog checks without tests

- **Severity**: 💡 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Success Criteria
- **Location**: src/main/java/com/example/itcardbrawl/game/CardCatalog.java:87-92, 104-106, 114-119, 159-161
- **Detail**: Beyond the plan's list, validation also rejects factions without id/name, cards without id/name/type, and empty deck entries. Consistent with fail-fast intent, but untested.
- **Fix**: Add 2–3 `CardCatalogTests` cases for the blank-field checks (or accept as defensive code).
- **Decision**: FIXED — `factionWithoutNameIsRejected`, `cardWithoutTypeIsRejected`, `emptyDeckEntryIsRejected` (each red with its check removed)

### F5 — Unrealistic frontend test fixtures

- **Severity**: 💡 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Pattern Consistency
- **Location**: frontend/src/app/new-match/new-match.spec.ts:19-29; frontend/src/app/match-table/match-table.spec.ts:41
- **Detail**: `new-match.spec.ts` MatchView fixture uses `stressLimit: 20`, `deckCount: 15`; `match-table.spec.ts` has a card with `cost: 0` (rules: 30, 25, cost 1–10). Doesn't affect assertions, but fixtures may be copied into future specs.
- **Fix**: Align fixture values with the rules (stressLimit 30, deckCount 25, cost ≥ 1).
- **Decision**: FIXED — new-match.spec.ts 30/25, match-table.spec.ts cost 5 (opponent 6/24 kept on purpose to keep its counts distinguishable from the player's)
