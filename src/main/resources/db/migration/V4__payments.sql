-- One payment attempt per booking. The simulated gateway answers synchronously, so a payment row is only ever
-- written with its final status (SUCCESS / FAILED); there is no PENDING payment.
CREATE TABLE payments (
    id                    BIGSERIAL PRIMARY KEY,
    booking_id            BIGINT         NOT NULL REFERENCES bookings (id),
    -- copied from bookings.amount server-side, never taken from the client
    amount                NUMERIC(10, 2) NOT NULL CHECK (amount > 0),
    status                VARCHAR(20)    NOT NULL CHECK (status IN ('SUCCESS', 'FAILED')),
    transaction_reference VARCHAR(64)    NOT NULL,
    failure_reason        VARCHAR(255),
    created_at            TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ    NOT NULL DEFAULT now(),
    -- database-level guard against duplicate payments for a booking (also backs the FK lookup)
    CONSTRAINT uk_payments_booking UNIQUE (booking_id),
    -- lets a later webhook locate a payment by the provider's reference, and keeps references unique
    CONSTRAINT uk_payments_transaction_reference UNIQUE (transaction_reference),
    CONSTRAINT chk_payments_failure_reason CHECK (status = 'FAILED' OR failure_reason IS NULL)
);
