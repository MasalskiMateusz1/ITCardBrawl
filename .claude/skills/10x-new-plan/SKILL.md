---
name: 10x-new-plan
description: Start a new change and plan it in one run - creates context/changes/<change-id>/change.md the way /10x-new does, then continues straight into /10x-plan for that change. Use when the user wants to open a change and go directly to an implementation plan, or says "new change and plan it", "start and plan", "nowa zmiana i plan". Use /10x-new-research instead when the change needs codebase exploration first.
argument-hint: "<change-id-or-path> [freeform intent]"
allowed-tools:
  - Read
  - Glob
  - Grep
  - Write
  - Edit
  - Bash
  - Agent
  - Task
  - Skill
  - AskUserQuestion
  - TaskCreate
  - TaskUpdate
  - TaskList
  - TaskGet
---
