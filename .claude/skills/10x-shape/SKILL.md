---
name: 10x-shape
description: >
  Facilitate a structured discovery conversation that turns an idea —
  greenfield or brownfield, auto-detected from cwd — into shape-notes.md,
  the input to /10x-prd. Trigger phrases: "new project", "from scratch",
  "od pomysłu", "shape an idea", "I have an idea", "greenfield",
  "brownfield", "istniejący projekt", "zmiana w projekcie".
  Use BEFORE /10x-prd, not in place of it.
argument-hint: "[freeform idea]"
allowed-tools:
  - Read
  - Write
  - Bash
  - AskUserQuestion
  - TaskCreate
  - TaskUpdate
  - Skill
---
