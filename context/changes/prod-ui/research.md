---
date: 2026-11-22T14:55:55+01:00
researcher: Claude (Opus 5.5) for Mateusz Masalski
git_commit: bb3eb646db58ed125d91ba44eeff1e70d6de5430
branch: main
repository: ITCardBrawl
topic: "Production UI for the match table (/match) against the Claude Design Battle board + Card frame"
tags: [research, ui, design-tokens, match-table, card-component, angular]
status: complete
last_updated: 2026-11-22
last_updated_by: Claude (Opus 5.5)
last_updated_note: "Participant decisions on Open Questions 1–5 (Google Fonts, desktop only, backend in scope, preview+overlays in scope, faction backgrounds as images); C5 moved to in-scope with a bounded backend slice; added backend findings."
---

# Research: Production UI for the match table (/match)

**Date**: 2026-11-22T14:55:55+01:00
**Researcher**: Claude (Opus 5.5) for Mateusz Masalski
**Git Commit**: bb3eb646db58ed125d91ba44eeff1e70d6de5430
**Branch**: main
**Repository**: ITCardBrawl

## Research Question

Two-way UI audit (10x-ui) of `frontend/src/app/match-table/` against the Claude Design canvas
(https://claude.ai/artifact/UDTNmTGxfcCzaZ4v1yyd5t — artboards `Board.dc.html`, `Card.dc.html`):
where do values and shared components live, which literals in the view should have been tokens or
components, what does the design need that the data/tests/build do or don't allow, and which 3–5
charges should the plan address.

Paths below are relative to `frontend/src/app/` unless they start with `src/`, `context/` or
`frontend/`. `B:` = `Board.dc.html`, `C:` = `Card.dc.html` (canvas version `1795355348-c7d2`).
Raw design values are saved in [design-reference.md](design-reference.md).

## Summary

- **No design system exists.** `frontend/src/styles.css:1` holds only the starter comment; there are
  no CSS custom properties in the app (inspected: every `*.css` under `frontend/src/`), no component
  library, no shared component folder. Contract variant: *no design system → introducing it is the
  change* (token block in `styles.css` + a shared Card component), no new npm dependency required.
- **Source → views:** 0 token reads in all 6 component stylesheets (inspected set: `app.css`,
  `lobby.css`, `new-match.css`, `sign-in.css`, `language-switch.css`, `match-table.css`).
- **View → source:** `match-table/match-table.css` has 21 lines with hex colours (grep count) and 60
  lines with colour/size literals; the design's palette is ~20 neutral/semantic values plus a
  3-faction accent set (design-reference.md).
- **Design ⇄ data mismatches** the plan must decide on: board characters carry no `cost`/`faction`
  (`game/match.ts:108-122`), graveyard is a count only (`game/match.ts:143,156`), and the design has
  art/flavor/card-preview/graveyard-overlay features with no data. Design draws only 1440×900 and none
  of focus-visible / error / loading / urgent timer / targeting / opponent-moving / game-over.
- **Hard constraints:** component CSS budget 4 kB warn / 8 kB error in the production build that CI
  runs (`frontend/angular.json:41-43`; `match-table.css` is already ~3 kB); anonymous static access is
  limited to `/*.js`, `/*.css`, `/media/**` (`src/main/java/com/example/itcardbrawl/config/SecurityConfig.java:27`);
  `match-table.spec.ts` (1597 lines) queries ~90 distinct class/structure selectors that a restyle must
  keep; PRD excludes mobile (`context/foundation/prd.md:135`).

## Charges

| # | Category | Evidence | Impact on the player | Status |
| --- | --- | --- | --- | --- |
| C1 | Missing tokens | `match-table/match-table.css:2,21,34,56,93-94,122,135-136,180,193,199` — `#ccc`, `#555`, `#f0f0f0`, `#999`, `#1a73e8`, `#c62828`, `#2e7d32` literals; radii `8px`/`4px` repeated (3, 35, 57, 102, 150, 194, 212; 73, 130); `frontend/src/styles.css:1` empty | The table renders as a light, unstyled wireframe unrelated to the dark Battle board; no one place to apply the theme, so every later screen will invent its own greys. | → plan (tokens phase) |
| C2 | Missing shared component | Board character duplicated `match-table.html:63-104` vs `212-253`; active event `111-122` vs `260-271`; last played `127-137` vs `276-286`; effects list ×5 (`117,130,266,279,311`); hand card `292-325`; stats `dl` `16-37` vs `179-196`; pill style shared ad hoc (`match-table.css:69-74,125-132`) | Cards look and behave differently depending on where they sit; the design's single Card frame (full + compact, `C:15-57`) cannot be applied consistently, and Result/Rules/FactionSelect would copy it again. | → plan (view phase: `Card` component + badge/pill) |
| C3 | Accidental architecture | `app.html:1-4` renders `<header><h1>ItCardBrawl</h1><app-language-switch/>` above every route; `match-table.html:344` back link outside every layout region, rendered in all states; design is a full-screen 1440×900 board with "Wyjdź do menu" in the aside (`B:18, B:204`) | On the board, a generic page header steals vertical space the 1440×900 layout needs, and the exit link floats at the bottom of the page instead of being a deliberate control. | → plan (view phase: board layout owns its chrome); keep `app.spec.ts` header/h1 contract on other routes |
| C4 | Missing states (accidental architecture) | `match-table.html:1` + `340-344`: while `match()` is null with no error only the back link renders (no loading); errors are bare `<p class="message">` with no `role="alert"` (`173-175`, `340-342`) and no `.message` rule; no focus styles anywhere (grep over `frontend/src/**/*.css`: 0 `outline`/`:focus` rules); empty board/hand/events render an empty `<ul>` under a heading (`60-124`, `209-327`); target/attack/play buttons are named only "target"/"attack"/"play" (`91,101,240,250,322`) | Opening `/match` by link shows a blank page with "back to lobby" during load; keyboard players can't see where focus is; screen-reader users hear "target, target, target" with no character name; failures are easy to miss. | → plan (states phase, 7-state matrix) |
| C5 | Design ⇄ data gap | Design compact card needs cost + faction on board characters (`B:31` props), `BoardCharacter` has neither (`game/match.ts:108-122`); graveyard overlay needs card list (`B:113-137`), API exposes `graveyardCount` only (`game/match.ts:143,156`); art window / flavor (`C:21,39`) have no data; card preview aside (`B:181-183`) has no selection-for-preview state | If the plan copies the mockup literally it either invents data in the SPA (violates "keep the SPA thin", `AGENTS.md`) or ships empty art windows/zero costs. | ~~deferred~~ **→ plan (backend phase)** per participant decision 2026-11-22: `cost` + `faction` on board characters and active events, graveyard card list. Still **deferred**: graveyard "how"/turn (needs new engine tracking) and flavor text (new pl/en content for every card). Art window renders the placeholder icon as drawn (`C:21-23`). See *Decisions*. |

## Detailed Findings

### Design reference (Board + Card)

- Layout: flex row of `main` (flex 1) and `aside` (280 wide) inside a 1440×900 frame with
  `overflow: hidden` (`B:18`, `B:155`). Main: opponent zone 270 tall (`B:21-53`) → turn bar 56
  (`B:55-70`) → player zone 270 (`B:72-104`) → hand flex 1 (`B:106-112`); overlays for graveyard and
  log cover `main` (`B:113-152`). Aside: opponent status (`B:156-179`) → full-card preview
  (`B:181-183`) → player status with budget pips (`B:185-203`) → exit link (`B:204`).
- Zones: up to 5 compact cards per side, empty slots dashed (`B:35`), "Aktywne wydarzenia" with a
  turns-left chip (`B:42-49`), each zone over a faction background component (`B:22-24, 73-75`; the
  `BgDevelopers/BgTesters/BgNeutral` artboards were not read in this research).
- Card: full 240×336 and compact 150×210 (`C:16, C:43`); faction-driven accent/soft/tint
  (`C:65-69`); character attack (gold) and HP (red) badges; event duration chip; states drawn:
  selected (`C:91`) and not playable (`C:92-93`). Not drawn: modified stats, damaged health, trapped,
  can-attack.
- States drawn on the board: default, selected (`aria-pressed`), empty slot, expanded overlays
  (`aria-expanded`). Not drawn: hover (only `cursor: pointer`), focus-visible, disabled End turn,
  error, loading, urgent timer, opponent's turn, attack targeting, game over.
- Only 1440×900 is drawn (`B:18`; all `canvas.json` screen frames are 1440×900).
- Typography: Inter body, Space Grotesk display, JetBrains Mono numbers via a Google Fonts `css2` link
  (`B:12`). Full palette and sizes: design-reference.md.

### Current view (`match-table/`)

- One component, no children. Regions: result banner (`match-table.html:2-12`), opponent zone
  (`14-139`), turn bar (`141-171`), in-table error (`173-175`), player zone incl. hand (`177-328`), log
  (`330-339`), back link (`344`).
- State handling exists in TS and must be preserved: `busy()` = pending || replaying
  (`match-table.ts:226-228`), replay at 700 ms steps (`match-table.ts:29-32`, `293-304`), urgent timer
  ≤10 s (`match-table.ts:35, 95-98`) with auto end-turn at 0 (`359-371`), attacker/event selection
  mutually exclusive (`154`, `191`), 404 on load → `/new-match` (`395-397`).
- Data (`game/match.ts`): `PlayerView`/`OpponentView` (`134-158`) give faction {id,name}, hp/maxHp,
  budget current/max, deckCount, hand or handCount, board, activeEvents, graveyardCount, lastEvent;
  `HandCard` (`86-102`) has type, cost, neutral, attack/health, ability, duration, effects, playable,
  targets; `BoardCharacter` (`108-122`) has effective vs base attack/health and separate `damage`;
  `TurnView` (`179`) has `timeLimitSeconds`. Everything the turn bar timer bar and HP bars need is
  computable from these.
- Accessibility today: section `aria-label`s (`14`, `177`, `330`), `role="status"` (`150,156,158`),
  `role="timer"` (`162`); selected cards are CSS-only (no `aria-pressed`).

### Constraints

- **Tests:** `match-table.spec.ts` uses a whitespace-collapsing `text()` helper 122× and selectors
  such as `.end-turn` (15 button lookups), `.player .card` (10), `.turn` (12), `.opponent-moving` (11),
  `.character-stats.modified`, the `dl` structure `.hp dd`/`.budget dt`, aria-labels "Opponent"/"You"
  (`match-table.spec.ts:408-409`), `role` checks (`1172`, `1281`) and `disabled` on real `<button>`s.
  Child components are fine (their DOM is inside the fixture), but class names and element types must
  survive or the spec changes become part of the plan. `app.spec.ts` asserts `header`, `h1`,
  `router-outlet`, `app-language-switch button`.
- **Build budget:** `anyComponentStyle` 4 kB warn / 8 kB error (`frontend/angular.json:41-43`), CI runs
  the production build. Tokens in global `styles.css` count against the 500 kB initial budget instead
  (main bundle ~276 kB). Splitting card styles into a `Card` component keeps `match-table.css` under
  the limit.
- **Fonts:** no CSP anywhere (no header config in `src/main`, `deploy/Caddyfile`, `Dockerfile`), so a
  Google Fonts link would load. Self-hosted font files referenced via `url()` from `styles.css` are
  emitted by Angular to `/media/` and pass `SecurityConfig.java:27`; files dropped into
  `frontend/public/fonts/` would be served at `/fonts/…` and 401 before sign-in.
- **Text:** every new label (e.g. "Aktywne wydarzenia", "brak", aria-labels with card names) goes into
  `i18n/messages.ts` + `pl.ts` + `en.ts` (`AGENTS.md`, Hard Rules).
- **Tooling:** no stylelint/eslint/husky/pre-commit; Prettier installed but not run in CI
  (`.github/workflows/ci.yml` runs `ng test` + `npm run build`). No Playwright or screenshot tool;
  Playwright was deferred to `/10x-e2e-setup` (`context/archive/2026-11-16-spa-api-walking-skeleton/plan.md:33`).
- **Agent rules:** `AGENTS.md` has no UI instructions and no rule encouraging one-off values;
  the "Utrwal to" UI block will be new.

## Code References

- `frontend/src/styles.css:1` — empty global stylesheet (token source to create)
- `frontend/src/app/match-table/match-table.html:1-344` — the view
- `frontend/src/app/match-table/match-table.css:1-227` — literals (C1)
- `frontend/src/app/match-table/match-table.ts:29-35,94-98,226-228,293-304,359-371,395-403` — state logic to keep
- `frontend/src/app/game/match.ts:86-206` — view model
- `frontend/src/app/app.html:1-4` — global header (C3)
- `frontend/angular.json:28-29,41-43` — styles entry, component budget
- `src/main/java/com/example/itcardbrawl/config/SecurityConfig.java:27` — anonymous static paths
- `context/foundation/prd.md:135` — no mobile version in v1

## Architecture Insights

- Token layer: plain CSS custom properties on `:root` in `styles.css`, named by role (design-reference.md
  proposes names). Faction accent set as `--faction-*` values selected per element via a
  `data-faction`/class on the zone or card, so components never branch on hex values.
- Component layer: a standalone `Card` component (`card/` or `ui/card/` folder, split into
  `.ts/.html/.css/.spec.ts` per `AGENTS.md`) with `variant` full|compact, rendering the existing class
  names the spec relies on, plus a small badge/pill. Angular has no shadcn equivalent here; "the stack's
  path" is `ng generate component`.
- The design's mock interactions (card preview, graveyard/log overlays) are new UI behaviour, not
  restyling; the log overlay can reuse existing `log` data, the graveyard overlay cannot (C5).
- No dark/light switch exists or is drawn; the app becomes dark-only, so the contrast check covers one theme.

## Historical Context (from prior changes)

- `context/archive/2026-11-16-spa-api-walking-skeleton/plan.md:32` — UI libraries and non-default styles were deferred until a slice needs them (supported; this is that slice).
- `context/archive/2026-11-21-sensible-computer-opponent/plan.md:218,244` — `reason` log entries must look distinct (italic or muted); keep it in the restyle.
- `context/archive/2026-11-20-first-full-match/plan-brief.md:42` — the "Ruch przeciwnika…" indicator exists so disabled buttons don't look frozen; the design does not draw it, the plan must.
- `context/archive/2026-11-19-deploy-pipeline-skeleton/plan.md:77` — origin of the static-path permit list that constrains font placement.

## Related Research

Not applicable — no prior UI research under `context/changes/**` or `context/archive/**`.

## Decisions (follow-up 2026-11-22)

Participant answers to Open Questions 1–3. They supersede the "default proposal" text of those items below.

1. **Fonts → Google Fonts** `css2` link (Inter, Space Grotesk, JetBrains Mono, weights as in `B:12`),
   placed in `frontend/src/index.html`. Feasible: no CSP in the app or proxy (see *Constraints*).
   Researcher's note recorded for later: self-hosting is the stronger long-term option for an
   EU-hosted public site (no visitor IP sent to Google, no third-party dependency); switching later
   only touches the font link and `@font-face` rules, not the tokens.
2. **Desktop only.** Mobile screenshot cell of the 10x-ui gate = N/A, reason `context/foundation/prd.md:135`.
   Visual gate widths: 1440×900 (design) and one smaller desktop, 1280×720.
3. **Backend changes are in scope**, bounded to what the view renders. Findings:
   - `Card` already carries `faction` and `cost` (`src/main/java/com/example/itcardbrawl/game/Card.java:11-12`);
     `BoardCharacterView` exposes neither (`src/main/java/com/example/itcardbrawl/api/MatchView.java:164-166`).
     Adding `cost` and `faction` (id) to `BoardCharacterView` and `ActiveEventView` is a view-only change.
   - `HandCardView` exposes `neutral` but not `faction` (`MatchView.java:143-145`); adding `faction` lets the
     Card component pick its accent from the card, not the side (neutral cards in a faction deck).
   - The graveyard is kept as `List<CardInstance>` per side (`src/main/java/com/example/itcardbrawl/game/MatchSide.java:10`),
     appended on destroy/expire/burn (`MatchEngine.java:250,321,430,460`); a `graveyard` list view
     (instanceId, cardId, name, type, cost, faction) is a view-only change. The design's "how" (played /
     expired / destroyed) and "t.N" turn (`B:129-132`) are **not** stored — tracking them changes the
     engine and stays deferred.
   - Flavor text: `factions.json` cards have keys `id, faction, name, type, cost, attack, health, ability, text`
     (no flavor) — adding it means pl+en content for every card (`AGENTS.md` rule); stays deferred.
   - Backend rules that apply: endpoints stay under `/api/**` (no new endpoint needed — fields go into the
     existing `MatchView`); `frontend/src/app/game/match.ts` types mirror the new fields; backend tests use
     MockMvc (`*Tests.java`).

