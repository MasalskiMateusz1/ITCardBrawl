---
name: 10x-goal-implement
description: >
  Autonomously implement technical plans from
  context/changes/<change-id>/plan.md under Claude Code's
  /goal — no human interaction at any point. Sibling of /10x-implement for
  unattended runs, in an interactive /goal session or headless via claude -p.
  Delegates each phase's code changes to a subagent, flips the
  plan's Automated Progress rows, verifies each phase through an automatic
  quality-gate stack (plan success criteria, deliberate-break check, full suite),
  commits each phase on green with Conventional Commits, and
  surfaces pending Manual rows as a closing human checklist. Use when the user
  wants autonomous or unattended plan execution, pairs /goal with a plan, asks to
  "run the plan under /goal", or needs headless implementation.
argument-hint: <change-id> [phase N]
allowed-tools:
  - Read
  - Glob
  - Grep
  - Write
  - Edit
  - Bash
  - Agent
  - Task
  - TaskCreate
  - TaskUpdate
  - TaskList
  - TaskGet
---
