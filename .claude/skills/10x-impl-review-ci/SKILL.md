---
name: 10x-impl-review-ci
description: >
  Run implementation review non-interactively in CI against a PR: discovers
  the plan, checks drift/safety/patterns/test coverage, writes
  context/changes/<change-id>/reviews/impl-review.md, commits it to the PR
  branch, and posts a summary comment. Use whenever the request mentions CI,
  GitHub Actions, GHA, Claude Code Action, automated PR review, or "review
  this PR in CI".
argument-hint: (none — discovers plan and PR automatically from the CI environment)
allowed-tools:
  - Read
  - Write
  - Glob
  - Grep
  - Bash
  - Agent
  - TaskCreate
  - TaskUpdate
  - TaskList
  - TaskGet
---
