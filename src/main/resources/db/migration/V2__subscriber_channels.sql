CREATE TABLE subscribers (
    id UUID PRIMARY KEY,
    display_name VARCHAR(255) NOT NULL,
    status VARCHAR(64) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE TABLE subscriber_channels (
    id UUID PRIMARY KEY,
    subscriber_id UUID NOT NULL,
    channel VARCHAR(32) NOT NULL,
    recipient VARCHAR(255) NOT NULL,
    status VARCHAR(64) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_subscriber_channels_subscriber FOREIGN KEY (subscriber_id) REFERENCES subscribers(id),
    CONSTRAINT uq_subscriber_channels_channel_recipient UNIQUE (channel, recipient)
);

CREATE INDEX idx_subscriber_channels_channel_status ON subscriber_channels(channel, status);
CREATE INDEX idx_subscriber_channels_subscriber_id ON subscriber_channels(subscriber_id);
