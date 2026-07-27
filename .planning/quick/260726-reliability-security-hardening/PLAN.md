---
status: complete
lane: CONTROLLED
date: 2026-07-26
---

# Reliability and Security Hardening

Strengthen the existing Telegram and deployment paths without changing the product's core behavior.

## Scope

1. Persist claimed Telegram update IDs so duplicate webhook deliveries are idempotent across app instances and valid out-of-order updates are still processed.
2. Keep the polling cursor compatible with webhook processing and release a claim when command processing aborts unexpectedly.
3. Add a short bounded backoff between transient Gemini retries.
4. Run the production container as a non-root user and add weekly Gradle/GitHub Actions dependency monitoring.

## Verification

- Add regression coverage for duplicate claims and out-of-order Telegram updates.
- Run focused Telegram, persistence, and Gemini tests.
- Run the complete Gradle test suite.
- Build the application jar and Docker image when Docker is available.
- Run Ponytail whole-repo audit and inspect the final diff.

## Constraints

- Preserve the user's existing README.md change.
- Do not change Telegram commands, summary content, daily schedule, or open-versus-private allowlist semantics.
- Do not enable the Render scheduler.
- Do not commit or push without a separate user request.
