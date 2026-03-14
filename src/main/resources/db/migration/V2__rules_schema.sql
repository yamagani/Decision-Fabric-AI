-- V2: Rule Management Schema
-- Depends on: V1__baseline.sql

-- Enable trigram extension for fuzzy name search
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- -----------------------------------------------------------------------
-- rule_sets
-- -----------------------------------------------------------------------
CREATE TABLE rule_sets (
    id          UUID        NOT NULL,
    name        VARCHAR(255) NOT NULL,
    description TEXT        NOT NULL DEFAULT '',
    status      VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_by  VARCHAR(255) NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL,
    updated_at  TIMESTAMPTZ  NOT NULL,
    version     BIGINT       NOT NULL DEFAULT 0,

    CONSTRAINT pk_rule_sets PRIMARY KEY (id),
    CONSTRAINT chk_rule_sets_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
);

CREATE UNIQUE INDEX uq_rule_sets_name_lower
    ON rule_sets (lower(name))
    WHERE status = 'ACTIVE';

-- -----------------------------------------------------------------------
-- rules
-- -----------------------------------------------------------------------
CREATE TABLE rules (
    id          UUID        NOT NULL,
    rule_set_id UUID        NOT NULL,
    name        VARCHAR(255) NOT NULL,
    description TEXT        NOT NULL DEFAULT '',
    status      VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_by  VARCHAR(255) NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL,
    updated_at  TIMESTAMPTZ  NOT NULL,
    row_version BIGINT       NOT NULL DEFAULT 0,

    CONSTRAINT pk_rules PRIMARY KEY (id),
    CONSTRAINT fk_rules_rule_set FOREIGN KEY (rule_set_id) REFERENCES rule_sets(id),
    CONSTRAINT chk_rules_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
);

-- Composite unique: name is unique within a rule set (case-insensitive) for ACTIVE rules
CREATE UNIQUE INDEX uq_rules_ruleset_name_lower
    ON rules (rule_set_id, lower(name))
    WHERE status = 'ACTIVE';

-- Index for filtering by status + rule_set_id (used in listing queries)
CREATE INDEX idx_rules_status_ruleset
    ON rules (status, rule_set_id);

-- Trigram index for fuzzy name search
CREATE INDEX idx_rules_name_trgm
    ON rules USING gin (name gin_trgm_ops);

-- -----------------------------------------------------------------------
-- rule_versions
-- -----------------------------------------------------------------------
CREATE TABLE rule_versions (
    rule_id      UUID        NOT NULL,
    version      INT         NOT NULL,
    dmn_xml      TEXT        NOT NULL,
    status       VARCHAR(20)  NOT NULL DEFAULT 'DRAFT',
    created_by   VARCHAR(255) NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL,
    activated_at TIMESTAMPTZ,
    activated_by VARCHAR(255),

    CONSTRAINT pk_rule_versions PRIMARY KEY (rule_id, version),
    CONSTRAINT fk_rule_versions_rule FOREIGN KEY (rule_id) REFERENCES rules(id),
    CONSTRAINT chk_rule_versions_status CHECK (status IN ('DRAFT', 'ACTIVE', 'INACTIVE'))
);

-- Index to quickly find active versions by rule
CREATE INDEX idx_rule_versions_rule_status
    ON rule_versions (rule_id, status);

-- -----------------------------------------------------------------------
-- rule_audit_log
-- -----------------------------------------------------------------------
CREATE TABLE rule_audit_log (
    id             UUID        NOT NULL,
    entity_id      VARCHAR(255) NOT NULL,
    entity_type    VARCHAR(50)  NOT NULL,
    action         VARCHAR(50)  NOT NULL,
    performed_by   VARCHAR(255) NOT NULL,
    correlation_id VARCHAR(255) NOT NULL,
    detail         TEXT        NOT NULL DEFAULT '',
    occurred_at    TIMESTAMPTZ  NOT NULL,

    CONSTRAINT pk_rule_audit_log PRIMARY KEY (id)
);

-- Index for querying audit log by entity
CREATE INDEX idx_rule_audit_log_entity
    ON rule_audit_log (entity_id, entity_type, occurred_at DESC);
