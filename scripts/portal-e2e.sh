#!/usr/bin/env bash
# Portal E2E smoke: login → submit 500M → queue → limits → pacs.009 detail
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
PORTAL_SIM="${PORTAL_SIM:-http://localhost:8093}"
TOKEN=$(printf '{"participantId":"BANK-A","role":"MAKER","tier":"TIER_1","displayName":"Bank A Pilot"}' | base64)

echo "=== Portal E2E demo (portal-sim API) ==="

require() {
  curl -sf "$1" >/dev/null || { echo "FAIL: $2 not reachable at $1"; exit 1; }
}

require "$PORTAL_SIM/health" "portal-sim"

echo "Step 1: auth/me (login context)..."
ME=$(curl -sf -H "Authorization: Bearer $TOKEN" "$PORTAL_SIM/api/v1/auth/me")
echo "$ME" | grep -q 'BANK-A' || { echo "FAIL: auth/me"; exit 1; }

echo "Step 2: submit issuance Rp 500M..."
TX=$(curl -sf -X POST "$PORTAL_SIM/api/v1/issuance" \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"amount":500000000,"valueDate":"2026-07-21","fundingReference":"FUND-E2E-001"}')
UETR=$(echo "$TX" | python3 -c "import sys,json; print(json.load(sys.stdin)['uetr'])")
echo "  UETR=$UETR status=$(echo "$TX" | python3 -c "import sys,json; print(json.load(sys.stdin)['status'])")"

echo "Step 3: FAFO queue contains submission..."
QUEUE=$(curl -sf -H "Authorization: Bearer $TOKEN" "$PORTAL_SIM/api/v1/queue")
echo "$QUEUE" | grep -q "$UETR" || { echo "FAIL: UETR not in queue"; exit 1; }

echo "Step 4: limits dashboard (per-issuance cap gauge data)..."
LIMITS=$(curl -sf -H "Authorization: Bearer $TOKEN" "$PORTAL_SIM/api/v1/limits")
CAP=$(echo "$LIMITS" | python3 -c "import sys,json; print(json.load(sys.stdin)['perIssuanceCap'])")
[ "$CAP" = "500000000" ] || { echo "FAIL: expected cap 500M, got $CAP"; exit 1; }

echo "Step 5: transaction detail includes pacs.009 XML..."
DETAIL=$(curl -sf -H "Authorization: Bearer $TOKEN" "$PORTAL_SIM/api/v1/transactions/$UETR")
echo "$DETAIL" | grep -q 'pacs.009.001.08' || { echo "FAIL: missing pacs.009 message type"; exit 1; }
echo "$DETAIL" | grep -q '<UETR>' || { echo "FAIL: missing UETR in XML"; exit 1; }

echo "Step 6: portal UI build..."
(cd "$ROOT/portal" && npm run build --silent)

echo "PASS: portal-e2e — login → 500M submit → queue → limits → pacs.009 + UI build"
