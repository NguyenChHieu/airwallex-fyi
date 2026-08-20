-- notification_attempts was part of the original per-post alert design and has
-- never been read or written by application code (the digest-based delivery model
-- uses digest_deliveries/digest_delivery_posts instead).
DROP TABLE IF EXISTS notification_attempts;
