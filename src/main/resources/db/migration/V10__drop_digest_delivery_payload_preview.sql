-- payload_preview stored ~500 chars of the message body per delivery row, per
-- subscriber, per day. Nothing reads it back (not exposed by any admin endpoint),
-- and the content is reconstructable from digest_delivery_posts + summaries.
-- At scale this was the single largest contributor to per-subscriber storage cost.
ALTER TABLE digest_deliveries DROP COLUMN IF EXISTS payload_preview;
