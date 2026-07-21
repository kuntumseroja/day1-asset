#!/usr/bin/env bash
set -euo pipefail
POLICY_URL="${POLICY_URL:-http://localhost:8084}"
AUTHOR="${AUTHOR:-policy-author}"
APPROVER="${APPROVER:-policy-checker}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

echo "=== Policy demo-limit-change: 500M → 750M per-issuance cap ==="

evaluate() {
  local amount="$1"
  curl -sf -X POST "$POLICY_URL/api/v1/policy/evaluate" \
    -H 'Content-Type: application/json' \
    -d "{\"participantId\":\"BANK-A\",\"tier\":\"TIER_1\",\"amount\":$amount,\"dailyCumulative\":1000000000,\"timestamp\":\"2026-07-20T12:00:00Z\"}"
}

activate_cap() {
  local cap="$1"
  local label="$2"
  CREATE=$(python3 - <<PY
import json
from pathlib import Path
drl = Path("samples/per_issuance_cap.drl").read_text().replace("500000000", "$cap")
body = {"name": "per_issuance_cap", "drlContent": drl, "authorId": "$AUTHOR"}
print(json.dumps(body))
PY
)
  CREATE_RESP=$(curl -sf -X POST "$POLICY_URL/api/v1/rules" \
    -H 'Content-Type: application/json' \
    -d "$CREATE")
  RULE_ID=$(echo "$CREATE_RESP" | python3 -c "import sys,json; print(json.load(sys.stdin)['id'])")
  curl -sf -X PATCH "$POLICY_URL/api/v1/rules/$RULE_ID" \
    -H 'Content-Type: application/json' \
    -d "{\"action\":\"SUBMIT_TEST\",\"actorId\":\"$AUTHOR\"}" >/dev/null
  curl -sf -X PATCH "$POLICY_URL/api/v1/rules/$RULE_ID" \
    -H 'Content-Type: application/json' \
    -d "{\"action\":\"APPROVE\",\"actorId\":\"$APPROVER\"}" >/dev/null
  curl -sf -X PATCH "$POLICY_URL/api/v1/rules/$RULE_ID" \
    -H 'Content-Type: application/json' \
    -d "{\"action\":\"ACTIVATE\",\"actorId\":\"$APPROVER\"}" >/dev/null
  echo "Activated ${label} cap (rule $RULE_ID)"
}

echo "Step 0: ensure 500M per-issuance cap baseline..."
BASELINE=$(evaluate 600000000)
if echo "$BASELINE" | grep -q '"decision":"ALLOW"'; then
  activate_cap 500000000 "500M"
  BASELINE=$(evaluate 600000000)
fi
echo "$BASELINE" | grep -q '"decision":"DENY"' || { echo "FAIL: expected DENY at 600M with 500M baseline"; exit 1; }
echo "Step 1: evaluate Rp 600M (expect DENY)..."
BEFORE=$(evaluate 600000000)
echo "$BEFORE"
echo "$BEFORE" | grep -q '"decision":"DENY"' || { echo "FAIL: expected DENY before limit change"; exit 1; }

echo "Step 2: activate 750M cap via maker-checker..."
activate_cap 750000000 "750M"

echo "Step 3: maker-checker workflow complete"

echo "Step 4: evaluate Rp 600M again (expect ALLOW)..."
AFTER=$(evaluate 600000000)
echo "$AFTER"
echo "$AFTER" | grep -q '"decision":"ALLOW"' || { echo "FAIL: expected ALLOW after limit change"; exit 1; }

echo "Step 5: author self-approve must be rejected (403)..."
PROBE=$(AUTHOR="$AUTHOR" python3 - <<'PY'
import json, os
author = os.environ["AUTHOR"]
drl = (
    "package id.bi.detp.policy.rules\n"
    "import id.bi.detp.policy.domain.PolicyFact\n"
    "import id.bi.detp.policy.domain.PolicyDecision\n"
    'rule "probe" when $f : PolicyFact() $d : PolicyDecision() then end\n'
)
print(json.dumps({"name": "probe_rule", "drlContent": drl, "authorId": author}))
PY
)
DRAFT=$(curl -sf -X POST "$POLICY_URL/api/v1/rules" -H 'Content-Type: application/json' -d "$PROBE")
PROBE_ID=$(echo "$DRAFT" | python3 -c "import sys,json; print(json.load(sys.stdin)['id'])")
curl -sf -X PATCH "$POLICY_URL/api/v1/rules/$PROBE_ID" \
  -H 'Content-Type: application/json' \
  -d "{\"action\":\"SUBMIT_TEST\",\"actorId\":\"$AUTHOR\"}" >/dev/null
HTTP=$(curl -s -o /dev/null -w '%{http_code}' -X PATCH "$POLICY_URL/api/v1/rules/$PROBE_ID" \
  -H 'Content-Type: application/json' \
  -d "{\"action\":\"APPROVE\",\"actorId\":\"$AUTHOR\"}")
[ "$HTTP" = "403" ] || { echo "FAIL: expected 403 for self-approve, got $HTTP"; exit 1; }

echo "PASS: demo-limit-change — Rp 600M DENY→ALLOW without restart; self-approve blocked"
