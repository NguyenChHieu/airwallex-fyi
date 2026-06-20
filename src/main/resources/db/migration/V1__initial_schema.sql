CREATE TABLE posts (
    id UUID PRIMARY KEY,
    url VARCHAR(2048) NOT NULL,
    source_type VARCHAR(32) NOT NULL,
    title VARCHAR(512),
    description TEXT,
    author VARCHAR(255),
    published_at TIMESTAMP,
    sitemap_lastmod TIMESTAMP,
    discovered_at TIMESTAMP NOT NULL,
    content_hash VARCHAR(128),
    article_body TEXT,
    processing_status VARCHAR(64) NOT NULL DEFAULT 'DISCOVERED',
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT uq_posts_url UNIQUE (url)
);

CREATE TABLE summaries (
    id UUID PRIMARY KEY,
    post_id UUID NOT NULL,
    headline VARCHAR(512) NOT NULL,
    summary_json TEXT NOT NULL,
    why_it_matters TEXT,
    tags_json TEXT,
    model VARCHAR(128),
    prompt_version VARCHAR(64),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_summaries_post FOREIGN KEY (post_id) REFERENCES posts(id),
    CONSTRAINT uq_summaries_post_id UNIQUE (post_id)
);

CREATE TABLE notification_attempts (
    id UUID PRIMARY KEY,
    post_id UUID NOT NULL,
    channel VARCHAR(32) NOT NULL,
    recipient VARCHAR(255) NOT NULL,
    status VARCHAR(64) NOT NULL,
    provider_message_id VARCHAR(255),
    error_message TEXT,
    attempted_at TIMESTAMP NOT NULL,
    sent_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_notification_attempts_post FOREIGN KEY (post_id) REFERENCES posts(id),
    CONSTRAINT uq_notification_attempts_post_channel_recipient UNIQUE (post_id, channel, recipient)
);

CREATE INDEX idx_posts_discovered_at ON posts(discovered_at DESC);
CREATE INDEX idx_notification_attempts_status ON notification_attempts(status);