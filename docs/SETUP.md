# Setup

## Local

1. Install Java 21.
2. Copy `.env.example` to `.env`.
3. Fill only the values you need.
4. Run:

```powershell
./gradlew.bat test
./gradlew.bat bootRun --args="--run-once"
```

The example uses H2. For cloud Postgres, replace `DATABASE_*`.

## Telegram

1. Create a bot with `@BotFather`.
2. Send `/start` to your bot.
3. Open `https://api.telegram.org/bot<TELEGRAM_BOT_TOKEN>/getUpdates`.
4. Copy `message.chat.id` into `TELEGRAM_CHAT_ID`.

Leave `TELEGRAM_CHAT_ID` blank if users should subscribe themselves with `/start`; the next run processes it.

## Telegram Webhook

For instant `/start` replies, deploy the app to a public HTTPS URL and set:

```text
TELEGRAM_WEBHOOK_SECRET=<random-secret>
```

Then register the webhook:

```powershell
curl.exe "https://api.telegram.org/bot<TELEGRAM_BOT_TOKEN>/setWebhook?url=https://<YOUR_HOST>/telegram/webhook&secret_token=<TELEGRAM_WEBHOOK_SECRET>"
```

GitHub Actions does not host this endpoint; it only runs the daily polling job.

## GitHub Actions

Add these repository secrets:

```text
DATABASE_URL
DATABASE_USERNAME
DATABASE_PASSWORD
GEMINI_API_KEY
TELEGRAM_BOT_TOKEN
```

Optional:

```text
TELEGRAM_CHAT_ID
```

Then run `Daily Airwallex FYI` from the Actions tab. Scheduled runs target `9:00am Australia/Sydney`.

## Postgres

Use a JDBC URL:

```text
jdbc:postgresql://HOST:5432/postgres?sslmode=require
```

Set `DATABASE_DRIVER=org.postgresql.Driver`.

## WhatsApp

Twilio WhatsApp is optional. Leave `WHATSAPP_TO` blank to skip it.
