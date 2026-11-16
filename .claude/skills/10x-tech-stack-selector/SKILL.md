---
name: 10x-tech-stack-selector
description: >
  Pick a starter and a stack for a greenfield project after the PRD is written.
  Reads context/foundation/prd.md, reasons over a language-aware starter
  registry with four agent-friendly quality gates, and writes the
  context/foundation/tech-stack.md hand-off. Use when the user asks "what
  stack should I use", "pick a stack", "choose framework",
  "co wybrać do projektu". Use AFTER /10x-prd, BEFORE /10x-bootstrapper.
argument-hint: "[path-to-prd]"
allowed-tools:
  - Read
  - Write
  - Bash
  - AskUserQuestion
  - TaskCreate
  - TaskUpdate
---
