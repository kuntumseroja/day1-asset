#!/usr/bin/env bash
# Initialize a local Hyperledger FireFly stack with Besu (deploys token + BatchPin contracts).
set -euo pipefail

STACK_NAME="${FF_STACK_NAME:-detp-besu}"

if ! command -v ff >/dev/null 2>&1; then
  echo "FAIL: FireFly CLI (ff) not found."
  echo "Install: go install github.com/hyperledger/firefly-cli/ff@latest"
  echo "Docs:    https://hyperledger.github.io/firefly/latest/gettingstarted/setup_env/"
  exit 1
fi

if ff list 2>/dev/null | grep -q "$STACK_NAME"; then
  echo "Stack '$STACK_NAME' already exists. Use: ff start $STACK_NAME"
  echo "To recreate: ff remove $STACK_NAME && re-run this script"
  exit 0
fi

echo "=== Initializing FireFly stack '$STACK_NAME' with Besu ==="
echo "Requires Docker with ~4 GB RAM free."

ff init ethereum "$STACK_NAME" \
  --blockchain-node besu \
  --blockchain-connector evmconnect \
  --org-name D-ETP-OMNIBUS \
  --token-providers erc20_erc721

echo ""
echo "OK: stack initialized. Start with:"
echo "  ./scripts/dlt-firefly-start.sh"
echo "  or: ff start $STACK_NAME"
