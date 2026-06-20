# Phase 2: Airwallex Source Monitoring - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md - this log preserves the alternatives considered.

**Date:** 2026-06-20
**Phase:** 2-Airwallex Source Monitoring
**Areas discussed:** URL Discovery And Filtering, Article Extraction Rules, First-Run Seed And Dedupe, Run Result Visibility

---

## URL Discovery And Filtering

| Decision Point | Options Considered | Selected |
|----------------|--------------------|----------|
| Sitemap URL filtering | Strict global only; global plus nearby; record rejected candidates | Strict global only |
| Source type classification | URL path; page metadata; hybrid | Hybrid |
| Sitemap fetch failure | Fail clearly; use previous posts/current DB state; retry | Use current DB state and clearly report failure |

**User's choice:** Strict global-only filtering; hybrid source type; keep service usable on sitemap failure while telling the user something went wrong.
**Notes:** Live sitemap verification later found pagination URLs such as `/global/newsroom/page/2`, reinforcing the exclusion requirement.

---

## Article Extraction Rules

| Decision Point | Options Considered | Selected |
|----------------|--------------------|----------|
| Minimum usable article | Title + body; title only; all metadata | Title + meaningful body |
| Structured vs fallback source | Structured wins; HTML body wins; compare and flag | Structured data wins |
| Hash inputs | Body only; title + description + body; all fields | Title + description + body |
| Missing date | Store null date; use sitemap lastmod; skip article | Store `published_at = null` |
| Body flattening | Normalized plain text; Markdown-ish; text plus raw JSON | Normalized plain text |
| Fallback success visibility | No special status; visible fallback note/status; skip fallback posts | Make fallback visible |
| Fixture coverage | Blog + Newsroom fixtures; minimal JSON snippets; live tests only | Blog + Newsroom fixtures |

**User's choice:** Structured extraction first, but only after live verification. Store normalized plain text for summaries; images are not required for Phase 2 persistence.
**Notes:** User challenged the `__NEXT_DATA__` assumption. Live checks on 2026-06-20 verified Blog article data under `pageData.post.fields` and Newsroom article data under `pageData.pr.fields`, both with structured `content` objects. User explicitly asked that we do not assume parser behavior and must double-check.

---

## First-Run Seed And Dedupe

| Decision Point | Options Considered | Selected |
|----------------|--------------------|----------|
| First-run seed scope | Seed all silently; seed recent only; preview first run | Seed recent only |
| Default seed size | 25; 20; 30; other | 25 |
| New-post definition | URL not in DB; URL not in DB or hash changed; URL + lastmod changed | URL not in DB |
| Existing URL hash change | Update quietly; ignore; flag for review | Update quietly and report count |
| Per-article extraction failure | Continue; fail whole run; write placeholder | Continue and report failed URL/reason |

**User's choice:** Seed a practical recent baseline of 25 posts, then treat only never-seen URLs as new. Existing URL edits update stored content but do not become new notifications.
**Notes:** User asked whether the app would check daily after first run. Clarified that daily scheduling is intended overall but belongs to Phase 4, while Phase 2 remains manual through `/admin/run-once`.

---

## Run Result Visibility

| Decision Point | Options Considered | Selected |
|----------------|--------------------|----------|
| Run-once response shape | Operational counts + samples; detailed per-URL report; minimal status | Operational counts + small samples |
| Partial failures | Explicit partial status; HTTP 200 with embedded errors; HTTP error on any failure | Explicit partial status |
| Recent posts endpoint | Keep simple with extraction fields; add filters; leave unchanged | Add simple filters |
| Body in admin responses | Short preview only; full body optional; no body | Short preview only |
| Detailed errors | Logs plus sampled response errors; persist failures in DB; include all on request | Logs plus sampled response errors |
| Phase 2 dry-run | No dry-run; preview query param; global dry-run prevents writes | No Phase 2 dry-run |

**User's choice:** Make the run result useful but not huge: counts and samples in response, full details in logs. Add simple recent-post filters and show body previews only.
**Notes:** Phase 2 has no external notification side effects, so database writes are intentional and should not be disabled by dry-run.

---

## the agent's Discretion

- Planner may choose exact package/class names and internal DTOs.
- Planner may choose the cleanest low-scope representation for fallback visibility.

## Deferred Ideas

- OpenAI summaries and WhatsApp notifications stay in Phase 3.
- Daily scheduling and CLI `--run-once` stay in Phase 4.
- Rich media/image persistence, raw payload storage, and dashboard UI stay future scope unless later evidence changes the need.
