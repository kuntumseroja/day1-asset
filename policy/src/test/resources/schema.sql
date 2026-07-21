CREATE SCHEMA IF NOT EXISTS detp;

CREATE TABLE IF NOT EXISTS detp.policy_rules (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(128) NOT NULL,
    drl_content TEXT NOT NULL,
    version INT NOT NULL DEFAULT 1,
    status VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    effective_from TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    author_id VARCHAR(64) NOT NULL,
    approver_id VARCHAR(64),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS detp.policy_audit (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    rule_id UUID REFERENCES detp.policy_rules(id),
    action VARCHAR(32) NOT NULL,
    actor_id VARCHAR(64) NOT NULL,
    detail JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS detp.outbox (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    aggregate_type VARCHAR(64) NOT NULL,
    aggregate_id VARCHAR(64) NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    payload JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
