-- =========================================
-- V1: Initialize Users table
-- CRM System — Phase 1
-- =========================================

CREATE TABLE users (
    id         BIGSERIAL PRIMARY KEY,
    name       VARCHAR(100)  NOT NULL,
    email      VARCHAR(150)  NOT NULL UNIQUE,
    password   VARCHAR(255)  NOT NULL,
    role       VARCHAR(20)   NOT NULL CHECK (role IN ('ADMIN', 'SALES_REP')),
    enabled    BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP     NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_users_email ON users (email);
CREATE INDEX idx_users_role  ON users (role);
