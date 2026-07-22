#!/usr/bin/env bash
set -euo pipefail

STACK_NAME="${FF_STACK_NAME:-detp-besu}"

if ! command -v ff >/dev/null 2>&1; then
  echo "FAIL: ff CLI not found. Run ./scripts/dlt-firefly-init.sh first."
  exit 1
fi

echo "=== Starting FireFly stack '$STACK_NAME' ==="
ff start "$STACK_NAME"

echo ""
echo "FireFly Core API (typical): http://localhost:5000"
echo "Wire day-one services:      ./scripts/dlt-firefly-wire.sh"
echo "Logs:                       ff logs $STACK_NAME --follow"
