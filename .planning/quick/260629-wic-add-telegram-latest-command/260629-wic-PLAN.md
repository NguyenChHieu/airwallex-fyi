---
quick_id: "260629-wic"
slug: "add-telegram-latest-command"
description: "add telegram /latest command"
status: complete
created_at: "2026-06-29T13:24:24Z"
---

# Quick Plan: Telegram /latest Command

## Goal

Let a Telegram user send `/latest` and receive the most recent summarized Airwallex updates already stored in the database.

## Tasks

1. Add a small latest-updates formatter/service that reads summary-ready posts and summaries from the database.
2. Route `/latest` through the existing Telegram command handling path.
3. Add tests covering `/latest` replies and constructor integration.
4. Document the new Telegram command.

## Verification

- Focused Telegram/monitor tests.
- Full Gradle test suite.
