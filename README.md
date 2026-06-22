# Airwallex FYI

Airwallex FYI is a Kotlin Spring Boot service that watches public Airwallex Blog and Newsroom updates, stores canonical posts and summaries once, and sends subscriber-facing WhatsApp daily digests from that central state.

Current scope is WhatsApp-first. Slack, chatbot interactions, Supabase/admin subscriber UI, production WhatsApp templates, and multi-instance scheduler locking are deferred.

## Requirements

- Java 21
- Gradle wrapper from this repo

## Local Setup

Use `.env.example` as the environment-variable reference. Spring Boot does not automatically load `.env` files by itself, so set the variables in your shell, IDE run config, or process manager.

Safe local defaults:

- `DRY_RUN=true`
- `SCHEDULER_ENABLED=false`
- `ADMIN_TOKEN=dev-admin-token`
- H2 in-memory database unless `DATABASE_URL` points somewhere else

## Tests

Run the full suite:

```powershell
./gradlew.bat test
```

Run the deterministic smoke test:

```powershell
./gradlew.bat test --tests "*RunOnceSmokeTest"
```

The smoke test uses fixture Airwallex sitemap/article HTML, fake AI summaries, and a dry-run notifier. It does not call live Airwallex, Gemini, or Twilio.

## Run One Check

Run one monitor check and exit:

```powershell
./gradlew.bat bootRun --args="--run-once"
```

This calls `MonitorRunService.runOnce()` directly through the command-line runner. It does not call `/admin/run-once` over HTTP. Exit code is `0` only when the monitor result status is `completed`; `partial_failure` and `failed` exit non-zero.

## Start Normally

Start the app without running a one-shot command:

```powershell
./gradlew.bat bootRun
```

With the default config, the scheduler is disabled, so normal startup exposes the app/admin endpoints without polling.

## Scheduled Polling

Scheduled polling is opt-in. For a daily fixed-delay interval:

```powershell
$env:SCHEDULER_ENABLED="true"
$env:SCHEDULER_FIXED_DELAY="86400000"
./gradlew.bat bootRun
```

Equivalent env values:

```text
SCHEDULER_ENABLED=true
SCHEDULER_FIXED_DELAY=86400000
```

The MVP assumes one running scheduler instance. If later deployment runs multiple app instances, add a DB/distributed lock before enabling more than one scheduler.

## Admin Endpoints

Admin routes require the `X-Admin-Token` header matching `ADMIN_TOKEN`.

- `GET /admin/health`
- `POST /admin/run-once`
- `GET /admin/posts/recent`

Example:

```powershell
curl.exe -H "X-Admin-Token: dev-admin-token" http://localhost:8080/admin/health
```

## Gemini Setup

For real summaries, set:

```text
AI_PROVIDER=gemini
AI_MODEL=gemini-3.5-flash
GEMINI_API_KEY=
```

Keep `GEMINI_API_KEY` blank for local tests. Set it only in your local shell, IDE, or host secrets.

## Twilio Sandbox Setup

For WhatsApp Sandbox delivery, set:

```text
TWILIO_ACCOUNT_SID=
TWILIO_AUTH_TOKEN=
TWILIO_WHATSAPP_FROM=
WHATSAPP_TO=
```

Twilio WhatsApp values should use `whatsapp:+...` format. Keep `DRY_RUN=true` until you intentionally want Twilio calls; dry-run mode records the payload preview and never calls Twilio.

## Deployment Notes

Deploy as a long-running Spring Boot service with environment variables supplied by the host. Keep `SCHEDULER_ENABLED=false` until the service is configured and verified with `--run-once` or the fixture-backed smoke test. When ready for unattended polling, enable one scheduler instance with a fixed delay such as `86400000` milliseconds for daily checks.

This phase does not require Docker, Railway, Render, Fly.io, Supabase, or GitHub Actions. Those can be added later after choosing a target deployment path.
