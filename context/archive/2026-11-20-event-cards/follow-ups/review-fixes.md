# Review fixes — event-cards

## Separate change: player HP instead of stress (from impl-review F1)

- **Decision (2026-11-21)**: Players start with 30 HP; damage lowers it, healing raises it (max 30); 0 HP = loss. Replaces the "stress" counter (0→30) everywhere.
- **Scope**: engine win/loss (`MatchRules.STRESS_LIMIT`, `addStress`, `MatchResult` reason), `StressChanged` event, `EffectKind.STRESS` and `ON_PLAY_STRESS`, view JSON (`stress` → `hp`), SPA (`match.ts`, match-table, i18n), log sentences, card texts in `factions.json`, tests (~110 references), PRD/roadmap/shape-notes terminology.
- **How**: open as its own change (`/10x-new player-hp` → `/10x-plan`), not as a review fix.
