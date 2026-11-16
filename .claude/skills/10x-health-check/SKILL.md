---
name: 10x-health-check
description: >
  Health-check an existing project: dependency audit, security scan, test
  runner detection, CI/CD and missing-config analysis. Writes
  context/foundation/health-check.md with prioritized fixes and an
  agent-readiness verdict. Trigger phrases: "health check", "audit my project",
  "is my project healthy", "sprawdź projekt", "audyt projektu". Use AFTER
  /10x-stack-assess (brownfield chain), BEFORE agent onboarding.
argument-hint: "[path-to-stack-assessment]"
allowed-tools:
  - Read
  - Write
  - Bash
  - AskUserQuestion
  - TaskCreate
  - TaskUpdate
---
