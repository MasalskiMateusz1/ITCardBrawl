# Code comments in English — Plan Brief

> Full plan: `context/changes/comments-in-english/plan.md`

## What & Why

Code comments in the backend and in config and deploy files are written in Polish, while the frontend is already English. This change translates every Polish code comment to English and changes the documented rule so new comments stay English. Runtime texts the player or tests depend on stay Polish.

## Starting Point

`AGENTS.md:30` requires Polish comments, javadoc and validation messages in Java. About 416 Java comment lines (66 files) and the `#` comments in 9 config, Docker, CI and deploy files are Polish, often without diacritics.

## Desired End State

Every code comment and javadoc is in English, and `AGENTS.md` tells contributors and agents to keep it that way. Exception and validation messages, test descriptions and the pl/en game content are unchanged. All tests pass as before.

## Key Decisions Made

| Decision | Choice | Why (1 sentence) |
| -------- | ------ | ---------------- |
| What to translate | Comments and javadoc only, plus the rule | Pure documentation change with zero behaviour risk; runtime texts stay Polish |
| Non-Java comments | Included (properties, Docker, CI, deploy, env example) | One consistent rule with no carve-outs to remember |
| Regression guard | `AGENTS.md` rule only, no automated check | Many Polish comments have no diacritics and Polish strings must stay, so a guard would be noisy |
| Commit-title language | Unchanged (Polish, `AGENTS.md:43`) | A separate convention, outside this change |

## Scope

**In scope:**
- All comments and javadoc in `src/main/java` and `src/test/java`
- `#` comments in `application*.properties`, `Dockerfile`, `.github/workflows/ci.yml`, `deploy/{compose.yaml,Caddyfile,deploy.sh,logrotate-docker}` and `.env.properties.example`
- Rewording the comment rule in `AGENTS.md:30`

**Out of scope:**
- Exception and validation messages, test `.as(…)` descriptions and any string literal
- `factions.json`, i18n files and the pl/en log sentences
- Prose docs (`deploy/README.md`, `frontend/README.md`, `context/**`) and archived changes
- Any automated guard, rewording, or adding or removing comments

## Architecture / Approach

Translate faithfully, one area per phase: keep the comment structure, line breaks, javadoc tags and Java formatting (tabs). Each phase proves it is comment-only with a `git diff` filter (every changed line is a comment line). A leftover grep covers diacritics plus common Polish words, since many comments lack diacritics. `./mvnw -B verify` confirms nothing else moved.

## Phases at a Glance

| Phase | What it delivers | Key risk |
| ----- | ---------------- | -------- |
| 1. Domain comments (`game/`) | English javadoc and comments in 37 domain files (~294 lines) | Losing nuance in load-bearing docs (fairness, evaluator weights) |
| 2. Remaining Java (`api`, `config`, `web`, `src/test`) | English comments in the other ~122 Java lines | Accidentally editing a test description or expected string |
| 3. Config, deploy and rule | English `#` comments in 9 files and the new `AGENTS.md` rule | A comment edit that breaks YAML, shell or Caddy syntax |

**Prerequisites:** none; `main` is clean apart from the user's pending `roadmap.md` edits.
**Estimated effort:** ~1 session across 3 small phases.

## Open Risks & Assumptions

- The leftover-word grep is a heuristic; a rare Polish comment without the listed words could slip through, which the manual spot-reads cover.
- Two Java comments trail code on the same line; the comment-only filter flags them, and they must be checked by eye.
- The `Dockerfile`, `ci.yml` and `deploy/` edits reach production only through the normal merge-to-`main` pipeline; CI and deploy success is the final check.

## Success Criteria (Summary)

- A reader sees English comments everywhere in the code, with nothing lost in translation.
- `git diff` for the whole change touches only comment lines and `AGENTS.md`.
- `./mvnw -B verify`, CI and the deploy pass unchanged.
