-- V1: Core platform tables (shared by saga-lib, policy, recon, firefly-kit)

CREATE SCHEMA IF NOT EXISTS detp;

-- Saga instances
CREATE TABLE detp.saga_instances (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    uetr VARCHAR(36) NOT NULL UNIQUE,
    saga_type VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    amount BIGINT NOT NULL,
    participant_id VARCHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_saga_status ON detp.saga_instances(status);

-- Idempotency registry (48h TTL enforced at application layer)
CREATE TABLE detp.idempotency_registry (
    uetr VARCHAR(36) PRIMARY KEY,
    original_response JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expires_at TIMESTAMPTZ NOT NULL
);

-- Wallet balances
CREATE TABLE detp.wallet_balances (
    participant_id VARCHAR(64) PRIMARY KEY,
    balance BIGINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Supply ledger
CREATE TABLE detp.supply_ledger (
    id SERIAL PRIMARY KEY,
    total_supply BIGINT NOT NULL,
    recorded_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

INSERT INTO detp.supply_ledger (total_supply) VALUES (0);

-- Outbox (Debezium CDC source)
CREATE TABLE detp.outbox (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    aggregate_type VARCHAR(64) NOT NULL,
    aggregate_id VARCHAR(64) NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    payload JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_outbox_created ON detp.outbox(created_at);

-- FireFly listener offsets
CREATE TABLE detp.listener_offsets (
    listener_id VARCHAR(64) PRIMARY KEY,
    topic VARCHAR(128) NOT NULL,
    last_offset BIGINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Policy rules
CREATE TABLE detp.policy_rules (
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

CREATE TABLE detp.policy_audit (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    rule_id UUID REFERENCES detp.policy_rules(id),
    action VARCHAR(32) NOT NULL,
    actor_id VARCHAR(64) NOT NULL,
    detail JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Discrepancy cases (recon)
CREATE TABLE detp.discrepancy_cases (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    status VARCHAR(16) NOT NULL DEFAULT 'OPEN',
    delta BIGINT NOT NULL,
    rtgs_balance BIGINT NOT NULL,
    platform_balance BIGINT NOT NULL,
    chain_balance BIGINT NOT NULL,
    candidate_uetrs JSONB,
    sla_deadline TIMESTAMPTZ NOT NULL,
    resolution TEXT,
    evidence TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    resolved_at TIMESTAMPTZ
);

-- Debezium publication
CREATE PUBLICATION detp_outbox_pub FOR TABLE detp.outbox;
