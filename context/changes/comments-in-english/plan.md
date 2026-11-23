# Code comments in English Implementation Plan

## Overview

Translate every Polish code comment in the repository to English: Java comments and javadoc in `src/main` and `src/test`, plus `#` comments in config, Docker, CI and deploy files. Then change the `AGENTS.md` rule so new code keeps comments in English. The change is behaviour-neutral: no code, string literal or game content changes.

## Current State Analysis

- `AGENTS.md:30` requires Polish: "Java: tabs. Comments, javadoc and validation messages are in Polish." TypeScript is already English (`AGENTS.md:31`), and the frontend has no Polish comments.
- Java comment lines (lines that start with `//`, `*` or `/**`):
  - `game/`: about 294 lines in 37 files;
  - `api/`: 25 lines in 5 files;
  - `config/`: 3 lines in 2 files;
  - `web/`: 11 lines in 1 file;
  - `src/test/java`: 83 lines in 20 files.
  - Two more Java comments trail code on the same line (`code; // …`).
  - `ItcardbrawlApplication.java` has none.
- Polish `#` comments outside Java:
  - `src/main/resources/application.properties` and `application-prod.properties`;
  - `Dockerfile` and `.github/workflows/ci.yml`;
  - `deploy/compose.yaml`, `deploy/Caddyfile`, `deploy/deploy.sh` and `deploy/logrotate-docker`;
  - `.env.properties.example`.
  - Many are written without diacritics (e.g. "naglowkow", "zob."), so a check based on diacritics alone misses them.
- Polish that stays, because it is runtime text, not a comment:
  - exception and validation messages (15 in `src/main`, e.g. `MatchService.java:70`);
  - test `.as("…")` descriptions (e.g. `BackendRoutesTests.java:34`, `ComputerOpponentTests` "ziarno %d");
  - expected Polish strings in tests;
  - pl/en content in `src/main/resources/game/factions.json`.

## Desired End State

- No Polish remains in any code comment in `src/**/*.java` or in the `#` comments of the nine non-Java files listed above.
- `AGENTS.md` says code comments and javadoc are in English, and that validation and exception messages stay in Polish.
- The diff touches only comment lines (plus `AGENTS.md`); `./mvnw -B verify` passes unchanged.

### Key Discoveries:

- `AGENTS.md:30` is the only place that mandates Polish comments; `AGENTS.md:43` (Polish commit titles) is a separate rule and stays.
- Polish without diacritics is common in the older files (`deploy/compose.yaml:1-17`, `application-prod.properties:1-4`), so the leftover check also greps common Polish function words.
- Javadoc carries meaning that later phases and reviewers rely on, e.g. the fairness note in `MatchView.java:28-32` and `PositionEvaluator`'s weight docs. Translate faithfully, do not summarize.

## What We're NOT Doing

- Not translating exception or validation messages, test `.as(…)` descriptions, or any string literal.
- Not touching `factions.json`, i18n files, or the pl/en log sentences in `MatchLogFormatter`.
- Not translating prose docs (`deploy/README.md`, `frontend/README.md`, `context/**`), and not changing archived changes.
- Not changing the Polish commit-title rule (`AGENTS.md:43`).
- Not adding an automated guard against new Polish comments. The documented rule plus review is the chosen mechanism.
- Not rewording or "improving" comments beyond translation, and not adding or removing comments.

## Implementation Approach

Translate faithfully, one area per phase, so each commit is reviewable on its own. Keep the existing comment structure, line breaks and javadoc tags (`{@link}`, `{@code}`, `@param`). Keep the Java formatting rules: tabs, and the existing wrap width of the surrounding code. Each phase proves that it is comment-only with a diff filter, and that no Polish is left with a leftover grep (diacritics plus common Polish words).

## Critical Implementation Details

- **Comment-only diff check.** Run this against the phase's working tree before staging. It must print nothing, except for the two known trailing comments where only the `// …` part changed:
  ```bash
  git diff -U0 -- <phase paths> | grep -E '^[+-]' | grep -vE '^(\+\+\+|---)' \
    | grep -vE '^[+-][[:space:]]*(//|/\*|\*|\*/|#|$)'
  ```
