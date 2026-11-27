-- =========================================
-- V2: Initialize Leads and Customers tables
-- CRM System — Phase 2: Leads & Customers
-- =========================================

-- -----------------------------------------
-- 1. Leads Table
-- -----------------------------------------
CREATE TABLE leads (
    id               BIGSERIAL PRIMARY KEY,
    name             VARCHAR(100)  NOT NULL,
    company          VARCHAR(100),
    email            VARCHAR(150)  NOT NULL,
    phone            VARCHAR(50),
    source           VARCHAR(30)   NOT NULL CHECK (source IN ('WEBSITE', 'REFERRAL', 'COLD_CALL', 'EVENT', 'OTHER')),
    status           VARCHAR(30)   NOT NULL CHECK (status IN ('NEW', 'CONTACTED', 'QUALIFIED', 'CONVERTED', 'LOST')),
    assigned_rep_id  BIGINT        REFERENCES users(id) ON DELETE SET NULL,
    created_at       TIMESTAMP     NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMP     NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_leads_status       ON leads (status);
CREATE INDEX idx_leads_source       ON leads (source);
CREATE INDEX idx_leads_assigned_rep ON leads (assigned_rep_id);
CREATE INDEX idx_leads_email        ON leads (email);
CREATE INDEX idx_leads_created_at   ON leads (created_at DESC);

-- -----------------------------------------
-- 2. Customers Table
-- -----------------------------------------
CREATE TABLE customers (
    id               BIGSERIAL PRIMARY KEY,
    name             VARCHAR(100)  NOT NULL,
    company          VARCHAR(100),
    email            VARCHAR(150)  NOT NULL,
    phone            VARCHAR(50),
    address          TEXT,
    status           VARCHAR(30)   NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'INACTIVE', 'CHURNED')),
    origin_lead_id   BIGINT        UNIQUE REFERENCES leads(id) ON DELETE SET NULL,
    assigned_rep_id  BIGINT        REFERENCES users(id) ON DELETE SET NULL,
    created_at       TIMESTAMP     NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMP     NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_customers_status       ON customers (status);
CREATE INDEX idx_customers_assigned_rep ON customers (assigned_rep_id);
CREATE INDEX idx_customers_origin_lead  ON customers (origin_lead_id);
CREATE INDEX idx_customers_email        ON customers (email);
CREATE INDEX idx_customers_created_at   ON customers (created_at DESC);
