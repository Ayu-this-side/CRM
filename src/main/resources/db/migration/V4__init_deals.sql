-- =========================================
-- V4: Sales / Deal Pipeline (Phase 3)
-- =========================================

CREATE TABLE deals (
    id                 BIGSERIAL PRIMARY KEY,
    title              VARCHAR(150)  NOT NULL,
    description        TEXT,
    customer_id        BIGINT        NOT NULL REFERENCES customers(id) ON DELETE RESTRICT,
    assigned_rep_id    BIGINT        REFERENCES users(id) ON DELETE SET NULL,
    value              NUMERIC(14,2) NOT NULL CHECK (value >= 0),
    stage              VARCHAR(30)   NOT NULL DEFAULT 'PROSPECTING'
                       CHECK (stage IN ('PROSPECTING', 'PROPOSAL', 'NEGOTIATION', 'WON', 'LOST')),
    probability        INTEGER       NOT NULL DEFAULT 10 CHECK (probability BETWEEN 0 AND 100),
    expected_close_date DATE,
    actual_close_date   DATE,
    lost_reason        TEXT,
    version            BIGINT        NOT NULL DEFAULT 0,
    created_at         TIMESTAMP     NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMP     NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_deals_stage       ON deals (stage);
CREATE INDEX idx_deals_customer    ON deals (customer_id);
CREATE INDEX idx_deals_assigned_rep ON deals (assigned_rep_id);
CREATE INDEX idx_deals_created_at  ON deals (created_at DESC);
