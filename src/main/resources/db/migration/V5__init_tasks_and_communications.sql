-- =========================================
-- V5: Tasks & Communication Logging (Phase 4)
-- =========================================
-- Polymorphic links are (linked_entity_type, linked_entity_id) with no FK:
-- one table can point at leads, customers or deals. Services validate the
-- target and its visibility before saving.

CREATE TABLE tasks (
    id                 BIGSERIAL PRIMARY KEY,
    title              VARCHAR(150) NOT NULL,
    description        TEXT,
    due_at             TIMESTAMP    NOT NULL,
    status             VARCHAR(20)  NOT NULL DEFAULT 'PENDING'
                       CHECK (status IN ('PENDING', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED')),
    priority           VARCHAR(10)  NOT NULL DEFAULT 'MEDIUM'
                       CHECK (priority IN ('LOW', 'MEDIUM', 'HIGH')),
    linked_entity_type VARCHAR(20)  NOT NULL
                       CHECK (linked_entity_type IN ('LEAD', 'CUSTOMER', 'DEAL')),
    linked_entity_id   BIGINT       NOT NULL,
    linked_entity_name VARCHAR(150),
    assigned_to_id     BIGINT       NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    created_by_id      BIGINT       NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    completed_at       TIMESTAMP,
    version            BIGINT       NOT NULL DEFAULT 0,
    created_at         TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_tasks_assignee     ON tasks (assigned_to_id);
CREATE INDEX idx_tasks_status_due   ON tasks (status, due_at);
CREATE INDEX idx_tasks_linked       ON tasks (linked_entity_type, linked_entity_id);
CREATE INDEX idx_tasks_created_at   ON tasks (created_at DESC);

CREATE TABLE communication_logs (
    id                 BIGSERIAL PRIMARY KEY,
    type               VARCHAR(12)  NOT NULL
                       CHECK (type IN ('CALL', 'MEETING', 'NOTE', 'EMAIL')),
    subject            VARCHAR(150),
    notes              TEXT,
    linked_entity_type VARCHAR(20)  NOT NULL
                       CHECK (linked_entity_type IN ('LEAD', 'CUSTOMER', 'DEAL')),
    linked_entity_id   BIGINT       NOT NULL,
    linked_entity_name VARCHAR(150),
    recipient_email    VARCHAR(150),
    logged_by_id       BIGINT       NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    logged_at          TIMESTAMP    NOT NULL DEFAULT NOW(),
    version            BIGINT       NOT NULL DEFAULT 0,
    created_at         TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_comms_linked       ON communication_logs (linked_entity_type, linked_entity_id, logged_at DESC);
CREATE INDEX idx_comms_type         ON communication_logs (type);
CREATE INDEX idx_comms_logged_at    ON communication_logs (logged_at DESC);
