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

WAL=$(docker compose exec -T postgres psql -U detp -d detp -tA -c "SHOW wal_level;" 2>/dev/null | tr -d '[:space:]')
if [ "$WAL" != "logical" ]; then
  echo "Enabling logical replication (wal_level=logical) — postgres restart required..."
  docker compose exec -T postgres psql -U detp -d detp -c "ALTER SYSTEM SET wal_level = logical;" >/dev/null
  docker compose exec -T postgres psql -U detp -d detp -c "ALTER SYSTEM SET max_replication_slots = 4;" >/dev/null
  docker compose exec -T postgres psql -U detp -d detp -c "ALTER SYSTEM SET max_wal_senders = 4;" >/dev/null
  docker compose restart postgres >/dev/null
  for i in $(seq 1 30); do
    docker compose exec -T postgres pg_isready -U detp >/dev/null 2>&1 && break
    sleep 2
  done
fi

echo "Waiting for Debezium Connect..."
for i in $(seq 1 30); do
  curl -sf "$CONNECT_URL/connectors" >/dev/null 2>&1 && break
  sleep 3
done

echo "Registering outbox connector..."
docker compose run --rm debezium-init 2>&1 | tail -5

STATUS=$(curl -sf "$CONNECT_URL/connectors/detp-outbox-connector/status" | python3 -c \
  "import sys,json; s=json.load(sys.stdin); print(s['tasks'][0]['state'] if s.get('tasks') else s['connector']['state'])" 2>/dev/null || echo "UNKNOWN")
if [ "$STATUS" != "RUNNING" ]; then
  echo "FAIL: connector task state=$STATUS (expected RUNNING) — waiting 10s and retrying..."
  sleep 10
  STATUS=$(curl -sf "$CONNECT_URL/connectors/detp-outbox-connector/status" | python3 -c \
    "import sys,json; s=json.load(sys.stdin); print(s['tasks'][0]['state'] if s.get('tasks') else s['connector']['state'])" 2>/dev/null || echo "UNKNOWN")
fi
if [ "$STATUS" != "RUNNING" ]; then
  echo "FAIL: connector task state=$STATUS (expected RUNNING)"
  curl -sf "$CONNECT_URL/connectors/detp-outbox-connector/status" || true
  exit 1
fi
echo "Connector task RUNNING"

EVENT_ID="$(python3 -c 'import uuid; print(uuid.uuid4())')"
topic_hwm() {
  docker compose exec -T redpanda rpk topic describe settlement.events -p --brokers redpanda:9092 2>/dev/null \
    | tail -1 | awk '{print $NF}'
}

HW_BEFORE=$(topic_hwm)
[ -n "$HW_BEFORE" ] || HW_BEFORE=0

AGG_ID="smoke-$(date +%s)"
psql_cmd -v ON_ERROR_STOP=1 -q -c \
  "INSERT INTO detp.outbox (id, aggregate_type, aggregate_id, event_type, payload)
   VALUES ('$EVENT_ID'::uuid, 'smoke', '$AGG_ID', 'ConfigChanged', '{\"smoke\":true}'::jsonb)"

echo "Inserted outbox row $EVENT_ID (aggregate_id=$AGG_ID) — waiting for settlement.events..."
for i in $(seq 1 20); do
  HW_AFTER=$(topic_hwm)
  if [ -n "$HW_AFTER" ] && [ "$HW_AFTER" -gt "$HW_BEFORE" ]; then
    echo "PASS: debezium-smoke — settlement.events offset $HW_BEFORE → $HW_AFTER"
    exit 0
  fi
  sleep 1
done

echo "FAIL: settlement.events high-water mark did not advance (before=$HW_BEFORE after=${HW_AFTER:-0})"
exit 1
