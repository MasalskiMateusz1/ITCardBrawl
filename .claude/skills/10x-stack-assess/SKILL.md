---
name: 10x-stack-assess
description: >
  Assess an existing project's stack for agent-friendliness against the 4
  quality gates (typed, convention-based, popular, well-documented); writes
  context/foundation/stack-assessment.md with per-component scores, gaps, and
  ready-to-paste CLAUDE.md/AGENTS.md entries. Trigger phrases: "assess my
  stack", "is my stack agent-friendly", "oceń mój stack", "stack assessment".
  Use AFTER /10x-prd (brownfield), BEFORE /10x-health-check.
argument-hint: "[path-to-prd]"
allowed-tools:
  - Read
  - Write
  - Bash
  - AskUserQuestion
  - TaskCreate
  - TaskUpdate
---
