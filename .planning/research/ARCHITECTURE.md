# Architecture Research: Airwallex FYI

## Components

1. Scheduler / runner starts a check on cron or `--run-once`.
2. Source discovery fetches the Airwallex blog sitemap and filters article URLs.
3. Article extractor fetches each new candidate and extracts metadata/body.
4. State store compares URLs, lastmod values, and content hashes.
5. Summarizer calls OpenAI for new posts only.
6. Notifier sends WhatsApp messages through Twilio or logs them in dry-run mode.
7. Admin endpoints expose health, recent posts, and manual run-once.

## Data Flow

Sitemap -> candidate URLs -> article extraction -> dedupe state -> OpenAI summary -> Twilio alert -> notification status update.

## Build Order

1. Spring Boot skeleton, configuration, database schema.
2. Source discovery, extraction, and dedupe.
3. OpenAI summary and Twilio notification.
4. Scheduling, admin controls, and deployment readiness.
