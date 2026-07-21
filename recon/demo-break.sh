#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
RECON_URL="${RECON_URL:-http://localhost:8085}"
FIREFLY_URL="${FIREFLY_URL:-http://localhost:8092}"
RTGS_URL="${RTGS_URL:-http://localhost:8091}"
PGURL="${PGURL:-postgresql://detp:detp@localhost:5433/detp}"

psql_cmd() {
  if command -v psql >/dev/null 2>&1; then
    psql "$PGURL" "$@"
  else
    docker compose -f "$ROOT/docker-compose.yml" exec -T postgres \
      psql -U detp -d detp "$@"
  fi
}

MINT_AMOUNT="${MINT_AMOUNT:-50000000}"
UETR="${UETR:-00000000-0000-4000-8000-000000000001}"
PARTICIPANT="${PARTICIPANT:-BANK-A}"

echo "=== Recon demo-break: suppress mint confirmation → case → replay → green ==="

require_service() {
  local name=$1 url=$2
  if ! curl -sf "$url" >/dev/null 2>&1; then
    echo "FAIL: $name not reachable at $url"
    exit 1
  fi
}

require_service "recon" "$RECON_URL/api/v1/recon/status"
require_service "firefly-stub" "$FIREFLY_URL/health"
require_service "rtgs-sim" "$RTGS_URL/health"

# Align chain supply with platform ledger baseline
PLATFORM=$(psql_cmd -tA -c \
  "SELECT COALESCE((SELECT total_supply FROM detp.supply_ledger ORDER BY id DESC LIMIT 1), 0)")
curl -sf -X POST "$FIREFLY_URL/control/reset-supply" \
  -H 'Content-Type: application/json' \
  -d "{\"supply\":\"$PLATFORM\"}" >/dev/null
echo "Baseline platform/chain supply: $PLATFORM"

BASELINE=$(curl -sf -X POST "$RECON_URL/api/v1/recon/run")
echo "Baseline recon delta: $(echo "$BASELINE" | python3 -c "import sys,json; print(json.load(sys.stdin)['delta'])")"

# Introduce break: mint on chain without ledger apply (suppress WS confirmation)
curl -sf -X POST "$FIREFLY_URL/control/suppress-next-confirmation" >/dev/null
curl -sf -X POST "$FIREFLY_URL/api/v1/tokens/mint" \
  -H 'Content-Type: application/json' \
  -d "{\"pool\":\"wRD\",\"amount\":\"$MINT_AMOUNT\",\"idempotencyKey\":\"$UETR\"}" >/dev/null
echo "Mint submitted with suppressed confirmation ($UETR, amount=$MINT_AMOUNT)"

# RTGS funded, mint on chain (confirmation suppressed) — saga stays FUNDED until replay
curl -sf -X POST "$RTGS_URL/api/v1/pacs009" \
  -H 'Content-Type: application/json' \
  -d "{\"uetr\":\"$UETR\",\"amount\":\"$MINT_AMOUNT\",\"debtorAgent\":\"$PARTICIPANT\"}" >/dev/null

psql_cmd -v ON_ERROR_STOP=1 -q -c \
  "INSERT INTO detp.saga_instances (uetr, saga_type, status, amount, participant_id)
   VALUES ('$UETR', 'ISSUANCE', 'FUNDED', $MINT_AMOUNT, '$PARTICIPANT')
   ON CONFLICT (uetr) DO UPDATE SET status='FUNDED', amount=$MINT_AMOUNT, updated_at=NOW()"

BREAK=$(curl -sf -X POST "$RECON_URL/api/v1/recon/run")
BALANCED=$(echo "$BREAK" | python3 -c "import sys,json; print(json.load(sys.stdin)['balanced'])")
DELTA=$(echo "$BREAK" | python3 -c "import sys,json; print(json.load(sys.stdin)['delta'])")

if [ "$BALANCED" = "True" ] || [ "$BALANCED" = "true" ]; then
  echo "FAIL: expected recon break after suppressed mint, got balanced"
  exit 1
fi

ABS_DELTA=$(python3 -c "print(abs(int('$DELTA')))")
if [ "$ABS_DELTA" != "$MINT_AMOUNT" ]; then
  echo "FAIL: expected |delta|=$MINT_AMOUNT, got delta=$DELTA"
  exit 1
fi

CASES=$(curl -sf "$RECON_URL/api/v1/cases?status=OPEN")
CASE_COUNT=$(echo "$CASES" | python3 -c "import sys,json; print(len(json.load(sys.stdin)))")
if [ "$CASE_COUNT" -lt 1 ]; then
  echo "FAIL: expected at least one OPEN case"
  exit 1
fi

HAS_UETR=$(echo "$CASES" | python3 -c "import sys,json; cases=json.load(sys.stdin); print(any('$UETR' in c.get('candidateUetrs',[]) for c in cases))")
if [ "$HAS_UETR" != "True" ]; then
  echo "FAIL: OPEN case should list candidate UETR $UETR"
  exit 1
fi
echo "Break case opened: delta=$DELTA, UETR=$UETR identified"

# Replay suppressed confirmation and apply ledger state (resolution path)
REPLAY=$(curl -sf -X POST "$FIREFLY_URL/control/replay-from-offset" \
  -H 'Content-Type: application/json' \
  -d '{"offset":0}')
REPLAY_COUNT=$(echo "$REPLAY" | python3 -c "import sys,json; print(json.load(sys.stdin).get('count',0))")
echo "Replay returned $REPLAY_COUNT events"

psql_cmd -v ON_ERROR_STOP=1 -q <<SQL
BEGIN;
UPDATE detp.saga_instances SET status='SETTLED', updated_at=NOW() WHERE uetr='$UETR';
INSERT INTO detp.wallet_balances (participant_id, balance)
VALUES ('$PARTICIPANT', $MINT_AMOUNT)
ON CONFLICT (participant_id) DO UPDATE
SET balance = detp.wallet_balances.balance + $MINT_AMOUNT, updated_at=NOW();
INSERT INTO detp.supply_ledger (total_supply)
SELECT COALESCE((SELECT total_supply FROM detp.supply_ledger ORDER BY id DESC LIMIT 1),0) + $MINT_AMOUNT;
COMMIT;
SQL
echo "Applied ledger state for $UETR"

CASE_ID=$(echo "$CASES" | python3 -c "import sys,json; cases=json.load(sys.stdin); print(cases[0]['id'])")
curl -sf -X POST "$RECON_URL/api/v1/cases/$CASE_ID/resolve" \
  -H 'Content-Type: application/json' \
  -d '{"resolution":"Replayed mint confirmation and applied ledger","evidence":"demo-break.sh replay-from-offset"}' >/dev/null

GREEN=$(curl -sf -X POST "$RECON_URL/api/v1/recon/run")
GREEN_BALANCED=$(echo "$GREEN" | python3 -c "import sys,json; print(json.load(sys.stdin)['balanced'])")
GREEN_DELTA=$(echo "$GREEN" | python3 -c "import sys,json; print(json.load(sys.stdin)['delta'])")

if [ "$GREEN_BALANCED" != "True" ] && [ "$GREEN_BALANCED" != "true" ]; then
  echo "FAIL: expected balanced recon after replay, delta=$GREEN_DELTA"
  exit 1
fi

STATUS=$(curl -sf "$RECON_URL/api/v1/recon/status")
TILE=$(echo "$STATUS" | python3 -c "import sys,json; print(json.load(sys.stdin)['status'])")
echo "Status tile: $TILE"
echo "PASS: demo-break"
