#!/usr/bin/env bash
# Smoke test: outbox INSERT → Debezium CDC → settlement.events on Redpanda
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

export DOCKER_HOST="${DOCKER_HOST:-unix://$HOME/.colima/default/docker.sock}"
CONNECT_URL="${CONNECT_URL:-http://localhost:8083}"
PGURL="${PGURL:-postgresql://detp:detp@localhost:5433/detp}"

psql_cmd() {
  if command -v psql >/dev/null 2>&1; then
    psql "$PGURL" "$@"
  else
    docker compose exec -T postgres psql -U detp -d detp "$@"
  fi
}

echo "=== Debezium outbox → Kafka smoke test ==="

echo "Starting infra (postgres, redpanda, debezium)..."
docker compose up -d postgres redpanda debezium 2>&1 | tail -3

echo "Waiting for postgres..."
for i in $(seq 1 30); do
  docker compose exec -T postgres pg_isready -U detp >/dev/null 2>&1 && break
  sleep 2
done

echo "Waiting for Debezium Connect..."
for i in $(seq 1 30); do
  curl -sf "$CONNECT_URL/connectors" >/dev/null 2>&1 && break
  sleep 3
done

echo "Registering outbox connector..."
docker compose run --rm debezium-init 2>&1 | tail -5

STATUS=$(curl -sf "$CONNECT_URL/connectors/detp-outbox-connector/status" | python3 -c \
  "import sys,json; print(json.load(sys.stdin)['connector']['state'])" 2>/dev/null || echo "UNKNOWN")
if [ "$STATUS" != "RUNNING" ]; then
  echo "FAIL: connector state=$STATUS (expected RUNNING)"
  curl -sf "$CONNECT_URL/connectors/detp-outbox-connector/status" || true
  exit 1
fi
echo "Connector RUNNING"

EVENT_ID="$(python3 -c 'import uuid; print(uuid.uuid4())')"
psql_cmd -v ON_ERROR_STOP=1 -q -c \
  "INSERT INTO detp.outbox (id, aggregate_type, aggregate_id, event_type, payload)
   VALUES ('$EVENT_ID'::uuid, 'smoke', 'smoke-aggregate', 'ConfigChanged', '{\"smoke\":true}'::jsonb)"

echo "Inserted outbox row $EVENT_ID — consuming settlement.events (15s timeout)..."
if docker compose exec -T redpanda rpk topic consume settlement.events -n 1 -o start --brokers redpanda:9092 2>/dev/null | grep -q "$EVENT_ID"; then
  echo "PASS: debezium-smoke — event reached settlement.events"
  exit 0
fi

# Fallback: consume from end (event may already be in topic)
MSG=$(timeout 15 docker compose exec -T redpanda rpk topic consume settlement.events -n 5 -o end --brokers redpanda:9092 2>/dev/null || true)
if echo "$MSG" | grep -q "smoke-aggregate\|ConfigChanged\|$EVENT_ID"; then
  echo "PASS: debezium-smoke — event found on settlement.events"
  exit 0
fi

echo "FAIL: no matching message on settlement.events within timeout"
echo "$MSG" | tail -20
exit 1