- **Leftover Polish check.** Two greps over comment lines in the phase's paths must both print nothing:
  - diacritics: `[ąćęłńóśźżĄĆĘŁŃÓŚŹŻ]`;
  - diacritic-free Polish words: `\b(oraz|jest|nie|dla|przez|albo|tylko|gdy|jako|tez|sie|zob|ktory|ktora|ktore|bez|lub|jesli|zeby|czy)\b` (case-insensitive).

## Phase 1: Domain comments (`game/`)

### Overview

Translate all comments and javadoc in the domain package, the largest and most cross-referenced area.

### Changes Required:

#### 1. Domain package

**File**: `src/main/java/com/example/itcardbrawl/game/**/*.java` (37 files)

**Intent**: Replace each Polish comment and javadoc with a faithful English translation, keeping every javadoc tag and reference.

**Contract**: Only comment lines change. Identifiers, string literals (including Polish exception messages) and code are byte-identical.

### Success Criteria:

#### Automated Verification:

- Comment-only diff check prints nothing for `src/main/java/com/example/itcardbrawl/game`
- Leftover Polish check prints nothing for comment lines in `src/main/java/com/example/itcardbrawl/game`
- Full backend suite passes: `./mvnw -B verify`

#### Manual Verification:

- Spot-read 3 translated files (`PositionEvaluator`, `ComputerOpponent`, `MatchEngine`): the javadoc is accurate and reads naturally in English

**Implementation Note**: After completing this phase and all automated verification passes, pause here for manual confirmation from the human that the manual testing was successful before proceeding to the next phase.

---

## Phase 2: Remaining Java (`api`, `config`, `web`, `src/test`)

### Overview

Translate the rest of the Java comments: the API, config and web packages and all tests.

### Changes Required:

#### 1. API, config and web packages

**File**: `src/main/java/com/example/itcardbrawl/{api,config,web}/**/*.java`

**Intent**: Translate comments and javadoc, including the fairness note on `MatchView` and the security notes in `config/` and `web/`.

**Contract**: Only comment lines change. The pl/en sentence templates in `MatchLogFormatter` are string literals and stay as they are.

#### 2. Tests

**File**: `src/test/java/**/*.java` (20 files)

**Intent**: Translate `//` comments and javadoc in tests.

**Contract**: Only comment lines change. `.as("…")` descriptions, expected strings and test names stay as they are.

### Success Criteria:

#### Automated Verification:

- Comment-only diff check prints nothing for `src/main/java/com/example/itcardbrawl/{api,config,web}` and `src/test/java`
- Leftover Polish check prints nothing for comment lines in all of `src/**/*.java`
- Full backend suite passes: `./mvnw -B verify`

#### Manual Verification:

- Spot-read `MatchView`, `SecurityConfig` and one test file: the translations are accurate, and the security and fairness notes keep their meaning

**Implementation Note**: After completing this phase and all automated verification passes, pause here for manual confirmation from the human that the manual testing was successful before proceeding to the next phase.

---

## Phase 3: Config, deploy and rule

### Overview

Translate the `#` comments in the nine non-Java files and change the documented rule.

### Changes Required:

#### 1. Config, Docker, CI and deploy comments

**File**: `src/main/resources/application.properties`, `src/main/resources/application-prod.properties`, `Dockerfile`, `.github/workflows/ci.yml`, `deploy/compose.yaml`, `deploy/Caddyfile`, `deploy/deploy.sh`, `deploy/logrotate-docker`, `.env.properties.example`

**Intent**: Translate each Polish `#` comment faithfully, and keep its references (e.g. "see deploy/README.md, \"Aktualizacja Caddy\"": the section title stays Polish because the README is not translated).

**Contract**: Only `#` comment lines (or the comment part of a trailing comment) change. Keys, values, YAML structure, shell commands and the Caddy directives are byte-identical.

#### 2. Coding-style rule

**File**: `AGENTS.md`

**Intent**: Replace the Polish-comments rule so agents and contributors write new comments in English.

**Contract**: `AGENTS.md:30` becomes, in substance: "Java: tabs. Comments and javadoc are in English (as in all code: TypeScript, config, scripts); validation and exception messages stay in Polish." The rest of `AGENTS.md`, including the Polish commit-title rule, stays as it is.

### Success Criteria:

#### Automated Verification:

