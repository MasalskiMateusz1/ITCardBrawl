# Review follow-ups: match-setup-table

Source: `reviews/impl-review.md` (2026-11-19)

## F3 — Atomic match update for S-03 (first-full-match)

- **Where**: `src/main/java/com/example/itcardbrawl/game/MatchStore.java`
- **Why**: Today `MatchStore` only has `save` (overwrite) + `findCurrent`, which is safe because a match never changes after start. Once S-03 adds moves (play card, end turn), a `findCurrent` → modify → `save` sequence loses updates under concurrent requests from the same player (double-click, two tabs).
- **To do in S-03 planning**: add `update(playerKey, UnaryOperator<Match>)` built on `ConcurrentHashMap.computeIfPresent` (or compare-and-set on matchId/version) and route every move through it; do not expose get-then-save for moves. Cover with a concurrent-update test.
