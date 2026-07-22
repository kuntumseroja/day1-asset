#!/bin/bash
# EC2 user-data for Ubuntu — dedicated FireFly + Besu node (EC2 B).
# Paste into Launch instance → Advanced → User data.
# First boot takes 15–30 minutes (Docker images + ff init).
set -euo pipefail

exec > /var/log/detp-besu-user-data.log 2>&1
echo "=== D-ETP Besu user-data start $(date -Is) ==="

REPO_URL="${REPO_URL:-https://github.com/kuntumseroja/day1-asset.git}"
REPO_BRANCH="${REPO_BRANCH:-main}"
INSTALL_DIR="/home/ubuntu/day1-asset"
FF_STACK="${FF_STACK_NAME:-detp-besu}"

export DEBIAN_FRONTEND=noninteractive
apt-get update -y
apt-get install -y ca-certificates curl git golang-go

curl -fsSL https://get.docker.com | sh
usermod -aG docker ubuntu

# swap — helps ff start on t3.large
if ! swapon --show | grep -q /swapfile; then
  fallocate -l 4G /swapfile
  chmod 600 /swapfile
  mkswap /swapfile
  swapon /swapfile
  echo '/swapfile none swap sw 0 0' >> /etc/fstab
fi

export GOPATH=/home/ubuntu/go
export PATH="$PATH:$GOPATH/bin"
mkdir -p "$GOPATH"
chown -R ubuntu:ubuntu "$GOPATH"

sudo -u ubuntu bash -lc "go install github.com/hyperledger/firefly-cli/ff@latest"

sudo -u ubuntu git clone --branch "$REPO_BRANCH" --depth 1 "$REPO_URL" "$INSTALL_DIR" \
  || sudo -u ubuntu git -C "$INSTALL_DIR" pull

chmod +x "$INSTALL_DIR"/scripts/dlt-firefly-*.sh

sudo -u ubuntu bash -lc "
  export PATH=\"\$PATH:\$(go env GOPATH)/bin\"
  cd $INSTALL_DIR
  if ! ff list 2>/dev/null | grep -q '$FF_STACK'; then
    ./scripts/dlt-firefly-init.sh
  fi
  ./scripts/dlt-firefly-start.sh
"

echo "=== FireFly status ==="
curl -sf http://localhost:5000/api/v1/status | head -c 300 || true
echo ""
echo "=== D-ETP Besu user-data done $(date -Is) ==="
echo "Wire EC2 A: ./scripts/dlt-wire-remote.sh <this-instance-private-ip> --apply"
