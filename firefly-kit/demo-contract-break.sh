#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"

echo "=== FireFly demo-contract-break: Pact provider verification ==="

cd "$ROOT/firefly-kit"
if mvn -q test -Dtest=FireflyProviderContractTest 2>/dev/null; then
  echo "Provider verification passed (expected to fail when schema broken)"
  echo "To simulate break: modify firefly-stub event schema and re-run"
  exit 0
else
  echo "PASS: provider verification failed as expected when contract broken"
  exit 0
fi
