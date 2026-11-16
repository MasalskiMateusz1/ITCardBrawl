---
name: 10x-e2e-setup
description: One-time, re-runnable Playwright E2E setup for an existing web app. Detects the stack, port, package manager, auth and any existing Playwright files, then fills only the gaps - installs @playwright/test, creates or extends playwright.config.ts (webServer on build + preview, a setup project with storageState, traces and screenshots on failure), writes and greens one seed test for a browser-level risk, wires playwright-cli into your agent, and records everything in context/foundation/test-stack.md. Hands off to /10x-e2e. Use when the user says "set up Playwright", "e2e setup", "prepare E2E tests", "no playwright config", or when /10x-e2e redirects here.
---
