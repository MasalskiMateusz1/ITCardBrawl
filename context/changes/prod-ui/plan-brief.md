# Production UI for the match table (/match) — Plan Brief

> Full plan: `context/changes/prod-ui/plan.md`
> Research: `context/changes/prod-ui/research.md`

## What & Why

Turn `/match` from a light, unstyled wireframe into the dark Battle board from the Claude Design canvas.
This also introduces the project's first design-system contract (tokens + a shared Card), so the
remaining screens inherit one palette and one card instead of inventing their own.

## Starting Point

There are no tokens and no shared components (`styles.css` is empty). `match-table` is one 344-line
template with duplicated card markup, per-card action buttons and a global header above it. The
backend view lacks cost/faction on board cards and exposes the graveyard as a count only.

## Desired End State

At 1440×900 the board looks as drawn, and at 1280×720 nothing clips. The main column holds the zones
with compact cards, the turn bar and a scrolling hand. The aside holds the status panels, a full-card
preview, the language switch and the exit link. Every move is a click on a card (or a status panel). Log,
graveyard and game over are overlays. Every state has a token-based look, and Playwright screenshots
guard it in CI. Each card's art window shows its own image, or a placeholder when the file is missing.

## Key Decisions Made

| Decision | Choice | Why (1 sentence) | Source |
| --- | --- | --- | --- |
| Fonts | Google Fonts link | Matches the design; no CSP blocks it; self-hosting later only touches the link | Research |
| Viewports | Desktop only: 1440×900 + 1280×720 | PRD excludes mobile; the fixed design would clip on laptops | Research |
| Backend | Add cost/faction + graveyard list to `MatchView` | The cards render real data instead of SPA-invented values | Research |
| Graveyard how/turn, flavor | Deferred | Needs engine tracking / new pl+en content | Research |
| Faction backgrounds | Exported images via `url()` → `/media/` | Passes the anonymous static permit list | Research |
| Card actions | No action buttons; clicking the card is the action | User's call; selection states are already mutually exclusive, so each click has one meaning | Plan |
| Hand click | Click plays; hover/focus shows full card in preview | One click per move; ability text readable for every card | Plan |
| Targeting feedback | Selected card lifted click-through into the board centre; viewfinder cursor | Shows what you aim with, never blocks a target; added after plan review | Plan |
| Player as target | Aside status panels become buttons when legal | Consistent "click the thing" model on existing panels | Plan |
| Header on /match | Hidden via route data; language switch in aside | Board gets the viewport; other routes unchanged | Plan |
| Layout | Fluid viewport, design sizes at ≥900 tall, scroll below 1280×720 | As drawn at 1440×900, no clipping on laptops | Plan |
| Existing spec | Keep selectors, change only forced lookups | The behaviour suite stays the safety net | Plan |
| Game over | Non-closable overlay over the dimmed board | Reuses the overlay pattern; clearly ends the match | Plan |
| Visual gate | Playwright now, mocked API, screenshots in CI | Repeatable regression instead of manual screenshots | Plan |
| Card art source | Participant supplies 31 files; plan only wires them in | No licensing/generation risk; art quality stays the participant's call | Plan |
| Card art lookup | `/media/cards/<cardId>.webp`, placeholder on load error | No data/API change; images can land card by card without breaking the board | Plan |
| Card art phase | New Phase 8 after the visual gate | Placeholder baselines first (art mocked to 404), art state added at the end | Plan |

## Scope

**In scope:**
- Backend view fields and their tests
- Token layer, fonts and background images
- `ui/card` (full + compact)
- Board layout with aside and preview
- Click interactions
- Log, graveyard and game-over overlays
- 7-state matrix plus urgent timer, opponent's turn and targeting
- i18n for all new text
- `AGENTS.md` UI rule
- Playwright setup with a CI job and screenshot baselines
- Card art slot with placeholder fallback, then the participant's 31 card images (Phase 8)

**Out of scope:** producing card art (supplied by the participant), other screens, mobile, light theme, graveyard how/turn, flavor text, self-hosted fonts,
a role-based spec rewrite, and npm dependencies beyond `@playwright/test`.

## Architecture / Approach

Data → contract → pixels → gate. `MatchView` (Java) and `match.ts` carry everything a card shows.
`styles.css` `:root` tokens plus `[data-faction]` sets feed a presentational `app-card`. The match table
wraps each card in a `<button class="card-action">` and dispatches clicks to the existing
`play`/`selectAttacker`/`attack`/`chooseTarget` legality logic. A `board-overlay` shell hosts the
log, graveyard and game over. Playwright drives the built SPA against JSON fixtures with no backend.

## Phases at a Glance

| Phase | What it delivers | Key risk |
| --- | --- | --- |
| 1. Backend data | cost/faction, graveyard list, tests, TS mirror | Spec fixtures needing new defaults everywhere |
| 2. Tokens, fonts, backgrounds | `:root` tokens, Google Fonts, 3 bg images, AGENTS.md rule | Bg artboards may not export cleanly to SVG |
| 3. Card component | `ui/card` full/compact with states, art slot + placeholder fallback, spec | Undrawn markers (modified/damaged) need invented looks |
| 4. Board + clicks | Layout, header hidden, click-a-card, status targets, preview, targeting lift + viewfinder, spec changes | About 45 spec lookups to remap; CSS budget |
| 5. Overlays + states | Log/graveyard/game-over overlays, state matrix | Focus management; contrast of muted text |
| 6. Playwright setup | `/10x-e2e-setup`, mocked API fixtures, CI job | New dependency + CI time |
| 7. Visual gate | 12 states × 2 viewports screenshot baselines (art mocked to 404) | Font anti-aliasing flakiness between machines |
| 8. Card images | 31 WebP files in `public/media/cards/`, completeness check vs `factions.json`, "with art" screenshots | Blocks on the participant's files; possible conversion tool needed |

**Prerequisites:** canvas access for the `Bg*` artboards; 31 card images (one per `cardId`) before Phase 8. `main` is green.
**Estimated effort:** about 6–8 sessions across 8 phases (phase 4 is the largest).

## Open Risks & Assumptions

- Google Fonts are fetched during CI screenshot runs. A network hiccup can fail the gate. Fallback: block the
  font request in tests and accept fallback fonts in baselines.
- Image files must be named exactly by `cardId`; the completeness check catches typos and renamed cards.
- Click-to-play has no confirmation, so a misclick plays a card. This is accepted by decision.
- States the canvas doesn't draw (modified/damaged stats, targeting, game over) are designed in the plan
  from the palette and may need a canvas follow-up.

## Success Criteria (Summary)

- A full match can be played on the new board with the mouse only and with the keyboard only, at 1440×900 and 1280×720.
- No hex or radius literals remain in the board's component CSS, and cards everywhere come from `ui/card`.
- Unit, backend and Playwright screenshot suites are green in CI.
