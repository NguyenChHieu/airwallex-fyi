# Research Summary: Airwallex FYI

## Stack

Kotlin Spring Boot is a good fit for a scheduled backend monitor and for the user's learning goal. Use Spring Data JDBC, PostgreSQL, Flyway, Spring Scheduling, and Jsoup fallback extraction.

## Table Stakes

The product needs source discovery, duplicate suppression, extraction, summaries, WhatsApp notifications, persistent state, dry-run mode, and basic operational visibility.

## Architecture

Build a linear monitor pipeline first: scheduler/run-once -> sitemap discovery -> article extraction -> dedupe store -> OpenAI summary -> Twilio WhatsApp -> notification status update.

## Watch Out For

Do not scrape blocked Airwallex app/API paths. Do not send historical alerts on first run. Do not overbuild an agent system before the monitor pipeline works. Do not rely on unofficial WhatsApp automation.
