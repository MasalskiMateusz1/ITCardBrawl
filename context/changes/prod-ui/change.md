---
change_id: prod-ui
title: Prod ui
status: implementing
created: 2026-11-22
updated: 2026-11-22
archived_at: null
---

## Notes

<!-- Free-form notes for this change: links, ad-hoc context, decisions that don't belong in research/frame/plan. -->

- **View (one):** `/match` → `frontend/src/app/match-table/` (artboard "Plansza bitwy" / Battle board).
- **Token source:** new CSS custom properties in `frontend/src/styles.css`; values taken from the Claude Design canvas https://claude.ai/artifact/UDTNmTGxfcCzaZ4v1yyd5t (artboards `Board.dc.html`, `Card.dc.html`, `CardSheet.dc.html`).
- **Contract variant:** no design system — introducing the contract IS this change: token block + 2–3 components used by this view (Card), no new npm dependency without the participant's decision.
- Remaining screens (lobby, new-match, sign-in, result, rules) are follow-up changes that inherit the tokens.
