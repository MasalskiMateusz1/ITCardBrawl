---
name: 10x-e2e
description: Drive an approved plan's browser-level (E2E) phases against the running app, one risk at a time — plan → generate → review → verify. The E2E sibling of /10x-implement and /10x-tdd, sharing the same plan and Progress. Only drives risks that genuinely need a browser and whose feature is already built; redirects the rest to /10x-tdd or /10x-implement. Use when the user says "e2e", "write/generate a Playwright test", "browser test this risk", or "drive the plan's E2E phases".
argument-hint: "<change-id> [phase N] | @context/changes/<change-id>/plan.md | <risk-id>"
---