- Comment-only diff check prints nothing for the nine files
- Leftover Polish check prints nothing for `#` comment lines in the nine files
- YAML still parses: `python3 -c "import yaml,sys; [yaml.safe_load(open(f)) for f in sys.argv[1:]]" .github/workflows/ci.yml deploy/compose.yaml`
- Shell script still parses: `bash -n deploy/deploy.sh`
- `AGENTS.md` no longer mandates Polish comments: `grep -n "Comments, javadoc and validation messages are in Polish" AGENTS.md` prints nothing
- Full backend suite passes: `./mvnw -B verify`

#### Manual Verification:

- After the merge to `main`, the CI run and the automatic deploy succeed (comment-only change to `ci.yml`, `Dockerfile` and `deploy/`)

**Implementation Note**: After completing this phase and all automated verification passes, pause here for manual confirmation from the human that the manual testing was successful before proceeding to the next phase.

---

## Testing Strategy

### Unit Tests:

- None added. The existing suite (`./mvnw -B verify`) proves the change is behaviour-neutral.

### Integration Tests:

- CI and the deploy pipeline run on the merge to `main` and confirm the comment-only edits to `ci.yml`, `Dockerfile` and `deploy/`.

### Manual Testing Steps:

1. Spot-read the files named in each phase's manual check and compare them with the Polish originals in `git diff`.
2. Confirm that no string literal, test description or game text changed in the diff.

## Performance Considerations

None. Only comments change.

## Migration Notes

None. Existing archived plans keep their Polish quotes of old comments. `git blame` shifts to these commits on comment lines only.

## References

- Rule to change: `AGENTS.md:30`
- Example Polish comments without diacritics: `deploy/compose.yaml:1-17`, `src/main/resources/application-prod.properties:1-4`
- Strings that must stay Polish: `src/main/java/com/example/itcardbrawl/game/MatchService.java:70`, `src/test/java/com/example/itcardbrawl/web/BackendRoutesTests.java:34`

## Progress

> Convention: `- [ ]` pending, `- [x]` done. Append ` — <commit sha>` when a step lands. Do not rename step titles. See `references/progress-format.md`.

### Phase 1: Domain comments (`game/`)

#### Automated

- [x] 1.1 Comment-only diff check prints nothing for `src/main/java/com/example/itcardbrawl/game` — 99f595c
- [x] 1.2 Leftover Polish check prints nothing for comment lines in `src/main/java/com/example/itcardbrawl/game` — 99f595c
- [x] 1.3 Full backend suite passes: `./mvnw -B verify` — 99f595c

#### Manual

- [x] 1.4 Spot-read 3 translated files (`PositionEvaluator`, `ComputerOpponent`, `MatchEngine`): the javadoc is accurate and reads naturally in English — 99f595c

### Phase 2: Remaining Java (`api`, `config`, `web`, `src/test`)

#### Automated

- [x] 2.1 Comment-only diff check prints nothing for `src/main/java/com/example/itcardbrawl/{api,config,web}` and `src/test/java` — e5a102b
- [x] 2.2 Leftover Polish check prints nothing for comment lines in all of `src/**/*.java` — e5a102b
- [x] 2.3 Full backend suite passes: `./mvnw -B verify` — e5a102b

#### Manual

- [x] 2.4 Spot-read `MatchView`, `SecurityConfig` and one test file: the translations are accurate, and the security and fairness notes keep their meaning — e5a102b

### Phase 3: Config, deploy and rule

#### Automated

- [x] 3.1 Comment-only diff check prints nothing for the nine files — 62951c2
- [x] 3.2 Leftover Polish check prints nothing for `#` comment lines in the nine files — 62951c2
- [x] 3.3 YAML still parses: `python3 -c "import yaml,sys; [yaml.safe_load(open(f)) for f in sys.argv[1:]]" .github/workflows/ci.yml deploy/compose.yaml` — 62951c2
- [x] 3.4 Shell script still parses: `bash -n deploy/deploy.sh` — 62951c2
- [x] 3.5 `AGENTS.md` no longer mandates Polish comments: `grep -n "Comments, javadoc and validation messages are in Polish" AGENTS.md` prints nothing — 62951c2
- [x] 3.6 Full backend suite passes: `./mvnw -B verify` — 62951c2

#### Manual

- [x] 3.7 After the merge to `main`, the CI run and the automatic deploy succeed (comment-only change to `ci.yml`, `Dockerfile` and `deploy/`) — 62951c2
