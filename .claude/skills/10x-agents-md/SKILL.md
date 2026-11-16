---
name: 10x-agents-md
description: >
  Generate an AGENTS.md onboarding document for AI coding agents working in
  this repository. Inspects the repo (package manifest, README, scripts,
  lint/test config, layout, commit history) and writes a concise contributor
  guide titled "Repository Guidelines". Use when the user invokes
  /10x-agents-md, asks to "create AGENTS.md", "write an agent onboarding
  doc", "generate contributor guide for agents", or similar. The output is
  optimized to be small, precise, reference-heavy, and ordered with critical
  rules at the top — so a future agent reads it once and stays unblocked.
allowed-tools:
  - Read
  - Glob
  - Grep
  - Bash
  - Write
  - Edit
  - AskUserQuestion
---
