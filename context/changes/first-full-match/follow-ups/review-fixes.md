# Follow-ups from impl-review (first-full-match)

## F4 — Duration-0 events vanish with no visible trace → S-04 (event-cards)

- **Source**: `reviews/impl-review.md` F4; user report during manual testing: "after playing event card it does not show in my active events".
- **Now**: 10 of 15 events have `duration 0`. They go straight to the graveyard (`MatchEngine.java:111`) with no effect until S-04, so only the graveyard counter changes.
- **Proposal**: For duration 0, show "Natychmiastowe" / "Instant" on the hand card instead of "Czas trwania (tury): 0", and/or add a log line when an event goes to the graveyard. Decide together with the event effects in S-04.
