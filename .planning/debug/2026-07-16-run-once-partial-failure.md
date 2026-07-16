# Run-once partial failure - 2026-07-16

## Symptom

GitHub Actions run-once exited with code 1 after:

- Gemini returned `503 Service Unavailable` for a new post summary.
- `https://www.airwallex.com/global/blog/the-venture-capital-perspective-with-kevin-spain-general-partner-emergence` failed extraction with `missing title`.

## Verified Causes

- The failing Airwallex URL currently responds with `301 Location: /global/blog`, so it is a stale sitemap URL that resolves to the generic blog index.
- The Gemini failure is transient provider overload and should be retried before marking the post `SUMMARY_FAILED`.

## Plan

- Retry transient Gemini failures (`429`, `503`, rate/high-demand wording) before failing summary generation.
- Detect canonical/redirected index pages in article extraction and treat them as unavailable articles.
- In monitor processing, skip unavailable article URLs without failing the whole run, while refreshing/storing state so the same stale sitemap entry does not repeatedly fail.
- Add focused regression tests and run the relevant Gradle tests.
