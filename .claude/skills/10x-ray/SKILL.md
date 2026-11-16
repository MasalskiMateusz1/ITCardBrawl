---
name: 10x-ray
description: >
  Learn how a skill works by watching it run. Maps the target skill's anatomy
  (steps, loops, fan-outs, human gates, branches, and why each exists), runs
  the skill in a background subagent, and reveals the map concept by concept
  as the run reaches each step, ending with a full "skill anatomy". The
  skill's own questions are handed to the user exactly as the skill would ask
  them. Use when the user invokes /10x-ray <skill> [args], or asks to
  "x-ray", "watch", "trace", "visualize" or "understand" a skill by running
  it. `--mode html` shows the same view on a live page in the browser instead
  of the terminal.
argument-hint: "[--mode terminal|html] <skill-name> [args for that skill]"
allowed-tools:
  - Bash
  - Read
  - Write
  - Agent
  - Monitor
  - SendMessage
  - TaskStop
  - AskUserQuestion
---
