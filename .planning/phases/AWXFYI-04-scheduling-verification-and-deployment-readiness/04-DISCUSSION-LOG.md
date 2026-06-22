# Phase 4: Scheduling, Verification, And Deployment Readiness - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md - this log preserves the alternatives considered.

**Date:** 2026-06-22
**Phase:** 4-Scheduling, Verification, And Deployment Readiness
**Areas discussed:** Run Mechanics, Scheduler Strategy, Verification, Docs And Deployment

---

## Run Mechanics

| Option | Description | Selected |
|--------|-------------|----------|
| Spring Boot one-shot runner | App starts with `--run-once`, calls existing `MonitorRunService.runOnce()`, logs bounded result, then exits. No admin token or self-HTTP needed. | yes |
| Call admin endpoint | Reuse `/admin/run-once` over HTTP from the command path, but would add token/port/self-call awkwardness. | |
| External script or Gradle task | Keep app code smaller by wrapping the admin endpoint or bootRun from a script, but less clean for deployment. | |
| Agent decides | User delegates the choice. | |

**User's choice:** User delegated: "i think u can decide the rest, just tell me what were the options and what did u pick so i can change if i want to".
**Notes:** Picked the one-shot runner because it reuses the central service path without extra HTTP/auth complexity and gives a clean deployment smoke command.

---

## Scheduler Strategy

| Option | Description | Selected |
|--------|-------------|----------|
| In-app Spring scheduler | Use Spring scheduling behind `SCHEDULER_ENABLED`, delegate to `MonitorRunService.runOnce()`, default disabled. | yes |
| External cron calling `--run-once` | Avoids always-on scheduler concerns and is good for some hosts, but shifts ops outside the app. | |
| Manual/admin only | Smallest code change, but would not satisfy unattended polling. | |

**User's choice:** Agent selected.
**Notes:** Existing config already has scheduler enabled/fixed-delay fields, so the simplest Phase 4 path is to make that config real. Multi-instance locking is deferred.

---

## Scheduler Interval Shape

| Option | Description | Selected |
|--------|-------------|----------|
| Fixed delay | Reuse existing `fixed-delay-ms` setting; document daily deployment value and allow shorter local verification intervals. | yes |
| Cron/time-of-day | More expressive for daily digest timing, but adds more config and edge cases now. | |
| Frequent polling only | Keep the existing short polling default, relying on digest dedupe to avoid duplicate messages, but it is noisier operationally. | |

**User's choice:** Agent selected.
**Notes:** Fixed delay is already in the code. The user can later ask for exact time-of-day scheduling if that becomes important.

---

## CLI Result Signaling

| Option | Description | Selected |
|--------|-------------|----------|
| Status-based exit | `completed` exits 0; `partial_failure`/`failed` exit non-zero so scripts notice problems. | yes |
| Always exit 0 with logs | Friendly for local manual use, but weak for smoke tests and deployment automation. | |
| Throw on any non-completed result | Simple but can hide the bounded result if not handled carefully. | |

**User's choice:** Agent selected.
**Notes:** Status-based exit keeps the monitor result visible while still making automation useful.

---

## Verification

| Option | Description | Selected |
|--------|-------------|----------|
| Fixture-backed smoke | Use fixture or mocked source/AI/notifier data; no live Airwallex, Gemini, or Twilio dependency in automated verification. | yes |
| Live sitemap dry-run | Closer to reality but network-dependent and not stable enough as the required smoke. | |
| Admin endpoint only | Very small but does not prove scheduler/CLI behavior. | |

**User's choice:** Agent selected.
**Notes:** Existing tests already cover much of QUAL-01. Phase 4 should add only the missing ops coverage and a deterministic smoke path.

---

## Docs And Deployment

| Option | Description | Selected |
|--------|-------------|----------|
| Local-first generic docs | Add `README.md` and `.env.example` with setup, env vars, Twilio Sandbox/Gemini setup, run-once, scheduled run, and smoke commands. | yes |
| Docker-first | Useful later, but adds packaging work before the app has final operational docs. | |
| Platform-specific host guide | Useful once a target host is chosen, but premature right now. | |

**User's choice:** Agent selected.
**Notes:** This keeps the phase useful for learning and local operation without committing to a hosting platform.

---

## the agent's Discretion

- User delegated the remaining Phase 4 choices to the agent.
- Agent chose conservative defaults: shared monitor path, Spring one-shot runner, in-app scheduler behind config, fixture-backed smoke verification, and local-first docs.
- Exact Kotlin/Spring class names, runner API, scheduler annotation style, and test class names are left to the planner/executor.

## Deferred Ideas

- Distributed scheduler lock or leader election for multi-instance deployments.
- Cron/time-of-day scheduling.
- Docker/platform-specific deployment guide.
- Supabase subscriber management UI.
- Slack/chatbot delivery surfaces.
- Live end-to-end smoke test with real external services.
