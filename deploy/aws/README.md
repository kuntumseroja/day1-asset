# AWS Indonesia (Jakarta) — One-Command Deploy

Deploy the full D-ETP Day-One Assets stack on a single **EC2** instance in **`ap-southeast-3`** (Jakarta).

## Architecture

```text
Internet :80
    │
    nginx (host)
    ├── /              → portal static (React)
    ├── /api/v1/*      → portal-sim :8093
    ├── /ws            → portal-sim WebSocket
    └── /recon/api/v1/* → recon :8085

docker compose (localhost only)
    postgres, redpanda, temporal, keycloak, policy, saga-lib, recon, simulators, ...
```

Portal build uses **same-origin** URLs via nginx — no CORS, no hardcoded `localhost`.

## Prerequisites

| Item | Value |
|------|--------|
| AWS region | `ap-southeast-3` (Jakarta) |
| Instance | **t3.xlarge** (16 GB RAM) minimum |
| AMI | Ubuntu 22.04 LTS |
| Storage | 80 GB gp3 |
| Security group | 22 (your IP), 80, 443 (optional) |

**Do not** expose 5432, 9092, or 7233 to the internet.

## Option A — Fully automated (user-data)

### 1. Launch EC2

1. Region → **Asia Pacific (Jakarta)**
2. Launch **Ubuntu 22.04**, **t3.xlarge**, 80 GB
3. Security group: allow **22** (your IP), **80** (0.0.0.0/0)
4. Advanced → **User data** → paste contents of [`user-data.sh`](user-data.sh)
5. Associate an **Elastic IP**

### 2. Wait ~15–25 minutes

Monitor progress:

```bash
ssh ubuntu@YOUR_ELASTIC_IP
sudo tail -f /var/log/detp-user-data.log
```

### 3. Open portal

```text
http://YOUR_ELASTIC_IP
```

Login → Dev bypass (Bank A).

---

## Option B — Manual (recommended for first time)

### 1. Launch EC2 + Elastic IP (same as above, **no user-data**)

### 2. SSH and install

```bash
ssh -i your-key.pem ubuntu@YOUR_ELASTIC_IP

sudo apt-get update
curl -fsSL https://get.docker.com | sudo sh
sudo usermod -aG docker ubuntu

curl -fsSL https://deb.nodesource.com/setup_20.x | sudo -E bash -
sudo apt-get install -y nodejs nginx git

# log out and back in
exit
```

### 3. Clone and configure

```bash
ssh ubuntu@YOUR_ELASTIC_IP

git clone https://github.com/kuntumseroja/day1-asset.git
cd day1-asset

cp deploy/aws/.env.example .env
# Edit .env — set PUBLIC_URL to your Elastic IP:
nano .env
# PUBLIC_URL=http://13.212.xxx.xxx
```

### 4. One-command deploy

```bash
chmod +x deploy/aws/deploy.sh
./deploy/aws/deploy.sh
```

---

## Environment variables

### Root `.env` (docker + deploy script)

| Variable | Purpose |
|----------|---------|
| `PUBLIC_URL` | Elastic IP or domain (used for portal build) |
| `POSTGRES_PASSWORD` | PostgreSQL password |
| `KEYCLOAK_ADMIN_PASSWORD` | Keycloak admin |
| `PACT_BROKER_BASIC_AUTH_PASSWORD` | Pact broker |

### Portal `VITE_*` (set automatically by `deploy.sh`)

| Variable | Example (nginx same-origin) |
|----------|----------------------------|
| `VITE_API_BASE` | `http://13.212.xxx.xxx/api/v1` |
| `VITE_RECON_BASE` | `http://13.212.xxx.xxx/recon/api/v1` |
| `VITE_WS_URL` | `ws://13.212.xxx.xxx/ws` |

Local dev: copy [`portal/.env.example`](../../portal/.env.example) to `portal/.env`.

---

## TLS (optional)

If you have a domain pointed at the Elastic IP:

```bash
sudo apt-get install -y certbot python3-certbot-nginx
sudo certbot --nginx -d detp.yourdomain.com
```

Then update `.env`:

```bash
PUBLIC_URL=https://detp.yourdomain.com
./deploy/aws/deploy.sh   # rebuilds portal with https/wss
```

---

## Verify

```bash
curl -sf http://localhost:8091/health   # rtgs-sim
curl -sf http://localhost:8093/health     # portal-sim
curl -sf http://localhost:8084/actuator/health  # policy

cd ~/day1-asset
make health
make demos
```

---

## Cost tips

| Tip | Saving |
|-----|--------|
| Use **Spot** t3.xlarge | ~60% vs on-demand |
| Stop EC2 when idle | Pay only storage |
| Skip exposing Keycloak/Temporal UI | Smaller attack surface |

---

## Troubleshooting

| Issue | Fix |
|-------|-----|
| User-data still running | `tail -f /var/log/detp-user-data.log` |
| OOM during build | Ensure 16 GB RAM or swap enabled (user-data adds 8 GB swap) |
| Portal blank / API errors | Re-run `./deploy/aws/deploy.sh` after fixing `PUBLIC_URL` |
| WebSocket disconnects | Check nginx `/ws` proxy; use `wss://` with HTTPS |

---

## Files

| File | Purpose |
|------|---------|
| [`user-data.sh`](user-data.sh) | EC2 launch script (full bootstrap) |
| [`deploy.sh`](deploy.sh) | Compose + portal build + nginx |
| [`nginx/detp.conf`](nginx/detp.conf) | Reverse proxy config |
| [`.env.example`](.env.example) | Production env template |
