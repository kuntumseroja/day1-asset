#!/bin/bash
# EC2 user-data for Ubuntu 22.04 — paste into "Advanced → User data" at launch.
# Set PUBLIC_URL via EC2 instance tag or edit REPO_URL / PUBLIC_URL below.
set -euo pipefail

exec > /var/log/detp-user-data.log 2>&1
echo "=== D-ETP user-data start $(date -Is) ==="

REPO_URL="${REPO_URL:-https://github.com/kuntumseroja/day1-asset.git}"
REPO_BRANCH="${REPO_BRANCH:-main}"
INSTALL_DIR="/home/ubuntu/day1-asset"

# --- packages ---
export DEBIAN_FRONTEND=noninteractive
apt-get update -y
apt-get install -y ca-certificates curl git nginx

# --- docker ---
curl -fsSL https://get.docker.com | sh
usermod -aG docker ubuntu

# --- node 20 ---
curl -fsSL https://deb.nodesource.com/setup_20.x | bash -
apt-get install -y nodejs

# --- clone ---
sudo -u ubuntu git clone --branch "$REPO_BRANCH" --depth 1 "$REPO_URL" "$INSTALL_DIR" \
  || sudo -u ubuntu git -C "$INSTALL_DIR" pull

# --- env: use Elastic IP if metadata available ---
TOKEN=$(curl -sf -X PUT "http://169.254.169.254/latest/api/token" \
  -H "X-aws-ec2-metadata-token-ttl-seconds: 21600")
PUBLIC_IP=$(curl -sf -H "X-aws-ec2-metadata-token: $TOKEN" \
  http://169.254.169.254/latest/meta-data/public-ipv4)

cat > "$INSTALL_DIR/.env" <<EOF
POSTGRES_PASSWORD=$(openssl rand -hex 16)
KEYCLOAK_ADMIN_PASSWORD=$(openssl rand -hex 12)
PACT_BROKER_BASIC_AUTH_PASSWORD=$(openssl rand -hex 12)
PUBLIC_URL=http://${PUBLIC_IP}
REPO_URL=$REPO_URL
REPO_BRANCH=$REPO_BRANCH
EOF
chown ubuntu:ubuntu "$INSTALL_DIR/.env"
chmod 600 "$INSTALL_DIR/.env"

# --- swap (helps Maven Docker builds on t3.xlarge) ---
if ! swapon --show | grep -q /swapfile; then
  fallocate -l 8G /swapfile
  chmod 600 /swapfile
  mkswap /swapfile
  swapon /swapfile
  echo '/swapfile none swap sw 0 0' >> /etc/fstab
fi

# --- deploy ---
chmod +x "$INSTALL_DIR/deploy/aws/deploy.sh"
sudo -u ubuntu bash -lc "cd $INSTALL_DIR && ./deploy/aws/deploy.sh"

echo "=== D-ETP user-data done $(date -Is) ==="
