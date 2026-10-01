-- Durable idempotency store for payment-provider webhook deliveries.
-- event_id is the provider's unique event identifier and is the idempotency key.
CREATE TABLE payment_webhook_events (
    id                     BIGSERIAL PRIMARY KEY,
    event_id               VARCHAR(128) NOT NULL,
    transaction_reference  VARCHAR(64)  NOT NULL,
    status                 VARCHAR(20)  NOT NULL CHECK (status IN ('SUCCESS', 'FAILED')),
    processed_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uk_payment_webhook_event_id UNIQUE (event_id),
    CONSTRAINT fk_payment_webhook_transaction
        FOREIGN KEY (transaction_reference)
        REFERENCES payments (transaction_reference)
);

CREATE INDEX idx_payment_webhook_transaction_reference
    ON payment_webhook_events (transaction_reference);
