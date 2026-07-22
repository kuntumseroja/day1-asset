#!/usr/bin/env bash
# Start D-ETP dev stack: infra in Docker, sims + Java services locally
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
# shellcheck disable=SC1091
source "$ROOT/scripts/docker-env.sh"
cd "$ROOT"

LOG_DIR="$ROOT/.dev-logs"
mkdir -p "$LOG_DIR"

echo "=== Starting infrastructure (Docker) ==="
docker compose up -d postgres redpanda temporal temporal-ui 2>&1 | tail -5

echo "Waiting for postgres..."
for i in $(seq 1 30); do
  if docker compose exec -T postgres pg_isready -U detp >/dev/null 2>&1; then break; fi
  sleep 2
done

echo "Waiting for Temporal..."
for i in $(seq 1 60); do
  if nc -z localhost 7233 2>/dev/null; then break; fi
  sleep 2
done

echo "=== Installing simulator dependencies ==="
for sim in rtgs-sim firefly-stub portal-sim; do
  if [ ! -d "sim/$sim/node_modules" ]; then
    (cd "sim/$sim" && npm install --silent)
  fi
done

start_bg() {
  local name=$1
  shift
  if [ -f "$LOG_DIR/$name.pid" ] && kill -0 "$(cat "$LOG_DIR/$name.pid")" 2>/dev/null; then
    echo "$name already running (pid $(cat "$LOG_DIR/$name.pid"))"
    return
  fi
  echo "Starting $name..."
  nohup "$@" >"$LOG_DIR/$name.log" 2>&1 &
  echo $! >"$LOG_DIR/$name.pid"
}

echo "=== Starting simulators ==="
start_bg rtgs-sim bash -c "cd sim/rtgs-sim && PORT=8091 SAGA_WEBHOOK_URL=http://localhost:8086/api/v1/rtgs/debit-confirmed PORTAL_SIM_URL=http://localhost:8093 npm start"
start_bg firefly-stub bash -c "cd sim/firefly-stub && PORT=8092 npm start"
start_bg portal-sim bash -c "cd sim/portal-sim && PORT=8093 npm start"

echo "=== Starting Java services ==="
export PATH="/opt/homebrew/bin:$PATH"
export SPRING_DATASOURCE_URL="${SPRING_DATASOURCE_URL:-jdbc:postgresql://localhost:5433/detp}"
export PGURL="${PGURL:-postgresql://detp:detp@localhost:5433/detp}"
export SPRING_KAFKA_LISTENER_AUTO_STARTUP=false
start_bg policy bash -c "cd policy && SPRING_DATASOURCE_URL=$SPRING_DATASOURCE_URL SPRING_KAFKA_LISTENER_AUTO_STARTUP=false mvn -q spring-boot:run"
start_bg recon bash -c "cd recon && SPRING_DATASOURCE_URL=$SPRING_DATASOURCE_URL mvn -q spring-boot:run"
start_bg firefly-kit bash -c "cd firefly-kit && SPRING_DATASOURCE_URL=$SPRING_DATASOURCE_URL mvn -q spring-boot:run"
start_bg saga-lib bash -c "cd saga-lib && SPRING_DATASOURCE_URL=$SPRING_DATASOURCE_URL mvn -q spring-boot:run"

echo "Waiting for services..."
wait_for() {
  local name=$1 url=$2 max=${3:-60}
  for i in $(seq 1 "$max"); do
    if curl -sf "$url" >/dev/null 2>&1; then
      echo "  OK $name"
      return 0
    fi
    sleep 2
  done
  echo "  TIMEOUT $name ($url) — see $LOG_DIR/$name.log"
  return 1
}

wait_for rtgs-sim http://localhost:8091/health 15
wait_for firefly-stub http://localhost:8092/health 15
wait_for portal-sim http://localhost:8093/health 15
wait_for policy http://localhost:8084/actuator/health 120
wait_for recon http://localhost:8085/api/v1/recon/status 120
wait_for saga-lib http://localhost:8086/actuator/health 180 || echo "  (saga-lib may still be starting — needs Temporal)"

echo ""
echo "Dev stack ready. Logs: $LOG_DIR/"
echo "  Portal UI:  cd portal && npm run dev"
echo "  Demos:      make demo-replay demo-limit-change demo-break"
