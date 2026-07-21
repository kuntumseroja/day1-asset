#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
FIREFLY_URL="${FIREFLY_URL:-http://localhost:8092}"
KIT_URL="${KIT_URL:-http://localhost:8087}"

echo "=== FireFly demo-replay: disconnect listener, mint 3, reconnect ==="

# Mint 3 tokens while listener is down (via stub directly)
for i in 1 2 3; do
  UETR="replay-test-00000000-0000-4000-8000-00000000000${i}"
  curl -sf -X POST "$FIREFLY_URL/api/v1/tokens/mint" \
    -H 'Content-Type: application/json' \
    -d "{\"pool\":\"wRD\",\"amount\":\"1000000\",\"idempotencyKey\":\"$UETR\"}" > /dev/null
  echo "Mint $i submitted ($UETR)"
done

# Replay from offset 0 via control endpoint
RESULT=$(curl -sf -X POST "$FIREFLY_URL/control/replay-from-offset" \
  -H 'Content-Type: application/json' \
  -d '{"offset":0}')
COUNT=$(echo "$RESULT" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('count',0))")

echo "Replay returned $COUNT events"
if [ "$COUNT" -lt 3 ]; then
  echo "FAIL: expected at least 3 replay events"
  exit 1
fi

echo "PASS: demo-replay"
