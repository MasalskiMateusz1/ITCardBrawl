---
name: 10x-domain-distillation
description: >
  Distil the business domain of an existing codebase into one map
  (context/domain/domain-distillation.md): the ubiquitous language with
  sources in the docs and in the code, subdomains classified as Core,
  Supporting or Generic, aggregate candidates and policies with their
  invariants and whether the code enforces them, the model-vs-code drift list,
  a ranking of what to fix first with a "done when" per item, owner-routed
  questions, and a compact glossary for agents (context/domain/glossary.md).
  Findings carry stable IDs and are diffed against the previous run.
  Tech-stack agnostic; works with or without requirement documents. Use when
  the user wants to discover or name the domain of a grown
  codebase, build a ubiquitous language or glossary from the repo, check
  whether the code matches the product documents, find the model-vs-code
  drift, classify subdomains, or asks for "domain distillation", "destylacja
  domeny", "słownik domeny", "DDD map of this repo".
argument-hint: "[--root <path>] [--docs <path>...] [--lang <code>] [--replace]"
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
