---
name: 10x-prd
description: >
  Generate context/foundation/prd.md from shape-notes.md (or raw notes) against
  the locked PRD schema. Auto-routes to greenfield (10 sections) or brownfield
  (11 sections) template based on context_type in shape-notes.md or cwd
  auto-detection. Use when the user has shaping notes ready and wants a
  schema-conformant PRD written to disk. Trigger phrases: "write the PRD",
  "generate PRD", "create the PRD from notes", "stwórz PRD", "turn notes into a
  PRD", "PRD from shape-notes". Use AFTER /10x-shape, not in place of it.
argument-hint: "[path-to-notes-file]"
allowed-tools:
  - Read
  - Write
  - Bash
  - AskUserQuestion
  - TaskCreate
  - TaskUpdate
---
