# D-ETP Day-One Assets

Project Garuda / SATRIA · wholesale Rupiah Digital (wRD) · IBM Consulting as SI.

Monorepo for Day-One assets 1–6: portal, saga-lib, policy, recon, ceremonies, firefly-kit.

See [SPEC.md](SPEC.md) for full build specification and sprint instructions.

## Prerequisites

- Docker Desktop (≥ 8 GB RAM recommended)
- Java 21 (backend services)
- Node.js 20+ (portal BFF, simulators)
- Maven 3.9+ (Java modules)

## AWS Indonesia (Jakarta) deploy

One-command deploy on EC2 in `ap-southeast-3`:

```bash
# On EC2 after clone — see deploy/aws/README.md
cp deploy/aws/.env.example .env   # set PUBLIC_URL to Elastic IP
./deploy/aws/deploy.sh
```

Or paste [`deploy/aws/user-data.sh`](deploy/aws/user-data.sh) into EC2 launch **User data** for fully automated bootstrap.

## Quick start (local)

```bash
# Start dev stack
docker compose up -d

# Wait for health checks, then verify simulators
# Verify simulators (host ports)
curl http://localhost:8091/health   # rtgs-sim
curl http://localhost:8092/health   # firefly-stub
curl http://localhost:8093/health   # portal-sim

# Java services (also in docker-compose)
# saga-lib :8086, policy :8084, recon :8085, firefly-kit :8087

# Run demo scripts (after sprint build)
make demo-duplicate    # Asset 2
make demo-replay       # Asset 6
make demo-limit-change # Asset 3
make demo-break        # Asset 4
make dry-run           # Asset 5
```

## Monorepo layout

| Directory | Asset | Description |
|-----------|-------|-------------|
| `portal/` | 1 | React 18 + Carbon portal + Node/Express BFF |
| `saga-lib/` | 2 | Temporal saga workflows (issuance, redemption, transfer) |
| `policy/` | 3 | Drools 8 policy-as-data service |
| `recon/` | 4 | Three-way reconciliation |
| `ceremonies/` | 5 | HSM runbooks + SoftHSM dry-run scripts |
| `firefly-kit/` | 6 | FireFly client, listener, Pact contracts |
| `sim/` | — | rtgs-sim, firefly-stub, portal-sim |
| `contracts/` | — | OpenAPI, Avro, Pact (single source of truth) |

## Sprint order

1. **S0** — Repo, docker-compose, contracts, sim stubs
2. **S1** — saga-lib + firefly-kit
3. **S2** — policy + recon
4. **S3** — portal + ceremonies

## Global conventions

- **Contract-first** — all inter-asset APIs in `contracts/`
- **UETR idempotency** — 48h dedup window
- **GATE semantics** — mint after RTGS debit; state after DLT finality
- **Outbox pattern** — no dual-write to Kafka
