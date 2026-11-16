---
name: 10x-new-research
description: Start a new change and research it in one run - creates context/changes/<change-id>/change.md the way /10x-new does, then continues straight into /10x-research for that change. Use when the user wants to open a change and explore the codebase before planning, or says "new change and research it", "start and research", "nowa zmiana i research". Use /10x-new-plan instead when the change can go directly to a plan.
argument-hint: "<change-id-or-path> [freeform intent or research question]"
allowed-tools:
  - Read
  - Glob
  - Grep
  - Write
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
