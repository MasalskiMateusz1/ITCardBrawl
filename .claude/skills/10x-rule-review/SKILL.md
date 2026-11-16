---
name: 10x-rule-review
description: >
  Review the condition of an "AI rules" file (CLAUDE.md, AGENTS.md,
  .cursor/rules/*.mdc, copilot-instructions.md, .windsurfrules, or similar)
  and produce a 5-point scorecard with concrete fixes, regardless of which
  tool the rules target. Use when the user asks to "review AI rules",
  "audit AGENTS.md", "check my CLAUDE.md", "score my agent instructions".
allowed-tools:
  - Read
  - Glob
  - Grep
  - Edit
  - AskUserQuestion
---
