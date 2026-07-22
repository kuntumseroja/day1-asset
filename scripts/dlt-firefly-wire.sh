#!/usr/bin/env bash
# Print environment variables to point saga-lib / firefly-kit / recon at a real FireFly stack.
set -euo pipefail

FIREFLY_HTTP="${FIREFLY_URL:-http://localhost:5000}"
FIREFLY_WS="${FIREFLY_WS_URL:-ws://localhost:5000/ws}"

cat <<EOF
# --- Wire D-ETP services to real FireFly (Besu) ---
# Run on host for local Java processes:
export FIREFLY_URL=$FIREFLY_HTTP
export FIREFLY_WS_URL=$FIREFLY_WS
export RECON_FIREFLY_URL=$FIREFLY_HTTP

# Or use docker compose overlay (Linux / Docker 20.10+):
#   docker compose -f docker-compose.yml -f docker-compose.dlt.yml up -d saga-lib firefly-kit recon

# Verify FireFly:
curl -sf $FIREFLY_HTTP/api/v1/status | head -c 200 && echo

# Then re-run DLT demos:
#   make demo-replay
#   make demo-break
EOF
