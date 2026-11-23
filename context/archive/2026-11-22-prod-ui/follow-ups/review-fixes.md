# Follow-ups from the implementation review

Queued from `reviews/impl-review.md` (2026-11-23). Each item is a candidate for a new change (`/10x-new`).

## F5 — Self-host the fonts

- **Why**: `frontend/src/index.html:11` loads Inter, Space Grotesk and JetBrains Mono from fonts.googleapis.com. The strict screenshot baselines (threshold 0.05, maxDiffPixelRatio 0.001) depend on the network and on the font files Google serves, and every visitor's IP goes to Google (GDPR for an EU audience). The plan kept the link "for now".
- **Do**: put the woff2 files (only the used weights: Inter 400/500/600, Space Grotesk 500/600/700, JetBrains Mono 400/600/700) under `frontend/src/assets-media/fonts/`, declare them with `@font-face` (`font-display: swap`) in `styles.css`, drop the preconnect/css2 links, regenerate the screenshot baselines (`npm run e2e:linux -- --update-snapshots`).
- **Check**: fonts are served from `/media/` without a session; the Linux suite is green.

## F7 — Split match-table.ts

- **Why**: `frontend/src/app/match-table/match-table.ts` is ~1,200 lines (~59 kB). Drag-to-play, drag/click aim with the arrow, attack animation, replay and the turn countdown all live in the table component. It holds no game rules, but every board change lands in this one file.
- **Do**: extract the pointer gesture layer (press/drag/aim, `queueMove`, ghost and drop zone state) and the attack effects (lunge, hit flash, damage floats) into injectable services or directives scoped to the table; keep `match-table.ts` as the orchestrator of state, moves and replay. Move the matching blocks of `match-table.spec.ts` alongside.
- **Check**: no behaviour change: `ng test` and the Linux screenshot suite stay green without new baselines.
