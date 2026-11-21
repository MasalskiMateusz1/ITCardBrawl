<!-- IMPL-REVIEW-REPORT -->
# Implementation Review: Code comments in English

- **Plan**: context/changes/comments-in-english/plan.md
- **Scope**: Full plan
- **Reviewed phases**: 1, 2, 3
- **Date**: 2026-11-21
- **Verdict**: APPROVED
- **Findings**: 0 critical, 1 warning, 3 observations

## Verdicts

| Dimension | Verdict |
|-----------|---------|
| Plan Adherence | WARNING |
| Scope Discipline | PASS |
| Safety & Quality | PASS |
| Architecture | PASS |
| Pattern Consistency | PASS |
| Success Criteria | PASS |

The automated criteria were re-run over `1acc367^..b59c3ab` and all pass:
- comment-only diffs for Java and for the nine files;
- the leftover-Polish greps;
- both YAML files parse, and `bash -n deploy/deploy.sh` passes;
- `AGENTS.md` no longer contains the old rule;
- CI run 44949061008 (test, image, deploy) is green.

Behaviour neutrality is proven:
- Stripping comments gives identical code for all 56 Java files. The stripper understands string and text-block literals.
- Every changed non-Java line is a whole-line `#` comment.
- YAML trees are identical, except one bash comment inside the CI `run: |` block. Only the comment text changed; the commands are byte-identical.
- No secrets or values were touched.

Translation fidelity: no inverted logic, lost negation or wrong threshold direction. Terminology is consistent across files.

## Findings

### F1 — Polish comments left in files the plan's inventory missed

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Plan Adherence
- **Location**: src/test/resources/config/application.properties:1-2, .dockerignore:1,6,9,13, .gitignore:50,55
- **Detail**: The plan listed nine non-Java files, but three more config files still have Polish `#` comments, e.g. "Atrapowe rejestracje klientow OAuth2…", "Artefakty buildu i zaleznosci…" and "Skille 10xDevs (licencjonowane, nie publikowac)". The new `AGENTS.md:30` rule ("as in all code: … config, scripts") makes them non-compliant. This is a gap in the plan's inventory, not an implementation drift.
- **Fix**: Translate those `#` comments too (comment-only, same checks as Phase 3).
- **Decision**: FIXED (translated # comments in src/test/resources/config/application.properties, .dockerignore, .gitignore)

### F2 — Comment rule sits in the Java bullet and under-specifies what stays Polish

- **Severity**: 💬 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Pattern Consistency
- **Location**: AGENTS.md:30-31
- **Detail**: The rule covers all code but lives in the "Java:" bullet, and line 31 (TypeScript, "English comments") repeats it. "Validation and exception messages stay in Polish" doesn't mention other runtime Polish that deliberately stays: deploy.sh log messages, CI step names, compose error text and test `.as("…")` descriptions. An agent could read the rule as asking it to translate those.
- **Fix**: Make it a separate bullet ("Code comments and javadoc — Java, TypeScript, config, scripts — are in English; runtime text stays Polish: validation/exception messages, log and CI step messages, test descriptions"), and drop the duplicate from the Java and TS bullets.
- **Decision**: FIXED (separate "Code comments" bullet in AGENTS.md listing the runtime Polish that stays; Java and TS bullets no longer repeat it)

### F3 — A few translations dropped reference data or read ambiguously

- **Severity**: 💬 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Plan Adherence
- **Location**: game/PolishNumberForms.java:19, api/MatchLogFormatter.java:27-28, game/MoveReason.java:15, game/ComputerOpponent.java:193-194, test/.../api/MatchControllerTests.java:633
- **Detail**:
  - `PolishNumberForms` lost the actual word forms ("obrażenie / obrażenia / obrażeń"), which are reference data, not prose.
  - `MatchLogFormatter` lost the Polish labels "Ty" and "Przeciwnik".
  - "more than its own" (`MoveReason:15`, `ComputerOpponent:193`) can be read as the player's board, while the original means the computer's.
  - "The manual tester" is a card name, "Manual Tester" in the catalog.
- **Fix**: Restore the quoted Polish forms and labels (`"obrażenie / obrażenia / obrażeń"`, `"Ty" (pl) / "You" (en)`, `"Przeciwnik" (pl) / "Opponent" (en)`), write "more than the computer's own board", and capitalize "Manual Tester".
- **Decision**: FIXED (restored "obrażenie / obrażenia / obrażeń" and the "Ty"/"Przeciwnik" labels; "the computer's own board" in MoveReason and ComputerOpponent; "Manual Tester")

### F4 — Some one-line comments rewrapped to two lines

- **Severity**: 💬 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Pattern Consistency
- **Location**: game/MoveReason.java:4, api/MatchLogFormatter.java:77-79 (and 7 more, all ≤120 cols)
- **Detail**: The plan says to keep line breaks. Most of the rewraps are justified by longer English, but `MoveReason:4` and `MatchLogFormatter:77-79` split mid-sentence for no length reason. This is cosmetic only.
- **Fix**: Re-join those two comments onto one line each where they fit in 120 columns.
- **Decision**: FIXED (MatchLogFormatter comment back on one line; MoveReason keeps its sentence-boundary break because one line would exceed 120 columns)
