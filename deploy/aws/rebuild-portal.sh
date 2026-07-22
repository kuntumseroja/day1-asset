#!/usr/bin/env bash
# Rebuild and publish portal static files only (no docker compose rebuild).
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
cd "$REPO_ROOT"

if [[ -f .env ]]; then
  set -a
  # shellcheck disable=SC1091
  source .env
  set +a
fi

PUBLIC_URL="${PUBLIC_URL:-http://localhost}"
PUBLIC_URL="${PUBLIC_URL%/}"

echo "=== Rebuild portal (same-origin /api/v1) ==="
cd "$REPO_ROOT/portal"
export VITE_API_BASE="/api/v1"
export VITE_RECON_BASE="/recon/api/v1"
unset VITE_WS_URL

npm ci --silent
npm run build

sudo mkdir -p /var/www/detp-portal
sudo rm -rf /var/www/detp-portal/*
sudo cp -r dist/* /var/www/detp-portal/
sudo cp "$SCRIPT_DIR/nginx/detp.conf" /etc/nginx/sites-available/detp
sudo ln -sf /etc/nginx/sites-available/detp /etc/nginx/sites-enabled/default
sudo nginx -t
sudo systemctl reload nginx

echo "Done. Portal: ${PUBLIC_URL} (hard-refresh browser: Cmd/Ctrl+Shift+R)"
echo "API check: curl -sf http://localhost:8093/health && curl -sf http://localhost/api/v1/limits | head -c 120"
