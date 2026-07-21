#!/usr/bin/env bash
# Run on EC2 after clone (or via user-data). Idempotent.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
cd "$REPO_ROOT"

# Load env
if [[ -f .env ]]; then
  set -a
  # shellcheck disable=SC1091
  source .env
  set +a
fi

PUBLIC_URL="${PUBLIC_URL:-http://localhost}"
PUBLIC_URL="${PUBLIC_URL%/}"

echo "=== D-ETP AWS deploy ==="
echo "Repo:    $REPO_ROOT"
echo "Public:  $PUBLIC_URL"

# --- Docker stack ---
echo "[1/4] Starting docker compose stack..."
docker compose up -d --build

echo "Waiting for core services..."
for i in $(seq 1 60); do
  if curl -sf http://localhost:8093/health >/dev/null 2>&1 \
     && curl -sf http://localhost:8084/actuator/health >/dev/null 2>&1; then
    echo "Core services healthy."
    break
  fi
  if [[ $i -eq 60 ]]; then
    echo "WARN: Some services may still be starting. Check: docker compose ps"
  fi
  sleep 10
done

# --- Portal build with public URLs (nginx same-origin paths) ---
echo "[2/4] Building portal..."
cd "$REPO_ROOT/portal"
export VITE_API_BASE="${PUBLIC_URL}/api/v1"
export VITE_RECON_BASE="${PUBLIC_URL}/recon/api/v1"
export VITE_WS_URL="${PUBLIC_URL//http:/ws:}/ws"
# Fix wss for https public URLs
if [[ "$PUBLIC_URL" == https://* ]]; then
  export VITE_WS_URL="${PUBLIC_URL/https:/wss:}/ws"
fi

npm ci --silent
npm run build

# --- nginx ---
echo "[3/4] Configuring nginx..."
sudo mkdir -p /var/www/detp-portal
sudo rm -rf /var/www/detp-portal/*
sudo cp -r dist/* /var/www/detp-portal/
sudo cp "$SCRIPT_DIR/nginx/detp.conf" /etc/nginx/sites-available/detp
sudo ln -sf /etc/nginx/sites-available/detp /etc/nginx/sites-enabled/default
sudo nginx -t
sudo systemctl enable nginx
sudo systemctl reload nginx

# --- systemd auto-start ---
echo "[4/4] Enabling compose on boot..."
sudo tee /etc/systemd/system/detp-compose.service >/dev/null <<EOF
[Unit]
Description=D-ETP Day-One Docker Compose
After=docker.service
Requires=docker.service

[Service]
Type=oneshot
RemainAfterExit=yes
WorkingDirectory=$REPO_ROOT
EnvironmentFile=-$REPO_ROOT/.env
ExecStart=/usr/bin/docker compose up -d
ExecStop=/usr/bin/docker compose stop
User=ubuntu

[Install]
WantedBy=multi-user.target
EOF

sudo systemctl daemon-reload
sudo systemctl enable detp-compose

echo ""
echo "=== Deploy complete ==="
echo "Portal:  $PUBLIC_URL"
echo "Health:  curl $PUBLIC_URL/api/v1/limits  (after login token)"
echo "Stack:   docker compose ps"
echo "Demos:   cd $REPO_ROOT && make demos"
