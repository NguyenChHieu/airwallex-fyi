# Phase 3: Summaries And WhatsApp Alerts - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md - this log preserves the alternatives considered.

**Date:** 2026-06-21
**Phase:** 3-Summaries And WhatsApp Alerts
**Areas discussed:** AI provider and summary contract, summary eligibility, WhatsApp alert format, dry-run and Twilio behavior, processing state and run-result visibility

---

## AI Provider And Summary Contract

| Option | Description | Selected |
|--------|-------------|----------|
| Strict JSON only | If AI returns invalid or missing fields, record summary failure and do not send WhatsApp. | yes |
| Repair once, then fail | Try one automatic repair or retry, then block notification if still invalid. | |
| Best-effort alert | Send a fallback message using title/description if structured JSON fails. | |

**User's choice:** Strict JSON only.
**Notes:** The user also raised interest in a free AI option. Current official docs were checked during discussion, and the selected MVP provider is Gemini free tier first, behind a provider-backed summarizer interface.

---

## Summary Eligibility

| Option | Description | Selected |
|--------|-------------|----------|
| Newly discovered only | Auto-summarize `DISCOVERED` posts only. | |
| New + changed known posts | Summarize new posts and changed known URLs, but avoid duplicate alerts. | |
| Any post missing summary | Allow seeded/history posts to be summarized later when missing summaries. | |
| Custom rule | Auto-summarize new posts; surface missing-summary and content-changed known posts as approval-needed before AI calls. | yes |

**User's choice:** Custom rule.
**Notes:** New posts auto-summarize. Seeded/history posts missing summaries and content-changed known URLs must show status and ask for user permission before summary/re-summary. Changed known URLs must not create duplicate WhatsApp alerts.

---

## WhatsApp Alert Format

| Option | Description | Selected |
|--------|-------------|----------|
| Compact FYI | Headline, 2 short bullets, why it matters, tags, and direct link. | |
| Ultra-short | Headline, one-sentence summary, and direct link only. | |
| Detailed | Headline, 3-5 bullets, why it matters, tags, source type, and direct link. | yes |

**User's choice:** Detailed.
**Notes:** Detailed format should still stay concise; short bullets are preferred over a long article rewrite.

---

## Dry-Run Behavior

| Option | Description | Selected |
|--------|-------------|----------|
| Full pipeline, no Twilio call | Call Gemini, parse/save summary, build exact WhatsApp payload, log/return it, record dry-run notification status. | yes |
| No external calls at all | Skip Gemini and Twilio; only show which posts would have been processed. | |
| Gemini only | Call Gemini and save summary, but do not build notification attempts. | |

**User's choice:** Full pipeline, no Twilio call.
**Notes:** Dry-run must never call Twilio, but should still show exactly what would have been sent.

---

## Twilio Send Failure

| Option | Description | Selected |
|--------|-------------|----------|
| Record failed attempt, no auto-retry | Surface failure in run result/admin state; retry can wait for Phase 4 or later. | yes |
| Retry once immediately | One quick retry in the same run, then record failure. | |
| Treat whole run as failed | Mark the entire run failed if any send fails. | |

**User's choice:** Record failed attempt, no auto-retry.
**Notes:** Phase 3 should be honest about failures without pulling scheduling/retry machinery forward.

---

## Processing State

| Option | Description | Selected |
|--------|-------------|----------|
| Simple lifecycle statuses | Add coarse statuses such as `SUMMARY_READY`, `ALERT_SENT`, `DRY_RUN_READY`, `SUMMARY_FAILED`, `ALERT_FAILED`, and `APPROVAL_NEEDED`. | yes |
| Keep posts mostly unchanged | Store truth in summary/notification tables and leave posts as `DISCOVERED`/`SEEDED`. | |
| Very detailed post statuses | Separate every step, such as `SUMMARIZING`, `FORMATTING_ALERT`, and `SENDING_ALERT`. | |

**User's choice:** Simple lifecycle statuses.
**Notes:** Status should be useful for admin inspection without becoming a workflow engine.

---

## Run Result Visibility

| Option | Description | Selected |
|--------|-------------|----------|
| Counts + samples | Existing discovery counts plus summary/alert counts and sample URLs/payload previews/errors. | yes |
| Everything | Include every summary, every alert payload, and every error in the response. | |
| Counts only | Keep response small and rely on logs/database for details. | |

**User's choice:** Counts + samples.
**Notes:** Keep the existing bounded response style from Phase 2.

---

## the agent's Discretion

- The planner may choose whether the approval-needed flow is visibility-only in Phase 3 or includes a very small protected admin action, as long as no unapproved AI call happens for seeded/history or content-changed known posts.
- The planner may choose exact class/repository names and package boundaries that fit the current Kotlin Spring Boot style.

## Deferred Ideas

- Full approval dashboard.
- Automatic Twilio retry scheduling.
- Slack, digest mode, production WhatsApp sender/template approval, and historical Q&A.
