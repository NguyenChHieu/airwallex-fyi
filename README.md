# Airwallex FYI

<p align="center">
  <img src="docs/assets/airwallex-fyi.png" alt="Airwallex FYI bot icon" width="160" />
</p>

![Daily Airwallex FYI](https://img.shields.io/github/actions/workflow/status/NguyenChHieu/airwallex-fyi/daily-airwallex-fyi.yml?branch=main&label=daily%20digest)
![Kotlin](https://img.shields.io/badge/Kotlin-2.3-7F52FF?logo=kotlin&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.1-6DB33F?logo=springboot&logoColor=white)
![Java](https://img.shields.io/badge/Java-21-ED8B00?logo=openjdk&logoColor=white)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL%20%2F%20Supabase-production-3FCF8E?logo=supabase&logoColor=white)
![Telegram](https://img.shields.io/badge/Telegram-bot-26A5E4?logo=telegram&logoColor=white)

Production-style digest bot for public Airwallex Blog and Newsroom updates. It discovers new posts, stores canonical state in PostgreSQL, summarizes updates with Gemini, and sends concise Telegram digests to subscribers.

## What It Does

- Monitors public Airwallex Blog and Newsroom sitemap entries.
- Extracts article content and detects new or changed updates.
- Generates structured summaries with Gemini.
- Sends daily Telegram digests with source links.
- Supports `/start`, `/stop`, `/latest`, and `/status`.
- Keeps optional Twilio WhatsApp delivery available.

## Using the Bot

Users interact with Airwallex FYI through Telegram after the bot webhook is connected: [@AirwallexFYIBot](https://t.me/AirwallexFYIBot).

Anyone can use the bot once it is reachable on Telegram unless `TELEGRAM_ALLOWED_CHAT_IDS` is configured. Each allowed chat that sends `/start` is stored as a subscriber, and future digests are fanned out from the central database.

| Command | Purpose |
| --- | --- |
| `/start` | Subscribe this chat to daily Airwallex FYI digests. |
| `/stop` | Unsubscribe this chat. |
| `/latest` | Show the latest summarized updates already stored in the database. |
| `/status` | Check subscription state, latest digest, latest update seen, and runtime mode. |

Daily digests are sent automatically when the scheduled worker finds new public Airwallex updates.

## Telegram Profile

| Field | Value |
| --- | --- |
| Name | `airwallex-fyi` |
| Username | [`@AirwallexFYIBot`](https://t.me/AirwallexFYIBot) |
| About | `Daily AI summaries for public Airwallex Blog and Newsroom updates.` |
| Description | `Airwallex FYI monitors public Airwallex Blog and Newsroom updates, summarizes new posts, and sends concise daily Telegram digests with source links.` |
| Avatar | [`docs/assets/airwallex-fyi.png`](docs/assets/airwallex-fyi.png) |

BotFather owns the public Telegram profile and command menu. Runtime setup, webhook configuration, and secrets stay in [Setup and deployment](docs/SETUP.md).

## Production Snapshot

| Area | Current |
| --- | --- |
| Production Kotlin files | 58 |
| Test classes | 29 |
| JUnit tests | 127 |
| Flyway migrations | 6 |
| Source/resource size | ~7.7k nonblank lines |
| Runtime stack | Render + Supabase + GitHub Actions + Telegram |

## Architecture

```text
Airwallex sitemap/pages
        |
        v
Spring Boot monitor
        |
        v
Supabase / PostgreSQL
        |
        v
Gemini summaries
        |
        v
Telegram daily digest
```

Render hosts the webhook/admin service. GitHub Actions runs the scheduled `--run-once` worker around 9am Australia/Sydney.

## Commands

```powershell
./gradlew.bat test
./gradlew.bat bootRun --args="--run-once"
```

## Docs

- [Setup and deployment](docs/SETUP.md)
- [Daily workflow](.github/workflows/daily-airwallex-fyi.yml)
