# Airwallex FYI

Daily digest bot for public Airwallex Blog and Newsroom updates.

It discovers new posts, stores canonical state in one database, summarizes new updates with Gemini, and sends concise daily digests to subscribers.

## Current Shape

- Kotlin Spring Boot
- PostgreSQL/Supabase in production, H2 for local tests
- Telegram delivery first
- Twilio WhatsApp optional
- GitHub Actions daily run, no always-on server required

## Docs

- [Setup](docs/SETUP.md)
- Main command: `./gradlew.bat bootRun --args="--run-once"`
- Tests: `./gradlew.bat test`
