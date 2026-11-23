---
change_id: prod-ui
title: Prod ui
status: implementing
created: 2026-11-22
updated: 2026-11-23
archived_at: null
---

## Notes

<!-- Free-form notes for this change: links, ad-hoc context, decisions that don't belong in research/frame/plan. -->

- **View (one):** `/match` → `frontend/src/app/match-table/` (artboard "Plansza bitwy" / Battle board).
- **Token source:** new CSS custom properties in `frontend/src/styles.css`; values taken from the Claude Design canvas https://claude.ai/artifact/UDTNmTGxfcCzaZ4v1yyd5t (artboards `Board.dc.html`, `Card.dc.html`, `CardSheet.dc.html`).
- **Contract variant:** no design system — introducing the contract IS this change: token block + 2–3 components used by this view (Card), no new npm dependency without the participant's decision.
- Remaining screens (lobby, new-match, sign-in, result, rules) are follow-up changes that inherit the tokens.
- **Phase 4 deviations (user decision after manual review, 2026-11-22):** viewfinder cursor enlarged to 64×64 (hotspot 32 32); lifted card flies in from its slot and floats (`match-table/lifted-card/`); full-card aside preview replaced by an in-place hover zoom (enlarged full card anchored at the hovered card); single aside split into two side columns — opponent status left, player status right; original SVG card back (`assets-media/card-back.svg`, token `--card-back`) used for deck piles and the opponent hand; draw animation for new hand cards; the hand overlaps instead of scrolling (up to 10 cards).
- **Phase 4 deviations, round 2 (user decision, 2026-11-22):** drag-to-play from the hand (pointer events, click/keyboard kept); drag-to-attack with an aim arrow (also after click-select; lifted card hidden while the arrow shows); battle log auto-scrolls to the newest entry (stick-to-bottom); attack animation (lunge, hit flash, damage numbers from HP diff, death fade) for the player's own attacks only — computer attacks are not animated because `ReplayStep` carries no attacker/target (follow-up: send `MatchEvent.Attacked` per step); Guard shield plate behind board cards, pulsing on legal targets; "Ostatnio zagrane" (last played) block removed from the zones — the battle log covers it (API field `lastEvent` kept).
- **Phase 4 fix, big screens (2026-11-23):** past 1600×900 the board regions (`.main`, both asides) grow uniformly by `--ui-scale` = min(width/1600, height/900) via `zoom`, set on the table host from a resize-tracked signal. Fixed overlays stay in screen px and scale only their card; screen-px offsets fed into transforms inside the zoomed regions (lift, draw, lunge) are divided by the scale. `.sr-only` moved to `styles.css` for the component style budget.
- **Phase 5 deviations (user decision, 2026-11-23):** no log overlay — the battle log stays in the right side column; the overlay shell serves the two graveyards and game over. Graveyard openers are the graveyard counts on the status panels (usable behind game over). The graveyard shows compact cards, and a clicked card is shown in full beside them (ability, effects, text) — so `GraveyardCardView` carries the card's catalog content (`attack`, `health`, `ability`, `duration`, `effects`, `text`). The focus ring is scoped to the match table (the light screens would hide a near-white ring).
