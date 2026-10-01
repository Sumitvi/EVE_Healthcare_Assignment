-- A booking of one diagnostic test at one centre. No FK to centre_tests on purpose: a booking must survive
-- the centre later dropping the test or changing its price (amount is a snapshot taken at booking time).
CREATE TABLE bookings (
    id                    BIGSERIAL PRIMARY KEY,
    user_id               BIGINT         NOT NULL REFERENCES users (id),
    test_id               BIGINT         NOT NULL REFERENCES diagnostic_tests (id),
    centre_id             BIGINT         NOT NULL REFERENCES diagnostic_centres (id),
    -- wall-clock time at the centre, interpreted in the configured booking time zone (app.booking.zone)
    appointment_date_time TIMESTAMP      NOT NULL,
    amount                NUMERIC(10, 2) NOT NULL CHECK (amount > 0),
    status                VARCHAR(20)    NOT NULL CHECK (status IN ('PENDING', 'CONFIRMED', 'FAILED', 'CANCELLED')),
    created_at            TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ    NOT NULL DEFAULT now()
);

-- "my bookings, newest first" (GET /bookings)
CREATE INDEX idx_bookings_user_created ON bookings (user_id, created_at DESC, id DESC);
-- centre schedule lookups; also covers the centre_id FK
CREATE INDEX idx_bookings_centre_appointment ON bookings (centre_id, appointment_date_time);
-- covers the test_id FK
CREATE INDEX idx_bookings_test ON bookings (test_id);
