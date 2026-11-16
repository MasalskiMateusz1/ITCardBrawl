<!-- IMPL-REVIEW-REPORT -->
# Implementation Review: Szkielet SPA ↔ API (spa-api-walking-skeleton)

- **Plan**: context/changes/spa-api-walking-skeleton/plan.md
- **Scope**: Full plan
- **Reviewed phases**: 1, 2
- **Date**: 2026-11-16
- **Verdict**: APPROVED
- **Findings**: 0 critical, 0 warnings, 4 observations

## Verdicts

| Dimension | Verdict |
|-----------|---------|
| Plan Adherence | PASS |
| Scope Discipline | PASS |
| Safety & Quality | PASS |
| Architecture | PASS |
| Pattern Consistency | PASS |
| Success Criteria | PASS |

Automated criteria re-run during review: `./mvnw test` (exit 0), `npx ng test --watch=false` (2/2), `npm run build` (exit 0), `git ls-files frontend | grep node_modules` (0 tracked). Manual rows 1.4 and 2.5–2.8 confirmed by the user in-session.

## Findings

### F1 — Default-deny chain will also block SPA static assets once served by Spring

- **Severity**: 💡 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Architecture
- **Location**: src/main/java/com/example/itcardbrawl/config/SecurityConfig.java:19
- **Detail**: `anyRequest().authenticated()` covers every path, not only `/api/**`. Correct for F-01 (the SPA is served by `ng serve`), but when F-02 packages the SPA into the jar, `/`, `/index.html` and the JS/CSS bundles will get 401. The plan explicitly defers packaging to F-02, so this is a hand-off note, not drift.
- **Fix**: Add a note to the F-02 plan to permit static SPA resources (e.g. `/`, `/index.html`, `/*.js`, `/*.css`, `/favicon.ico`) in `SecurityConfig`.
- **Decision**: FIXED — hand-off note added to F-02 in context/foundation/roadmap.md

### F2 — index.html says lang="en" and title "Itcardbrawl"

- **Severity**: 💡 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Pattern Consistency
- **Location**: frontend/src/index.html:2
- **Detail**: Generated defaults: UI text is Polish and the brand is spelled "ItCardBrawl" in the header, but the document declares `lang="en"` and the tab title is "Itcardbrawl".
- **Fix**: Set `<html lang="pl">` and `<title>ItCardBrawl</title>`.
- **Decision**: FIXED

### F3 — Generated frontend/.vscode config is silently git-ignored

- **Severity**: 💡 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Scope Discipline
- **Location**: .gitignore:36
- **Detail**: Root `.gitignore` ignores `.vscode/` as a directory, so the un-ignore rules in `frontend/.gitignore:24-29` cannot take effect; `frontend/.vscode/{extensions,launch,tasks}.json` from `ng new` exist on disk but are not committed. Consistent with the repo's existing policy, just not visible to anyone reading `frontend/.gitignore`.
- **Fix**: Accept as-is (repo policy: no editor config committed); optionally delete the dead `!.vscode/...` lines from `frontend/.gitignore`.
- **Decision**: FIXED — dead !.vscode rules removed from frontend/.gitignore (root .vscode/ policy kept)

### F4 — Success state ignores the response body

- **Severity**: 💡 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Plan Adherence
- **Location**: frontend/src/app/app.ts:19
- **Detail**: Any 2xx JSON response sets the state to `'ok'`, regardless of `status`. Fine for a walking skeleton (a non-JSON 200, e.g. index.html from a misrouted proxy, already fails JSON parsing and lands in `'error'`), but the displayed "API: ok" is not derived from the API's own answer.
- **Fix**: Leave as-is for F-01; revisit if `/api/ping` ever reports a non-ok status.
- **Decision**: ACCEPTED — left as-is for F-01; revisit if /api/ping can report a non-ok status
