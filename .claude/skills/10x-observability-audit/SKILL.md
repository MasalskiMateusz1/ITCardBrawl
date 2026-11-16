---
name: 10x-observability-audit
description: >
  Audit an existing codebase for code logic that hides production errors:
  swallowed exceptions, errors turned into redirects or 200s, lost stack
  traces and causes, code paths running outside the error tracker, logs that
  never become alerts. Picks the critical user flows from
  context/foundation (shape-notes, PRD, roadmap) or asks for 2-3 areas,
  fans out full-stack subagent audits, optionally proves gaps at runtime
  with a local fake error-tracker endpoint, and writes a new dated report
  under context/audits/observability/ on every run. Use whenever the user
  asks why production errors are missing or hard to debug, wants an
  observability / monitoring / error-tracking / logging audit, asks "what
  does Sentry (or Datadog, Rollbar, CloudWatch…) miss", "audyt
  observability", "czego nie widzimy w monitoringu", or wants to re-run a
  previous observability audit. Tech-stack agnostic.
argument-hint: "[area ...] [--runtime] [--verify <report-path>] [--out <dir-or-file>]"
allowed-tools:
  - Read
  - Glob
  - Grep
  - Bash
  - Task
  - Write
  - AskUserQuestion
  - TaskCreate
  - TaskUpdate
  - TaskList
---
