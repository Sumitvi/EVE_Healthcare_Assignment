CREATE TABLE diagnostic_centres (
    id         BIGSERIAL PRIMARY KEY,
    name       VARCHAR(150) NOT NULL,
    location   VARCHAR(150) NOT NULL,
    address    VARCHAR(255),
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_centres_location ON diagnostic_centres (lower(location));

CREATE TABLE diagnostic_tests (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(150) NOT NULL,
    description VARCHAR(500),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX uq_tests_name ON diagnostic_tests (lower(name));

-- Which test a centre offers, and at what price.
CREATE TABLE centre_tests (
    id         BIGSERIAL PRIMARY KEY,
    centre_id  BIGINT         NOT NULL REFERENCES diagnostic_centres (id) ON DELETE CASCADE,
    test_id    BIGINT         NOT NULL REFERENCES diagnostic_tests (id),
    price      NUMERIC(10, 2) NOT NULL CHECK (price > 0),
    CONSTRAINT uq_centre_test UNIQUE (centre_id, test_id)
);
CREATE INDEX idx_centre_tests_test ON centre_tests (test_id);
