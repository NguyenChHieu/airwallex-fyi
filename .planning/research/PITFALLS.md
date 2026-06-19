# Pitfalls Research: Airwallex FYI

## Pitfalls

- Scraping brittle CSS selectors instead of structured data or sitemap entries.
  - Prevention: prefer sitemap URLs and `__NEXT_DATA__`; keep Jsoup fallback generic.

- Sending historical spam on the first run.
  - Prevention: implement explicit seed mode and first-run behavior before notification code.

- Mixing scheduled service deployment with GitHub Actions-only assumptions.
  - Prevention: support long-running scheduler and `--run-once`; decide deployment later.

- Committing secrets.
  - Prevention: use env vars and provide `.env.example` only when implementing code.

- Summarizer producing inconsistent alert shape.
  - Prevention: use structured OpenAI output and tests around message formatting.

- WhatsApp production assumptions.
  - Prevention: v1 uses Twilio Sandbox; document production sender/template work separately.