4. **Card preview + overlays → default proposal accepted.** In scope: the board layout with the aside,
   the log overlay (existing `log` data) and the graveyard overlay (now backed by decision 3's list,
   without "how"/turn). Card preview of the selected card is in scope since it uses data already in the
   view; it renders the full Card variant.
5. **Faction backgrounds → image files.** The plan reads `BgDevelopers/BgTesters/BgNeutral.dc.html`
   (720×450 frames) and exports each as an image (SVG where the artboard is vector, otherwise
   WebP/PNG), commits it under `frontend/src/`, and references it with `url()` from CSS so Angular emits
   it under `/media/` (passes `SecurityConfig.java:27`; `frontend/public/` would serve it outside the
   permit list unless placed in `public/media/`). Faction → image mapping goes into the token layer
   (`--faction-bg`) so zones never name a file.

## Open Questions

1. **Fonts:** Google Fonts `<link>` (external request, matches the design) or self-hosted woff2 via
   `url()` in `styles.css` (emitted to `/media/`, no third-party call)? Self-hosting needs either
   committed font files or an `@fontsource/*` dev dependency (participant's decision).
2. **Mobile width:** PRD excludes mobile (`context/foundation/prd.md:135`). Proposal: mark the 10x-ui
   mobile screenshot N/A with that reason, and instead check a smaller desktop width (e.g. 1280×720),
   because the design's fixed 1440×900 with `overflow: hidden` would clip on common laptops.
3. **C5 data gaps:** add `cost` (and optionally `neutral`) to `BoardCharacter` and a graveyard card list
   to the API in this change, in a separate backend change, or drop those parts of the mockup? Default
   proposal: drop and defer (compact card shows no cost on the board; graveyard stays a count button).
4. **Card preview aside and overlays:** in scope for this change (new interaction state) or a follow-up?
   Default proposal: layout + log overlay in scope, card preview on selection in scope only if it needs
   no new data.
5. **Faction backgrounds:** `BgDevelopers/BgTesters/BgNeutral` artboards were not read; the plan's view
   phase must read them and decide CSS-only reproduction vs asset files (assets must live under
   `/media/` via `url()` for the same security reason as fonts).
6. **States the design omits** (focus-visible, disabled End turn, error, loading, urgent timer,
   targeting, opponent moving, result banner) need token-based designs invented in the plan, using the
   design's palette (`--color-danger` for error/urgent, `--color-text` ring for focus as with selection).
