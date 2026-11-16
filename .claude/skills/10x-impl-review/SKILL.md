---
name: 10x-impl-review
description: Review implementation against plan for drift, dangerous decisions, and pattern compliance
argument-hint: <plan-path> [phase N] | <saved-review-path>
allowed-tools:
  - Read
  - Glob
  - Grep
  - Bash
  - Agent
  - AskUserQuestion
  - TaskCreate
  - TaskUpdate
  - TaskList
  - TaskGet
---
