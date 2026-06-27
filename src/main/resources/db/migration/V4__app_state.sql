CREATE TABLE app_state (
    state_key VARCHAR(128) PRIMARY KEY,
    state_value TEXT NOT NULL,
    updated_at TIMESTAMP NOT NULL
);
