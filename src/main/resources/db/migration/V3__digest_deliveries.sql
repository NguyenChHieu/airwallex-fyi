CREATE TABLE digest_deliveries (
    id UUID PRIMARY KEY,
    subscriber_channel_id UUID NOT NULL,
    local_date DATE NOT NULL,
    message_type VARCHAR(32) NOT NULL,
    status VARCHAR(64) NOT NULL,
    recipient VARCHAR(255) NOT NULL,
    channel VARCHAR(32) NOT NULL,
    payload_preview TEXT,
    provider_message_id VARCHAR(255),
    error_message TEXT,
    attempted_at TIMESTAMP NOT NULL,
    sent_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_digest_deliveries_subscriber_channel FOREIGN KEY (subscriber_channel_id) REFERENCES subscriber_channels(id),
    CONSTRAINT uq_digest_deliveries_channel_date UNIQUE (subscriber_channel_id, local_date)
);

CREATE TABLE digest_delivery_posts (
    id UUID PRIMARY KEY,
    digest_delivery_id UUID NOT NULL,
    post_id UUID NOT NULL,
    summary_id UUID NOT NULL,
    display_order INTEGER NOT NULL,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_digest_delivery_posts_delivery FOREIGN KEY (digest_delivery_id) REFERENCES digest_deliveries(id),
    CONSTRAINT fk_digest_delivery_posts_post FOREIGN KEY (post_id) REFERENCES posts(id),
    CONSTRAINT fk_digest_delivery_posts_summary FOREIGN KEY (summary_id) REFERENCES summaries(id),
    CONSTRAINT uq_digest_delivery_posts_delivery_post UNIQUE (digest_delivery_id, post_id)
);

CREATE INDEX idx_digest_deliveries_status ON digest_deliveries(status);
CREATE INDEX idx_digest_deliveries_local_date ON digest_deliveries(local_date);
CREATE INDEX idx_digest_deliveries_subscriber_channel_id ON digest_deliveries(subscriber_channel_id);
CREATE INDEX idx_digest_delivery_posts_delivery_id ON digest_delivery_posts(digest_delivery_id);
