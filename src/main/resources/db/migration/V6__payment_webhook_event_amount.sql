-- Store the amount included in the processed event so an eventId cannot be reused
-- with a different payload while still being treated as an idempotent retry.
ALTER TABLE payment_webhook_events
    ADD COLUMN amount NUMERIC(10, 2);

UPDATE payment_webhook_events e
SET amount = p.amount
FROM payments p
WHERE p.transaction_reference = e.transaction_reference;

ALTER TABLE payment_webhook_events
    ALTER COLUMN amount SET NOT NULL;

ALTER TABLE payment_webhook_events
    ADD CONSTRAINT chk_payment_webhook_event_amount CHECK (amount > 0);
