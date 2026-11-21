---
change_id: event-cards
title: Event cards
status: impl_reviewed
created: 2026-11-20
updated: 2026-11-21
archived_at: null
---

## Notes

<!-- Free-form notes for this change: links, ad-hoc context, decisions that don't belong in research/frame/plan. -->

- `BoardCharacterView.canAttack` means "can attack right now" (`!attackTargets.isEmpty()`, from `legalMoves`), so it is false outside the player's turn, for opponent characters and in replay steps. Intended; the SPA uses it only for the player's own Attack button. (impl-review F10)
- `research.md` (decisions D1–D18) referenced by the plan was never committed; the plan is the only surviving record of those decisions. (impl-review F10)
- Follow-up: player HP (30→0) instead of stress — see `follow-ups/review-fixes.md`. (impl-review F1)
