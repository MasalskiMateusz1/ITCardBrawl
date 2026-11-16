---
name: 10x-repo-map
description: >
  Build a Project Map (context/map/repo-map.md) of an unfamiliar or legacy
  repository in one run, organised around business capabilities — checkout,
  billing, sign-in, search, notifications, whatever the product actually
  does — and ranked by criticality × buzz: where change, fixes, reverts and
  discussion concentrate. A capability inventory from entry points, data
  model and commit vocabulary feeds four parallel evidence sub-agents
  (history, discussion, structure, people); the main agent synthesizes one
  decision-ready report with risk zones, a first-day reading list and
  explicit unknowns. Tech-stack agnostic; works on git alone, richer with the
  GitHub CLI. Use when the user wants to get up to speed in, map or
  understand a large or inherited codebase, asks "where do I start in this
  repo", "what are the hot areas here", "zbuduj mapę projektu", "wide scan",
  "repo map", or wants to refresh an existing context/map/repo-map.md.
argument-hint: "[--root <path>] [--since <git-date>] [--lang <code>] [--replace]"
allowed-tools:
  - Read
  - Glob
  - Grep
  - Bash
  - Write
  - Agent
  - Task
  - AskUserQuestion
---
