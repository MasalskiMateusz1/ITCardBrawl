<!-- IMPL-REVIEW-REPORT -->
# Implementation Review: Production UI for the match table (/match)

- **Plan**: context/changes/prod-ui/plan.md
- **Scope**: Full plan
- **Reviewed phases**: 1, 2, 3, 4, 5, 6, 7, 8
- **Date**: 2026-11-23
- **Verdict**: NEEDS ATTENTION
- **Findings**: 0 critical, 3 warnings, 6 observations

## Verdicts

| Dimension | Verdict |
|-----------|---------|
| Plan Adherence | WARNING |
| Scope Discipline | PASS |
| Safety & Quality | WARNING |
| Architecture | WARNING |
| Pattern Consistency | WARNING |
| Success Criteria | PASS |

Success criteria run during the review: `./mvnw -B verify` green; `ng test` 185/185; `npm run build` clean (no budget warning, backgrounds + 31 card images under `media/`); no hex literals in component CSS; `npm run e2e:linux` 32/32; CI `test` + `e2e` green on PR #3. All manual Progress rows are ticked and backed by the diff. Every deviation from the plan is recorded in `change.md` Notes and was treated as approved.

## Findings

### F1 — e2e job does not gate image build and deploy

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: .github/workflows/ci.yml:117
- **Detail**: `image` has `needs: test` only, and `deploy` needs `image`. A push to `main` whose Playwright suite is red is still built and deployed automatically. The plan only asked for a separate e2e job, so this is a gap rather than a plan violation.
- **Fix**: Change `image` to `needs: [test, e2e]`.
- **Decision**: FIXED

### F2 — New child components skip the .ts/.html/.css/.spec.ts split

- **Severity**: ⚠️ WARNING
- **Impact**: 🔎 MEDIUM — real tradeoff; pause to reason through it
- **Dimension**: Pattern Consistency
- **Location**: frontend/src/app/match-table/* (aim-arrow, attack-fx, board-slot, drag-ghost, drop-zone, hand-slot, hover-card, lifted-card), frontend/src/app/ui/card-back
- **Detail**: AGENTS.md requires standalone components split into `name.ts/.html/.css/.spec.ts`. Nine new components use an inline `template:` and 14 component folders have no spec (including status-panel, graveyard-list, turn-timer, board-skeleton, side-cards). They are covered indirectly by the 3078-line `match-table.spec.ts`. These components came from the approved Phase 4 deviations, so this is a convention gap, not plan drift.
- **Fix A ⭐ Recommended**: Split the logic-bearing components (status-panel, graveyard-list, turn-timer, hover-card) and give each a small spec; record in AGENTS.md that tiny presentational components may keep an inline template.
  - Strength: Restores the convention where it matters and makes the exception explicit for the next agent.
  - Tradeoff: A follow-up change of moderate size (4 specs, a few file splits).
  - Confidence: HIGH — the sibling screens (lobby/, new-match/, sign-in/) show the target shape.
  - Blind spot: Some behaviour may be easier to test through match-table than in isolation.
- **Fix B**: Split and spec every listed component.
  - Strength: Full compliance with the rule as written.
  - Tradeoff: Lots of boilerplate for 10-line presentational components; duplicates coverage that match-table.spec.ts already has.
  - Confidence: MED — value per spec is low for the smallest components.
  - Blind spot: None significant.
- **Decision**: FIXED (Fix A)

### F3 — Polish javadoc on GraveyardCardView

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Pattern Consistency
- **Location**: src/main/java/com/example/itcardbrawl/api/MatchView.java:243
- **Detail**: Two stacked javadoc blocks: an English one-liner followed by a Polish block ("Karta na cmentarzu z treścią z katalogu…"). Only the last block attaches to the record, so the effective doc is Polish, against the English-comments rule.
- **Fix**: Merge both into one English javadoc (graveyard card with catalog content; attack/health/ability for characters, duration/effects for events, others null).
- **Decision**: FIXED

### F4 — Phase 8 rewrote 18 existing baselines that the plan promised to keep

- **Severity**: 💡 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Plan Adherence
- **Location**: frontend/e2e/__screenshots__/match-board.spec.ts/*-linux.png
- **Detail**: Phase 8 §3 says existing states keep their baselines. Commit 16cb487 changed 18 of them because the user-approved rename of `tst-automation` to "Tester automatyczny" changes the name on the board and in the log. The commit body says so; change.md mentions the rename but not the baseline refresh.
- **Fix**: Add one line to the Phase 8 deviation note in change.md: the rename refreshed 18 placeholder-state baselines.
- **Decision**: FIXED

### F5 — Google Fonts link: screenshot stability and privacy

- **Severity**: 💡 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: frontend/src/index.html:11
- **Detail**: Fonts load from fonts.googleapis.com at runtime. The pixel baselines (threshold 0.05, maxDiffPixelRatio 0.001) therefore depend on network access and on the font files Google serves, and every visitor's IP goes to Google (a known GDPR concern for an EU audience). The plan chose this deliberately ("Self-hosted fonts … for now").
- **Fix**: Open a follow-up change to self-host the woff2 files under frontend/src/assets-media/ with @font-face in styles.css.
- **Decision**: FIXED (queued in follow-ups/review-fixes.md)

### F6 — Unthrottled pointermove work during drag and aim

- **Severity**: 💡 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: frontend/src/app/match-table/match-table.ts:861-882, :1003-1016
- **Detail**: Each pointermove runs elementFromPoint/closest/querySelector/getBoundingClientRect, sets signals and re-arms a tilt setTimeout. On 120–240 Hz pointers this forces layout reads and change detection several times per frame.
- **Fix**: Coalesce into one requestAnimationFrame per frame (keep the last event, process in the RAF callback, cancel in endPress).
- **Decision**: FIXED (queueMove/flushMove/cancelMove; specs await one frame after pointer moves)

### F7 — match-table.ts has grown to ~1,200 lines

- **Severity**: 💡 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Architecture
- **Location**: frontend/src/app/match-table/match-table.ts
- **Detail**: Drag, aim, attack animation, replay and timer logic all live in the table component (~59 kB). It holds no game rules (SPA stays thin), but it is the obvious hotspot for the next change.
- **Fix**: Track a follow-up to extract drag/aim and attack-fx into services or directives.
- **Decision**: FIXED (queued in follow-ups/review-fixes.md)

### F8 — Playwright version can drift from the pinned Linux image

- **Severity**: 💡 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: frontend/package.json:30, .github/workflows/ci.yml:77, frontend/e2e/linux.mjs
- **Detail**: `@playwright/test` is `^1.65.1` while CI and e2e:linux run the `v1.65.1-noble` image; a lockfile refresh can move past the image version and make the baselines diverge.
- **Fix**: Pin `@playwright/test` to exactly `1.65.1`.
- **Decision**: FIXED

### F9 — e2e static server dies on a malformed URL

- **Severity**: 💡 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: frontend/e2e/serve.mjs:36
- **Detail**: `decodeURIComponent` throws URIError on input like `/%E0`, uncaught inside the request handler, which kills the local test server. Test-only code.
- **Fix**: Wrap the decode in try/catch and answer 400.
- **Decision**: FIXED
