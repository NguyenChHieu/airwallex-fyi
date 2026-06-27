# Airwallex FYI

Airwallex FYI is a Kotlin Spring Boot service that watches public Airwallex Blog and Newsroom updates, stores canonical posts and summaries once, and sends subscriber-facing WhatsApp or Telegram daily digests from that central state.

Current scope supports Twilio WhatsApp Sandbox and free Telegram bot delivery. Slack, chatbot interactions, Supabase/admin subscriber UI, production WhatsApp templates, and multi-instance scheduler locking are deferred.

## Requirements

- Java 21
- Gradle wrapper from this repo

## Local Setup

Use `.env.example` as the environment-variable reference, then put your local secrets in `.env`. The app imports `.env` automatically when it exists, and `.env` is ignored by Git.

Example `.env` entries for the free Telegram path:

```properties
TELEGRAM_BOT_TOKEN=123456:your_bot_token
TELEGRAM_CHAT_ID=123456789
WHATSAPP_TO=
DRY_RUN=false
SCHEDULER_ENABLED=false
```

Example `.env` entries for a Twilio Sandbox smoke test:

```properties
TWILIO_ACCOUNT_SID=ACxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx
TWILIO_AUTH_TOKEN=your_twilio_auth_token
TWILIO_WHATSAPP_FROM=whatsapp:+14155238886
WHATSAPP_TO=whatsapp:+61YOURNUMBER
DRY_RUN=false
SCHEDULER_ENABLED=false
```

Safe local defaults:

- `DRY_RUN=true`
- `SCHEDULER_ENABLED=false`
- `ADMIN_TOKEN=dev-admin-token`
- H2 in-memory database unless `DATABASE_URL` points somewhere else

## Persistent Database

Local runs default to an in-memory H2 database, so processed posts disappear when the process exits. For a real daily bot, use a persistent Postgres-compatible database from a cloud provider such as Supabase, Neon, Railway, Render, Fly.io, or a managed Postgres host.

The app already includes the PostgreSQL driver and Flyway PostgreSQL support. Switching from local H2 to cloud Postgres should only require environment variables:

```properties
DATABASE_URL=jdbc:postgresql://HOST:5432/postgres?sslmode=require
DATABASE_USERNAME=postgres
DATABASE_PASSWORD=your_database_password
DATABASE_DRIVER=org.postgresql.Driver
```

Keep the `jdbc:postgresql://...` prefix. Some providers show a `postgres://...` URL; convert that to the JDBC form before putting it in `.env`.

Flyway migrations run on startup, so the same schema used locally will be created in the cloud database.

## Tests

Run the full suite:

```powershell
./gradlew.bat test
```

Run the deterministic smoke test:

```powershell
./gradlew.bat test --tests "*RunOnceSmokeTest"
```

The smoke test uses fixture Airwallex sitemap/article HTML, fake AI summaries, and a dry-run notifier. It does not call live Airwallex, Gemini, Twilio, or Telegram.

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

Scheduled polling is opt-in. For local testing with a long-running process:

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

## GitHub Actions Daily Job

For the cheapest MVP deployment, use the included GitHub Actions workflow instead of a long-running server. It wakes up once per day, runs the monitor with `--run-once`, sends any Telegram digest, then exits.

The workflow lives at `.github/workflows/daily-airwallex-fyi.yml` and also supports manual runs from the GitHub Actions tab. Its cron is `0 22 * * *` UTC, which is about 8am in Sydney during AEST and 9am during AEDT.

Add these repository secrets in GitHub under `Settings -> Secrets and variables -> Actions -> New repository secret`:

```text
DATABASE_URL
DATABASE_USERNAME
DATABASE_PASSWORD
GEMINI_API_KEY
TELEGRAM_BOT_TOKEN
TELEGRAM_CHAT_ID
```

Use the same Supabase database values from your local `.env`. `DATABASE_URL` should be the JDBC URL, for example:

```text
jdbc:postgresql://HOST:5432/postgres?sslmode=require
```

After pushing the workflow, test it manually:

1. Open the GitHub repo.
2. Go to `Actions`.
3. Select `Daily Airwallex FYI`.
4. Click `Run workflow`.
5. Check the logs and Telegram message.

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
AI_MODEL=gemini-2.5-flash
GEMINI_API_KEY=
```

Keep `GEMINI_API_KEY` blank for local tests. Set it only in your local shell, IDE, or host secrets.

## Telegram Setup

Telegram is the simplest free delivery path for this project:

1. In Telegram, message `@BotFather`.
2. Send `/newbot`, follow the prompts, and copy the bot token.
3. Open your new bot chat and send `/start`.
4. In a browser, open `https://api.telegram.org/bot<TELEGRAM_BOT_TOKEN>/getUpdates`.
5. Copy `message.chat.id` from the JSON response.
6. Put these values in `.env`:

```text
TELEGRAM_BOT_TOKEN=
TELEGRAM_CHAT_ID=
WHATSAPP_TO=
DRY_RUN=false
```

Leave `WHATSAPP_TO` blank if you only want Telegram. If both `WHATSAPP_TO` and `TELEGRAM_CHAT_ID` are set, the app seeds both subscriber channels and sends the daily digest to both.

## Twilio Sandbox Setup

For optional WhatsApp Sandbox delivery, set:

```text
TWILIO_ACCOUNT_SID=
TWILIO_AUTH_TOKEN=
TWILIO_WHATSAPP_FROM=
WHATSAPP_TO=
```

Twilio WhatsApp values should use `whatsapp:+...` format. Keep `DRY_RUN=true` until you intentionally want Twilio calls; dry-run mode records the payload preview and never calls Twilio.

## Deployment Notes

For the MVP, prefer GitHub Actions as a scheduled job so there is no always-on server. Keep `SCHEDULER_ENABLED=false` in scheduled jobs because GitHub Actions should run once and exit.

If you later deploy as a long-running Spring Boot service, supply environment variables through the host. Keep `SCHEDULER_ENABLED=false` until the service is configured and verified with `--run-once` or the fixture-backed smoke test. When ready for unattended polling, enable one scheduler instance with a fixed delay such as `86400000` milliseconds for daily checks.
