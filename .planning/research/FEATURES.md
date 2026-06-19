# Feature Research: Airwallex FYI

## Table Stakes

- Source discovery from a stable feed or sitemap.
- Duplicate suppression across runs.
- Article extraction with structured-data preference and HTML fallback.
- Summary generation with predictable shape.
- Notification delivery with dry-run mode.
- Persistent state so scheduled runs survive restarts.
- Basic operational visibility through logs and health endpoints.

## Differentiators For Later

- Importance scoring and topic filtering.
- Slack alongside WhatsApp.
- Daily/weekly digest mode.
- Multi-source monitoring including docs/status/regional pages.
- Question-answering over update history.
- Small dashboard for history and controls.

## Anti-Features For V1

- Browser automation for every fetch unless needed; the public pages currently expose structured payloads.
- Unofficial WhatsApp Web automation.
- A multi-agent architecture before basic monitoring works.
