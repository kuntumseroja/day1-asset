#!/usr/bin/env bash
# Wire EC2 A (app stack) to FireFly on another host (EC2 B or Kaleido).
set -euo pipefail

usage() {
  cat <<EOF
Usage: $0 <firefly-host-ip-or-hostname> [--apply]

Examples:
  $0 10.0.2.87              # print export commands
  $0 10.0.2.87 --apply      # stop stub, recreate saga-lib firefly-kit recon

Environment written:
  FIREFLY_HOST=http://<host>:5000
  FIREFLY_WS_HOST=ws://<host>:5000/ws
EOF
}

if [[ $# -lt 1 ]]; then
  usage
  exit 1
fi

HOST="$1"
APPLY="${2:-}"

FIREFLY_HTTP="http://${HOST}:5000"
FIREFLY_WS="ws://${HOST}:5000/ws"

echo "=== Remote FireFly wire ==="
echo "FIREFLY_HOST=$FIREFLY_HTTP"
echo "FIREFLY_WS_HOST=$FIREFLY_WS"
echo ""
echo "# CLI demos on EC2 A:"
echo "export FIREFLY_URL=$FIREFLY_HTTP"
echo "export FIREFLY_WS_URL=$FIREFLY_WS"
echo ""

if ! curl -sf "${FIREFLY_HTTP}/api/v1/status" >/dev/null; then
  echo "WARN: cannot reach ${FIREFLY_HTTP}/api/v1/status (SG / ff start / wrong IP?)"
else
  echo "OK: FireFly reachable at $FIREFLY_HTTP"
fi

if [[ "$APPLY" == "--apply" ]]; then
  ROOT="$(cd "$(dirname "$0")/.." && pwd)"
  cd "$ROOT"
  export FIREFLY_HOST="$FIREFLY_HTTP"
  export FIREFLY_WS_HOST="$FIREFLY_WS"
  docker compose stop firefly-stub 2>/dev/null || true
  docker compose -f docker-compose.yml -f docker-compose.dlt.yml up -d saga-lib firefly-kit recon
  echo "OK: recreated saga-lib, firefly-kit, recon with remote FireFly"
fi
