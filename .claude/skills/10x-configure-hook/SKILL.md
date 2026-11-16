---
name: 10x-configure-hook
description: >
  Turn the quality gates from context/foundation/test-plan.md into working
  agent hooks and prove them before handing off. Detects the harness from the
  repo (Claude Code, Cursor, Codex, GitHub Copilot), audits existing hook
  config, maps each gate to a moment (per edit, end of turn, commit, push,
  CI), generates config and scripts in that harness's signal protocol, and
  runs every script against a deliberately broken file. Trigger phrases:
  "configure hooks", "set up agent hooks", "lint after every edit", "hook
  fires but the agent ignores it", "skonfiguruj hooki", "hook po edycji",
  "agent nie widzi błędów z hooka". Use AFTER /10x-test-plan (M3L1). Does
  not write git pre-commit/pre-push config — it only recommends it.
---
