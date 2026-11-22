# Production UI for the match table (/match) Implementation Plan

## Overview

Rebuild `/match` as the dark "Plansza bitwy" (Battle board) from the Claude Design canvas
(https://claude.ai/artifact/UDTNmTGxfcCzaZ4v1yyd5t — `Board.dc.html`, `Card.dc.html`). The change
introduces the project's first design-system contract: a role-named token layer in
`frontend/src/styles.css` and a shared `Card` component (full + compact). It also adds the card data
the design needs to the backend view, replaces the per-card action buttons with "click the card"
interactions, and gates the result with Playwright screenshots. The research charges C1–C5 map
onto phases 1–5.

## Current State Analysis

- `frontend/src/styles.css:1` is empty: no tokens, no shared components. `match-table.css` holds 21
  hex literals and repeated radii (C1, `research.md`).
- `match-table.html` (344 lines) is one component. Board characters, events, effects and stats are
  duplicated between the opponent and player zones (C2). Every board character renders its full
  ability text plus `attack`/`target`/`event-target` buttons (`match-table.html:80-103, 229-252`),
  hand cards render a `play` button (`322`), and players are targeted through `.attack-player` /
  `.target-player` buttons (`38-57, 199-208`).
- The global header (`app.html:1-4`) renders above every route. The back link (`match-table.html:344`)
  floats outside any region (C3).
- No loading state, no `role="alert"`, no focus styles, empty lists render as an empty `<ul>` (C4).
- `BoardCharacterView` / `ActiveEventView` carry no `cost`/`faction`, `HandCardView` carries no
  `faction`, and the graveyard is a count only (`src/main/java/com/example/itcardbrawl/api/MatchView.java:143-187`).
  The domain already has everything: `Card.faction()`/`cost()` (`game/Card.java:11-12`),
  `MatchSide.graveyard()` (`game/MatchSide.java:10`) (C5).
- State logic to keep: `busy()` (`match-table.ts:226-228`), 700 ms replay (`29-32, 293-304`), urgent
  timer ≤10 s with auto end-turn (`35, 95-98, 359-371`), attacker/event selection mutually exclusive
  (`154, 191`), 404 → `/new-match` (`395-397`).

## Desired End State

On `/match` the player sees the Battle board as drawn at 1440×900. Opponent zone, turn bar, player
zone and a horizontally scrolling hand of compact cards sit in the main column. The 280 px aside holds
the opponent status panel, the full-card preview, the player status panel with budget pips, the
language switch and "Wyjdź do menu". The board fills the viewport fluidly, stays unclipped at
1280×720 and scrolls only below that. The global header is not rendered on `/match`.

Every move is a click on a card (or a status panel for the player as target). While aiming, the
selected card floats click-through in the board centre and the cursor becomes a viewfinder. Hovering or focusing a
card shows it full-size in the preview. Log, graveyard and game-over are overlays over the main
column. Loading, errors, focus, urgent timer, opponent's turn and targeting each have a token-based
look. All colours, radii and fonts on the board come from tokens. Playwright screenshot tests of the
key states at both viewports pass locally and in CI.

Verify: `./mvnw -B verify`, `cd frontend && npx ng test --watch=false`, `npm run build` (no budget
warning), `npm run e2e`, plus a visual comparison of the screenshots against the canvas.

### Key Discoveries:

- In the design the hand uses **compact** cards inside `<button aria-pressed>` (`Board.dc.html:106-112`).
  The full variant appears only in the aside preview (`B:181-183`), so the Card component can stay purely
  visual and the view wraps it in a button.
- The compact card shows cost, name (2-line clamp), the art window, one `compactLine` (keyword or
  duration) and attack/HP badges. It has no ability text (`Card.dc.html:42-56`), so the preview is the
  only place ability text appears.
- `HAND_LIMIT = 10`, `BOARD_LIMIT = 5` (`game/MatchRules.java:28,31`). Five compact cards fit a zone,
  ten hand cards (about 1626 px) don't fit the about 1160 px main column, so the hand scrolls.
- `MatchViewTests.java:284` pins the JSON key set of the side views. It must gain `graveyard`.
- `match-table.spec.ts` references the removed action buttons about 45 times. These lookups are the
  bulk of the spec changes.
- Component style budget is 4 kB warn / 8 kB error (`frontend/angular.json:41-43`), and `match-table.css`
  is already about 3 kB. Board CSS must be split across child components.
- Assets referenced by `url()` from CSS are emitted under `/media/`, which `SecurityConfig.java:27`
  permits anonymously. Files in `frontend/public/` would get a 401 before sign-in.

## What We're NOT Doing

- Restyling lobby, new-match, sign-in, result or rules screens (follow-up changes inherit the tokens).
  Tokens are defined on `:root`, but the dark ground is applied by the board root, not `body`.
- Mobile layout (`context/foundation/prd.md:135`). The mobile screenshot cell is N/A.
- Graveyard "how" (played/expired/destroyed) and "t.N" turn (`B:129-132`). They need engine tracking.
- Flavor text (`C:39`). It would need pl+en content for every card. Art windows render the placeholder
  icon as drawn.
- Self-hosted fonts (Google Fonts link for now; switching later touches only the link and `@font-face`).
- A light theme or theme switch.
- New npm dependencies other than `@playwright/test` (via `/10x-e2e-setup`).
- Rewriting `match-table.spec.ts` against roles. Selectors are kept, and only the removed buttons'
  lookups change.

## Implementation Approach

Data first, then contract, then pixels. Phase 1 makes the backend and the `match.ts` mirror carry
everything the cards render, so no phase invents data in the SPA. Phase 2 lays down tokens, fonts and
background images. Phase 3 builds the Card on those tokens in isolation. Phase 4 swaps the board's
markup and interactions to use the Card and the new layout, keeping existing class names on the list
items so the behaviour spec stays the safety net. Phase 5 covers the states and overlays the design
doesn't draw. Phases 6–7 add Playwright and the screenshot gate.

Click semantics (decided in planning). Attacker and event selections stay mutually exclusive:
- Hand card click: a playable card is played. An event that requires a target becomes the
  `selectedEvent` (as today's `play()`).
- Own character click with no selection: if `canAttack`, it becomes `selectedAttacker`. Clicking the
  selected attacker again cancels.
- Opponent character click with an attacker selected: attack it, if it is in `attackTargets`.
- Any character click with an event selected: target it, if it is in the event's `targets`.
- Status panel (aside) click: the opponent panel is the attack/event target for the opponent, the player
  panel is the event target for the player. Panels are buttons only while such a target is legal,
  otherwise plain regions.
- Cards that are not a legal action in the current state are rendered as disabled buttons (not playable
  look `opacity 0.6; saturate(0.3)` for hand cards only). The cancel buttons in the turn bar stay.

## Critical Implementation Details

- **User experience spec.** The accessible name of each card button combines the action and the card
  name (e.g. pl "Atakuj: Senior Developer", "Cel: …", "Zagraj: …"), built from new i18n functions.
  Selected cards carry `aria-pressed="true"`. When an overlay opens, focus moves to its heading and
  returns to the opening button on close (Escape on the overlay itself closes it, with no global keydown
  handler). The game-over overlay takes focus once when the result first appears.
- **Performance constraints.** `anyComponentStyle` 4 kB warn / 8 kB error applies per component. Keep
  card CSS in `Card`, overlay CSS in an overlay component, and aside panels in their own component,
  so `match-table.css` holds only the grid layout.

## Phase 1: Backend data for the cards

### Overview

Expose the card data the design renders: cost and faction on board characters and active events,
faction on hand cards, and the graveyard card list per side. This changes the view only, not the engine.

### Changes Required:

#### 1. Match view records

**File**: `src/main/java/com/example/itcardbrawl/api/MatchView.java`

**Intent**: Add the fields from the domain `Card` to the view records so the Card component picks its
accent from the card itself (neutral cards in a faction deck) and the board can show cost.

**Contract**: `BoardCharacterView` and `ActiveEventView` gain `int cost` and `String faction` (the
card's faction id, e.g. `developers`, `testers`, `neutral`, the same ids as `FactionRef.id`).
`HandCardView` gains `String faction` (`neutral` stays). `PlayerView` and `OpponentView` gain
`List<GraveyardCardView> graveyard` (oldest first, as stored), keeping `graveyardCount`. New record
`GraveyardCardView(int instanceId, String cardId, String name, CardType type, int cost, String faction)`.
New fields are appended at the end of the record components.

#### 2. Backend tests

**File**: `src/test/java/com/example/itcardbrawl/api/MatchViewTests.java` (and `MatchControllerTests.java` if it pins JSON)

**Intent**: Pin the new fields and the graveyard list. A destroyed character and an expired event show
up in its owner's graveyard with name/cost/faction, and a neutral card in a faction deck reports
`neutral` as its faction.

**Contract**: The key-set assertion at `MatchViewTests.java:284` includes `graveyard`. Test descriptions
`.as(…)` are in Polish (`AGENTS.md`).

#### 3. Frontend type mirror

**File**: `frontend/src/app/game/match.ts`

**Intent**: Mirror the new fields so templates and specs type-check. Update spec fixtures that build
these objects.

**Contract**: `HandCard.faction: string`, `BoardCharacter.cost: number` + `faction: string`,
`ActiveEvent.cost: number` + `faction: string`, `GraveyardCard` type, and `PlayerView.graveyard` /
`OpponentView.graveyard: GraveyardCard[]`. Fixture factories in `match-table.spec.ts` (and any other spec
building `MatchView`) get default values.

### Success Criteria:

#### Automated Verification:

- Backend tests pass: `./mvnw -B verify`
- Frontend tests pass: `cd frontend && npx ng test --watch=false`
- Production build passes: `cd frontend && npm run build`

#### Manual Verification:

- `GET /api/matches/current` in a running app shows `cost`/`faction` on board characters and events, and a `graveyard` list that grows when a character dies

**Implementation Note**: After completing this phase and all automated verification passes, pause here for manual confirmation from the human that the manual testing was successful before proceeding to the next phase.

---

## Phase 2: Tokens, fonts, backgrounds

### Overview

Create the token layer (C1), load the design's fonts, export the faction backgrounds as images, and
record the UI rule for future agents.

### Changes Required:

#### 1. Token block

**File**: `frontend/src/styles.css`

**Intent**: The single source of colour, type, radius, spacing and shadow values, named by role. The
values come from `design-reference.md`.

**Contract**: `:root` custom properties:
- colours `--color-bg`, `--color-overlay`, `--color-panel`, `--color-surface`, `--color-raised`,
  `--color-raised-2`, `--color-border`, `--color-border-strong`, `--color-text`,
  `--color-text-secondary`, `--color-text-muted`, `--color-on-accent`, `--color-attack`,
  `--color-on-attack`, `--color-danger`, `--color-on-danger`, `--color-slot-border`,
  `--color-slot-fill`, `--color-divider`, `--color-link`, `--color-link-hover`, plus `--color-focus`
  (= `--color-text`, the selection ring colour);
- fonts `--font-body`, `--font-display`, `--font-mono`;
- radii `--radius-zone` 16, `--radius-card` 14, `--radius-card-compact` 12, `--radius-row` 10,
  `--radius-art` 8, `--radius-pill` 20;
- spacing `--space-1..` covering 6/8/12/14/16/20;
- `--shadow-card`, `--shadow-card-selected`.

Faction sets are selected by attribute: `[data-faction='developers'|'testers'|'neutral']` sets
`--faction-accent`, `--faction-accent-soft`, `--faction-tint` and `--faction-bg` (the background
image `url()`). Components read only `--faction-*`, never a faction hex or file name. Unknown ids fall
back to the neutral set. Body stays unstyled (other screens unchanged).

#### 2. Fonts

**File**: `frontend/src/index.html`

**Intent**: Load Inter 400/500/600, Space Grotesk 500/600/700 and JetBrains Mono 400/600/700 as in `B:12`.

**Contract**: `preconnect` links to `fonts.googleapis.com` / `fonts.gstatic.com` plus one `css2` stylesheet link with `display=swap`.

#### 3. Faction background images

**File**: `frontend/src/assets-media/bg-developers.svg`, `bg-testers.svg`, `bg-neutral.svg` (folder under `frontend/src/`, not `public/`)

**Intent**: Read the `BgDevelopers/BgTesters/BgNeutral.dc.html` artboards (720×450) from the canvas and
export each one as SVG where the artboard is vector, otherwise WebP. Reference them only from the
`--faction-bg` tokens so Angular emits them under `/media/`.

**Contract**: Built output contains the files under `dist/.../media/`, reachable without a session
(`SecurityConfig.java:27`). No emoji, no gradients beyond what the artboard draws.

#### 4. Agent rule

**File**: `AGENTS.md`

**Intent**: Persist the contract so the next agent keeps using it (10x-ui "Utrwal to").

**Contract**: New `## UI` section with these rules. Colours, radii, fonts, spacing and shadows come from
`styles.css` tokens, with no hex or px-radius literals in component CSS. Faction looks come from
`data-faction` + `--faction-*`. Cards are rendered with `ui/card` (no per-screen card markup). Assets
referenced by CSS live under `frontend/src/` and are referenced via `url()` (served at `/media/`).

### Success Criteria:

#### Automated Verification:

- Production build passes and emits the three backgrounds under `media/`: `cd frontend && npm run build && ls dist/*/browser/media/`
- Frontend tests pass: `cd frontend && npx ng test --watch=false`

#### Manual Verification:

- Lobby, new-match and sign-in look unchanged after the token block lands
- The three exported backgrounds match their canvas artboards by eye

**Implementation Note**: After completing this phase and all automated verification passes, pause here for manual confirmation from the human that the manual testing was successful before proceeding to the next phase.

---

## Phase 3: Card component

### Overview

The shared Card (C2) in full and compact variants, built only from tokens, presentational (no click
handling), with its own spec.

### Changes Required:

#### 1. Card component

**File**: `frontend/src/app/ui/card/card.ts`, `card.html`, `card.css`, `card.spec.ts`

**Intent**: Render the design's Card frame (`C:15-57`) for characters and events, from hand, board,
active events, preview and graveyard.

**Contract**: Standalone `app-card` with inputs:
- `variant: 'full' | 'compact'`, `faction: string`, `type: CardType`, `name`, `cost`, `attack?`,
  `health?` (displayed health = remaining HP), `baseAttack?`, `baseHealth?`, `damaged?: boolean`,
  `keyword?: string`, `ability?: string`, `text?: string`, `effects?: string[]`, `duration?: number | null`,
  `turnsLeft?: number`;
- state flags `selected`, `playable` (default true), `trapped`.

Host sets `data-faction`. Root is an `<article>` with an `aria-label` of the card name. The cost badge
and stat badges carry aria-labels from i18n (`cost`, `attack`, `HP`). Modified stats (effective ≠ base)
and damaged health get a visible marker on the badge (ring in `--color-text`), because the design
doesn't draw them. The trapped state shows a chip. The compact line shows, in priority order, trapped,
keyword, event duration/turns left. The full variant shows ability, text and effects, and `instant` when
`duration === 0`. Fixed sizes 240×336 / 150×210.

#### 2. i18n

**File**: `frontend/src/app/i18n/messages.ts`, `pl.ts`, `en.ts`

**Intent**: Card labels (cost, attack, HP, trapped chip) without hardcoded text (Hard Rule).

**Contract**: New `card` section in `Messages` with both translations.

### Success Criteria:

#### Automated Verification:

- Card spec covers both variants, character vs event, modified/damaged/trapped markers, not-playable state and the `data-faction` host attribute: `cd frontend && npx ng test --watch=false --include src/app/ui/card/card.spec.ts`
- Full frontend suite passes: `cd frontend && npx ng test --watch=false`
- `card.css` contains no hex literal: `! grep -nE '#[0-9a-fA-F]{3,8}\b' frontend/src/app/ui/card/card.css`

**Implementation Note**: After completing this phase and all automated verification passes, pause here for manual confirmation from the human that the manual testing was successful before proceeding to the next phase.

---

## Phase 4: Board layout and click interactions

### Overview

Replace the match-table markup with the Battle board layout, the Card component and click-to-act
interactions (C2, C3). Hide the global header on `/match`.

### Changes Required:

#### 1. App shell

**File**: `frontend/src/app/app.routes.ts`, `app.ts`, `app.html`, `app.spec.ts`

**Intent**: The board owns the full viewport. Other routes keep the header.

**Contract**: The `/match` route has `data: { fullscreen: true }`, and `App` reads the active route's data
(router events → signal) and skips the `<header>` when it is set. `app.spec.ts` keeps its header/h1
assertions for a normal route and adds one for `/match` without the header.

#### 2. Board layout

**File**: `frontend/src/app/match-table/match-table.html`, `match-table.css`

**Intent**: The design's structure (`B:18-204`): `main` (opponent zone → turn bar → player zone →
hand) plus a 280 px `aside`. The layout is fluid: 100vw × 100vh grid, with design sizes at ≥ 900 px
height. Below that, zones and the hand tighten (zone height `clamp`). Under 1280×720 the page scrolls
instead of clipping.

**Contract**:
- Zones keep `section.zone.opponent` / `.zone.player` with their aria-labels. Each zone renders five
  slots (cards + dashed empty slots), plus "Aktywne wydarzenia" with turns-left chips and the faction
  background via `data-faction` on the zone.
- Board `<li>` items keep `.character` / `.event` and `.selected`. Hand `<li>` items keep `.card` / `.selected`.
- The last-played block keeps `.last-played` / `.nothing-played`.
- The turn bar keeps `.turn`, `.turn-timer`, `.end-turn`, `.cancel-attack`, `.cancel-target`,
  `.choose-target`, `.opponent-moving`, `.move-reason`.
- The hand is a horizontally scrolling row of compact cards.
- Stats move to the aside status panels (`.status.opponent` / `.status.player`), keeping the
  `dl.stats` / `.hp` / `.budget` / `.deck-count` / `.hand-count` / `.graveyard` structure. The
  player panel adds budget pips and an HP bar.
- The language switch and the exit link (`routerLink="/"`) sit at the bottom of the aside.
- Literals in `match-table.css` are replaced by tokens. Aside panels move into a child component if
  the CSS budget needs it.

#### 3. Click interactions

**File**: `frontend/src/app/match-table/match-table.ts`, `match-table.html`

**Intent**: Drop the per-card action buttons and make the card the action, following the click semantics
in *Implementation Approach*.

**Contract**:
- Each card is wrapped in `<button type="button" class="card-action">` with `[attr.aria-pressed]`,
  `[disabled]` when no action is legal or `busy()`, and an accessible name from new i18n functions
  (`playCard(name)`, `selectAttacker(name)`, `attackCard(name)`, `targetCard(name)`).
- A single `onCardClick(zone, item)` dispatches to the existing `play`/`selectAttacker`/`attack`/
  `chooseTarget`. Their legality checks (`canPlay`, `canAttackTarget`, `canTargetEvent`) are reused
  unchanged.
- Status panels render as `<button class="status-target">` only when `canAttackTarget(null)` /
  `canTargetEvent(null, side)` is true.
- The buttons `.attack`, `.target`, `.event-target`, `.play`, `.attack-player` and `.target-player`
  are removed.

#### 4. Card preview

**File**: `frontend/src/app/match-table/match-table.ts`, `match-table.html`

**Intent**: The aside shows the full card for whatever card is hovered or focused (hand, either board,
active events). When nothing is hovered, it shows the selected attacker/event. Otherwise it shows a
muted "brak" placeholder.

**Contract**: `previewed = signal<PreviewRef | null>` set on `mouseenter`/`focusin` and cleared on
`mouseleave`/`focusout`. The preview renders `app-card variant="full"`.

#### 5. Targeting presentation

**File**: `frontend/src/app/match-table/match-table.html`, `match-table.css`, `frontend/src/assets-media/cursor-target.svg`

**Intent**: Make it obvious that the player is aiming (added after plan review). While `selectedAttacker`
or `selectedEvent` is set, the selected card is lifted into the centre of the board and the cursor over
the board becomes a viewfinder.

**Contract**:
- Lifted card:
  - An `app-card variant="compact"` (scale about 1.1, `--shadow-card-selected`) is absolutely
    centred over the turn bar of the main column.
  - It has `pointer-events: none` and `aria-hidden="true"`, because the selection is already
    announced by `aria-pressed` and `.choose-target`.
  - It fades to about 35 % opacity while the pointer is over a legal target under it (the main
    column carries a `targeting-hover` state set from the target buttons' `mouseenter`/`mouseleave`).
  - It disappears when the selection ends (cancel, move done, `busy()` replay).
  - The original card stays in its slot with the selected ring.
- Viewfinder cursor:
  - While targeting, the main column and the aside status panels use
    `cursor: url(cursor-target.svg) 16 16, crosshair`. The SVG is a 32×32 stroke crosshair in the
    `--color-attack` hex, baked in because cursor images can't read CSS variables.
  - The cursor is referenced from the token layer as `--cursor-target`, so components never name
    the file.
  - Disabled (non-legal) card buttons and panels show `not-allowed`.
  - Outside targeting, cards use `pointer`.

#### 6. Spec adaptation

**File**: `frontend/src/app/match-table/match-table.spec.ts`

**Intent**: Keep the behaviour suite and change only what the DOM change forces.

**Contract**:
- Lookups of removed buttons become `.card-action` inside the same `<li>` (or `.status-target` inside
  `.status.opponent` / `.status.player`).
- Stats lookups are scoped to the aside panels.
- New tests: click semantics (select/cancel attacker by clicking it again, a disabled non-legal card),
  `aria-pressed`, accessible names with the card name, the preview following hover/focus, the lifted
  card rendered only while an attacker/event is selected, and the header absent on `/match`.
- Every changed `describe`/`it` is listed in the phase's commit message.

#### 7. i18n

**File**: `frontend/src/app/i18n/messages.ts`, `pl.ts`, `en.ts`

**Intent**: New labels: action names, "Aktywne wydarzenia", empty slot, preview placeholder ("brak"),
"Wyjdź do menu", status panel names. Unused button labels are removed.

**Contract**: Both translations for every new key.

### Success Criteria:

#### Automated Verification:

- Frontend tests pass: `cd frontend && npx ng test --watch=false`
- Production build passes without a component style budget warning: `cd frontend && npm run build`
- No hex literal in match-table CSS: `! grep -nE '#[0-9a-fA-F]{3,8}\b' frontend/src/app/match-table/*.css`

#### Manual Verification:

- At 1440×900 the board matches the canvas layout (zones, turn bar, hand, aside)
- At 1280×720 nothing is clipped and the hand stays reachable
- A full match is playable with the mouse only: play a character, attack a character, attack the opponent, play a targeted event on a character and on a player
- A full match is playable with the keyboard only (Tab/Enter), and screen-reader names include the card names
- Lobby and new-match still show the header, and `/match` does not
- While targeting, the selected card floats in the board centre without blocking clicks, fades over a target beneath it, and the cursor is a viewfinder (`not-allowed` on illegal targets)

**Implementation Note**: After completing this phase and all automated verification passes, pause here for manual confirmation from the human that the manual testing was successful before proceeding to the next phase.

---

## Phase 5: Overlays and states

### Overview

Overlays and the states the design omits (C4): the 7-state matrix of default, hover, focus-visible,
disabled, loading, error and empty, plus urgent timer, opponent's turn, targeting and game over.

### Changes Required:

#### 1. Overlay component

**File**: `frontend/src/app/match-table/board-overlay/board-overlay.ts`, `.html`, `.css`, `.spec.ts`

**Intent**: One overlay shell (`B:113-152`) covering the main column for the log, the graveyard and game
over, so overlay CSS stays out of `match-table.css`.

**Contract**:
- Inputs: `title` and `closable`. Output: `closed`. It renders `role="dialog"` with `aria-labelledby`
  and moves focus to its heading on open. Escape (handled on the overlay element) and the close
  button emit `closed`.
- The opening buttons in the turn bar ("Log", "Cmentarz N" per side) carry `aria-expanded`. Focus
  returns to the opener on close.

#### 2. Log, graveyard and game-over content

**File**: `frontend/src/app/match-table/match-table.html`, `.ts`

**Intent**: The log overlay lists the existing `m.log` entries, keeping `.log`, `.log-entries`,
`.log-entry`, `.mine` and `.reason`, with `reason` rows italic and muted
(`context/archive/2026-11-21-sensible-computer-opponent/plan.md:218`). The graveyard overlay lists a
side's `graveyard` as rows (name, type, cost, faction accent). Game over shows the result in a
non-closable overlay over the dimmed board, keeping `.result`, `.win`, `.result-reason` and
`.play-again`. The log button stays usable behind it.

**Contract**: `openOverlay = signal<'log' | 'graveyard-player' | 'graveyard-opponent' | null>`. The
game-over overlay renders whenever `m.result` is set, regardless of `openOverlay`. The log/graveyard
overlays stack above it when opened.

#### 3. State looks

**File**: `match-table.css`, `ui/card/card.css`, `src/styles.css`

**Intent**: Token-based designs for states the canvas doesn't draw.

**Contract**:
- focus-visible: a 3 px `--color-focus` outline with offset on every button/link, defined once in
  `styles.css`.
- disabled End turn: `--color-raised` with `--color-text-muted`.
- urgent timer (≤10 s): `--color-danger` text and timer bar.
- opponent's turn: the `.opponent-moving` pill pulses (respecting `prefers-reduced-motion`), and the
  player zone is dimmed.
- targeting: legal target cards and panels get the `--color-attack` ring, illegal ones stay
  un-highlighted. The lifted card and viewfinder cursor come from Phase 4 §5.
- loading: while `match()` is null with no error, a board skeleton (empty slots plus a
  `role="status"` "Ładowanie partii…").
- error: `<p class="message" role="alert">` styled in `--color-danger`, for both the in-table and the
  load error.
- empty: dashed slots for an empty board and "brak" for no events or no last-played card.

#### 4. i18n and spec

**File**: `i18n/*`, `match-table.spec.ts`, `board-overlay.spec.ts`

**Intent**: Labels for the overlays (log, graveyard, close, loading) and tests for the states.

**Contract**:
- Spec covers: the log/graveyard overlays opening/closing with focus return, graveyard rows from
  `graveyard`, the game-over overlay on `result`, the loading status while the request is pending,
  `role="alert"` on errors, and the urgent class at ≤10 s (existing tests).
- Existing `.result`/`.log` tests are adapted to the overlay.

### Success Criteria:

#### Automated Verification:

- Frontend tests pass: `cd frontend && npx ng test --watch=false`
- Production build passes without a component style budget warning: `cd frontend && npm run build`
- No hex literal in component CSS of the board: `! grep -rnE '#[0-9a-fA-F]{3,8}\b' frontend/src/app/match-table frontend/src/app/ui`

#### Manual Verification:

- Each state of the matrix (default, hover, focus-visible, disabled, loading, error, empty) plus urgent timer, opponent's turn, targeting and game over is visible and readable at 1440×900
- Text contrast at least 4.5:1 for muted labels on `--color-panel` / `--color-surface` (checked in DevTools)
- Overlays open, trap nothing, close with Escape, and return focus to their button

**Implementation Note**: After completing this phase and all automated verification passes, pause here for manual confirmation from the human that the manual testing was successful before proceeding to the next phase.

---

## Phase 6: Playwright setup

### Overview

Run `/10x-e2e-setup` to install Playwright and its config, then add a CI job. The board is driven
against mocked API responses, so no backend or OAuth is needed.

### Changes Required:

#### 1. Playwright via /10x-e2e-setup

**File**: `frontend/playwright.config.ts`, `frontend/e2e/`, `frontend/package.json`, `context/foundation/test-stack.md`

**Intent**: A repeatable browser harness for the screenshot gate.

**Contract**:
- `@playwright/test` is a dev dependency, with an `npm run e2e` script.
- `webServer` serves the production build.
- Fixtures under `frontend/e2e/fixtures/` hold `MatchView` JSON for each state (shaped by the
  Phase 1 types). A helper `mockMatch(page, fixture)` routes `/api/me` (signed in) and
  `/api/matches/**`. The seed test opens `/match` with a mid-game fixture and asserts both zones and
  the aside render.
- Projects: chromium at 1440×900 and 1280×720.

#### 2. CI job

**File**: `.github/workflows/ci.yml`

**Intent**: Run the e2e suite on PRs and `main`, next to the existing job.

**Contract**:
- A separate `e2e` job: install, `npx playwright install --with-deps chromium`, build, `npm run e2e`.
- The HTML report is uploaded as an artifact on failure.
- Step names in Polish (`AGENTS.md`).

### Success Criteria:

#### Automated Verification:

- Seed test passes locally: `cd frontend && npm run e2e`
- Unit tests still pass: `cd frontend && npx ng test --watch=false`
- CI e2e job is green on the PR

**Implementation Note**: After completing this phase and all automated verification passes, pause here for manual confirmation from the human that the manual testing was successful before proceeding to the next phase.

---

## Phase 7: Visual gate

### Overview

Screenshot tests of the key states at both viewports. They are the 10x-ui visual gate, compared once
against the canvas by a human, then pinned as baselines.

### Changes Required:

#### 1. Screenshot specs

**File**: `frontend/e2e/match-board.spec.ts`, `frontend/e2e/fixtures/*.json`

**Intent**: One `toHaveScreenshot` per state × viewport, driven by fixtures and clicks.

**Contract**:
- States: default mid-game, attacker selected (targets highlighted, lifted card in the centre), event targeting, hover preview,
  focus-visible on a card, urgent timer, opponent's turn (replay), log overlay, graveyard overlay,
  game over (win), loading, and load error.
- Timers are frozen with Playwright's clock API so the timer and replay are deterministic.
- Baselines are committed under `frontend/e2e/__screenshots__/` and generated on Linux chromium, the
  same environment as CI, with a small `maxDiffPixelRatio` for font anti-aliasing.

### Success Criteria:

#### Automated Verification:

- Screenshot suite passes against committed baselines: `cd frontend && npm run e2e`
- CI e2e job is green on the PR

#### Manual Verification:

- Baselines at 1440×900 compared side by side with `Board.dc.html` / `Card.dc.html`: layout, colours, type and radii match
- Baselines at 1280×720 show no clipping
- Mobile cell recorded as N/A (`context/foundation/prd.md:135`) in the PR description

**Implementation Note**: After completing this phase and all automated verification passes, pause here for manual confirmation from the human that the manual testing was successful before proceeding to the next phase.

---

## Testing Strategy

### Unit Tests:

- Backend: new view fields and graveyard contents (`MatchViewTests`), including a neutral card's faction.
- Card: variants, markers, states, `data-faction`.
- Match table: click semantics per selection state, disabled non-legal cards, `aria-pressed`, accessible
  names, preview, overlays with focus return, loading/error/empty, header hidden on `/match`.
- Existing behaviour (replay, timer, auto end-turn, 404 redirect) stays covered by the kept spec.

### Integration Tests:

- Playwright with mocked API: seed plus 12 visual states × 2 viewports.

### Manual Testing Steps:

1. Play a full match against the computer with the mouse only, then with the keyboard only.
2. Open the log and both graveyards mid-match, and close them with Escape.
3. Let the timer run under 10 s and watch the urgent look and auto end-turn.
4. Finish a match and check the game-over overlay plus "Zagraj ponownie".
5. Resize to 1280×720 and to a smaller window, and check that nothing clips (scroll below 1280×720).

## Performance Considerations

Component CSS budget (4 kB / 8 kB) is the only hard limit, handled by splitting styles into Card,
overlay and aside components. Google Fonts adds one external stylesheet with `display=swap`. Hover
preview updates a single signal.

## Migration Notes

The API change is additive (new fields), so an older SPA build keeps working. Not applicable beyond that.

## References

- Research: `context/changes/prod-ui/research.md`
- Design values: `context/changes/prod-ui/design-reference.md`
- Design canvas: https://claude.ai/artifact/UDTNmTGxfcCzaZ4v1yyd5t (`Board.dc.html`, `Card.dc.html`, `Bg*.dc.html`)
- View records: `src/main/java/com/example/itcardbrawl/api/MatchView.java:143-187`
- Current view: `frontend/src/app/match-table/match-table.html:1-344`, `match-table.ts:154,191,226-228,293-304,359-371`
- Budget: `frontend/angular.json:41-43`; static permit list: `src/main/java/com/example/itcardbrawl/config/SecurityConfig.java:27`

## Progress

> Convention: `- [ ]` pending, `- [x]` done. Append ` — <commit sha>` when a step lands. Do not rename step titles. See `references/progress-format.md`.

### Phase 1: Backend data for the cards

#### Automated

- [x] 1.1 Backend tests pass: `./mvnw -B verify`
- [x] 1.2 Frontend tests pass: `cd frontend && npx ng test --watch=false`
- [x] 1.3 Production build passes: `cd frontend && npm run build`

#### Manual

- [x] 1.4 `GET /api/matches/current` shows cost/faction on board characters and events, and a graveyard list that grows when a character dies

### Phase 2: Tokens, fonts, backgrounds

#### Automated

- [ ] 2.1 Production build passes and emits the three backgrounds under `media/`
- [ ] 2.2 Frontend tests pass: `cd frontend && npx ng test --watch=false`

#### Manual

- [ ] 2.3 Lobby, new-match and sign-in look unchanged after the token block lands
- [ ] 2.4 The three exported backgrounds match their canvas artboards by eye

### Phase 3: Card component

#### Automated

- [ ] 3.1 Card spec covers variants, character vs event, markers, not-playable state and `data-faction`
- [ ] 3.2 Full frontend suite passes: `cd frontend && npx ng test --watch=false`
- [ ] 3.3 `card.css` contains no hex literal

### Phase 4: Board layout and click interactions

#### Automated

- [ ] 4.1 Frontend tests pass: `cd frontend && npx ng test --watch=false`
- [ ] 4.2 Production build passes without a component style budget warning
- [ ] 4.3 No hex literal in match-table CSS

#### Manual

- [ ] 4.4 At 1440×900 the board matches the canvas layout
- [ ] 4.5 At 1280×720 nothing is clipped and the hand stays reachable
- [ ] 4.6 A full match is playable with the mouse only
- [ ] 4.7 A full match is playable with the keyboard only, and screen-reader names include the card names
- [ ] 4.8 Lobby and new-match still show the header, and `/match` does not
- [ ] 4.9 While targeting, the selected card floats in the board centre click-through and the cursor is a viewfinder

### Phase 5: Overlays and states

#### Automated

- [ ] 5.1 Frontend tests pass: `cd frontend && npx ng test --watch=false`
- [ ] 5.2 Production build passes without a component style budget warning
- [ ] 5.3 No hex literal in component CSS of the board

#### Manual

- [ ] 5.4 Each state of the matrix plus urgent timer, opponent's turn, targeting and game over is visible and readable at 1440×900
- [ ] 5.5 Text contrast at least 4.5:1 for muted labels on panel/surface
- [ ] 5.6 Overlays open, close with Escape, and return focus to their button

### Phase 6: Playwright setup

#### Automated

- [ ] 6.1 Seed test passes locally: `cd frontend && npm run e2e`
- [ ] 6.2 Unit tests still pass: `cd frontend && npx ng test --watch=false`
- [ ] 6.3 CI e2e job is green on the PR

### Phase 7: Visual gate

#### Automated

- [ ] 7.1 Screenshot suite passes against committed baselines: `cd frontend && npm run e2e`
- [ ] 7.2 CI e2e job is green on the PR

#### Manual

- [ ] 7.3 Baselines at 1440×900 match `Board.dc.html` / `Card.dc.html`
- [ ] 7.4 Baselines at 1280×720 show no clipping
- [ ] 7.5 Mobile cell recorded as N/A in the PR description
