# AWS Indonesia (Jakarta) — Deployment Guide

Deploy D-ETP Day-One Assets on a single EC2 in **`ap-southeast-3` (Jakarta)**.

**Repo:** https://github.com/kuntumseroja/day1-asset.git

---

## Before you start

| Requirement | Value |
|-------------|--------|
| AWS region | **Asia Pacific (Jakarta) `ap-southeast-3`** |
| Instance (paid) | **t3.xlarge** — 4 vCPU, 16 GiB RAM (recommended) |
| Instance (free tier max) | **m7i-flex.large** — 2 vCPU, 8 GiB RAM (new accounts only, see below) |
| AMI | **Ubuntu Server 24.04 LTS**, 64-bit **(x86)** — not Arm, not Ubuntu Pro |
| Disk | **50 GB gp3** (30 GB minimum) |
| Key pair | RSA, `.pem` format |
| Elastic IP | **Required** — attach before you rely on SSH |

**Do not expose to the internet:** PostgreSQL (5432), Kafka (9092), Temporal (7233).

---

## Free tier note

| Account created | Max free EC2 size |
|-----------------|-------------------|
| **On/after 15 Jul 2025** | `m7i-flex.large` (8 GiB) — largest free-tier eligible |
| **Before 15 Jul 2025** | `t3.micro` (1 GiB) only — **too small** for full stack |

Check: **Billing → Free Tier**. Full stack on 8 GiB is tight — add swap (Phase 4) or use **t3.xlarge**.

---

## Architecture

```text
Browser → http://YOUR_ELASTIC_IP:80
              │
         nginx (on EC2 host)
              ├── /              → React portal (static)
              ├── /api/v1/*      → portal-sim :8093
              ├── /ws            → WebSocket
              └── /recon/api/v1/* → recon :8085

docker compose (127.0.0.1 / internal only)
    postgres, redpanda, temporal, policy, saga-lib, recon, simulators, ...
```

---

## Deployment paths

| Path | Best for |
|------|----------|
| **[Manual (Phases 0–7)](#manual-deployment-phases-0–7)** | First time — verify each step |
| **[Automated (user-data)](#automated-deployment)** | Repeatable full bootstrap |

---

## Manual deployment (Phases 0–7)

### Phase 0 — AWS account & region

1. Sign in to [AWS Console](https://console.aws.amazon.com).
2. Top-right → set region to **Asia Pacific (Jakarta) ap-southeast-3**.
3. (Optional) Note your **Account ID**: click account name (top-right) → 12-digit ID.

**Checkpoint:** Region shows `ap-southeast-3`.

---

### Phase 1 — Create key pair

1. **EC2 → Network & Security → Key pairs**
2. **Create key pair**
   - Name: `detp-jakarta-key`
   - Type: **RSA**
   - Format: **.pem**
3. Save the downloaded file, e.g. `~/awskey/detp-jakarta-key.pem`
4. Fix permissions (Mac/Linux):

```bash
chmod 400 ~/awskey/detp-jakarta-key.pem
```

**Checkpoint:** Key pair visible in list (Jakarta region).

---

### Phase 2 — Security group

1. **EC2 → Security Groups → Create**
2. Name: `detp-demo-sg`
3. Inbound rules:

| Type | Port | Source | Purpose |
|------|------|--------|---------|
| SSH | 22 | **My IP** | Admin (update if your IP changes) |
| HTTP | 80 | 0.0.0.0/0 | Portal |
| HTTPS | 443 | 0.0.0.0/0 | Portal TLS (optional) |

**Do not** add 5432, 9092, or 7233.

**Checkpoint:** Security group created in Jakarta.

---

### Phase 3 — Launch EC2

1. **EC2 → Launch instance**
2. Settings:

| Field | Value |
|-------|--------|
| Name | `detp-dayone-demo` |
| AMI | **Ubuntu Server 24.04 LTS (HVM), SSD** — **64-bit (x86)** |
| Instance type | **m7i-flex.large** (free) or **t3.xlarge** (recommended) |
| Key pair | `detp-jakarta-key` |
| Security group | `detp-demo-sg` |
| Storage | **50 GB gp3** |

3. **Do not** paste user-data for manual path.
4. Launch instance.
5. Wait until **Instance state = running** and **Status check = 2/2 passed**.

**Checkpoint:** Instance running in Jakarta.

---

### Phase 4 — Elastic IP (do this before SSH breaks)

1. **EC2 → Elastic IPs → Allocate**
2. **Actions → Associate** → select `detp-dayone-demo`
3. Copy the Elastic IP, e.g. `3.24.xxx.xxx`

> Without Elastic IP, stop/start changes your public IP and SSH will fail.

**Checkpoint:** Elastic IP associated; note the address.

---

### Phase 5 — Connect & install base software

From your Mac (use Elastic IP):

```bash
cd ~/awskey
ssh -i detp-jakarta-key.pem ubuntu@YOUR_ELASTIC_IP
```

If **Operation timed out**:

1. EC2 → instance → confirm **running**, **2/2 checks**
2. Security group → SSH 22 → set source to **My IP** again
3. Confirm you use **Elastic IP**, not an old address
4. Try **EC2 → Connect → EC2 Instance Connect** (browser)
5. If hung: **Actions → Reboot**

On the server:

```bash
# System update
sudo apt-get update -y
sudo apt-get upgrade -y

# Docker
curl -fsSL https://get.docker.com | sudo sh
sudo usermod -aG docker ubuntu

# Node 20 + nginx + git
curl -fsSL https://deb.nodesource.com/setup_20.x | sudo -E bash -
sudo apt-get install -y nodejs nginx git

# Swap (important on 8 GiB free tier)
sudo fallocate -l 8G /swapfile
sudo chmod 600 /swapfile
sudo mkswap /swapfile
sudo swapon /swapfile
echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab

# Verify
docker --version
node --version
nginx -v
free -h
```

**Log out and back in** so docker group applies:

```bash
exit
ssh -i detp-jakarta-key.pem ubuntu@YOUR_ELASTIC_IP
docker ps   # should work without sudo
```

**Checkpoint:** `docker ps`, `node -v`, `free -h` show swap ~8G.

---

### Phase 6 — Clone repo & configure

```bash
git clone https://github.com/kuntumseroja/day1-asset.git
cd day1-asset

cp deploy/aws/.env.example .env
nano .env
```

Set in `.env`:

```bash
PUBLIC_URL=http://YOUR_ELASTIC_IP
POSTGRES_PASSWORD=<strong-random-password>
KEYCLOAK_ADMIN_PASSWORD=<strong-random-password>
PACT_BROKER_BASIC_AUTH_PASSWORD=<strong-random-password>
```

Save and exit.

**Checkpoint:** `cat .env | grep PUBLIC_URL` shows your Elastic IP.

---

### Phase 7 — Deploy application

```bash
chmod +x deploy/aws/deploy.sh
./deploy/aws/deploy.sh
```

This runs (~15–25 min first time):

1. `docker compose up -d --build`
2. Portal build with `VITE_*` URLs
3. nginx reverse proxy on port 80
4. systemd auto-start on boot

**Checkpoint:**

```bash
curl -sf http://localhost:8093/health && echo " portal-sim OK"
curl -sf http://localhost:8084/actuator/health && echo " policy OK"
docker compose ps
```

Open in browser:

```text
http://YOUR_ELASTIC_IP
```

Login → **Dev bypass (Bank A)** → Issuance / Queue / Limits.

---

## Automated deployment

For hands-off bootstrap, paste [`user-data.sh`](user-data.sh) into **Launch instance → Advanced → User data**.

Still complete **Phases 1–4** (key pair, security group, launch, **Elastic IP**).

Monitor:

```bash
ssh -i detp-jakarta-key.pem ubuntu@YOUR_ELASTIC_IP
sudo tail -f /var/log/detp-user-data.log
```

When log shows `Deploy complete`, open `http://YOUR_ELASTIC_IP`.

---

## Verify & demos

```bash
cd ~/day1-asset
make health
make demos
```

| Demo | Command |
|------|---------|
| Saga duplicate UETR | `make demo-duplicate` |
| FireFly replay | `make demo-replay` |
| Policy limit change | `make demo-limit-change` |
| Recon break case | `make demo-break` |
| HSM ceremony | `make dry-run` |

Full stakeholder walkthrough for FireFly, Besu, smart contracts, Kaleido, and Paladin: [`docs/DLT-DEMO.md`](../../docs/DLT-DEMO.md).

Optional local Besu stack (developer machine):

```bash
make dlt-init && make dlt-start
make dlt-wire   # print FIREFLY_URL exports
docker compose -f docker-compose.yml -f docker-compose.dlt.yml up -d saga-lib firefly-kit recon
```

---

## Environment variables

### Root `.env`

| Variable | Example |
|----------|---------|
| `PUBLIC_URL` | `http://3.24.xxx.xxx` |
| `POSTGRES_PASSWORD` | random string |
| `KEYCLOAK_ADMIN_PASSWORD` | random string |

### Portal (set automatically by `deploy.sh`)

| Variable | Value |
|----------|--------|
| `VITE_API_BASE` | `/api/v1` (same-origin — do **not** bake `localhost` or Elastic IP into JS) |
| `VITE_RECON_BASE` | `/recon/api/v1` |
| `VITE_WS_URL` | *(unset — resolved at runtime from `window.location`)* |

Quick portal-only rebuild after UI changes:

```bash
./deploy/aws/rebuild-portal.sh
```

---

## TLS (optional)

Point a domain A-record to your Elastic IP:

```bash
sudo apt-get install -y certbot python3-certbot-nginx
sudo certbot --nginx -d detp.yourdomain.com
```

Update `.env`:

```bash
PUBLIC_URL=https://detp.yourdomain.com
./deploy/aws/deploy.sh
```

---

## Troubleshooting

### SSH: `Operation timed out`

| Check | Action |
|-------|--------|
| Wrong IP | Use **Elastic IP** from EC2 console |
| IP changed | Security group → SSH → **My IP** |
| Instance stopped | Start instance |
| Instance hung | Reboot; if still bad → Stop → Start |
| Region wrong | Instance must be in **Jakarta** |

Test from Mac:

```bash
nc -zv -w 5 YOUR_ELASTIC_IP 22
ssh -v -i detp-jakarta-key.pem ubuntu@YOUR_ELASTIC_IP
```

Use **EC2 Instance Connect** if local SSH fails but instance is healthy.

### Lost SSH during Phase 5 (before clone)

Usually **not** the app — fix access first (above), then resume Phase 6. Re-run Phase 5 install commands only if `docker --version` fails.

### Docker build OOM

```bash
free -h
docker compose ps
```

Add swap (Phase 5) or resize to **t3.xlarge**.

### Portal loads but API fails

```bash
# Backend must be up
docker compose ps portal-sim policy recon
curl -sf http://localhost:8093/health && echo " portal-sim OK"

# Rebuild portal with same-origin API paths (fixes "Failed to fetch")
cd ~/day1-asset && git pull
./deploy/aws/rebuild-portal.sh

# Hard-refresh browser (Cmd/Ctrl+Shift+R)
# In DevTools → Network, requests should go to /api/v1/* not localhost:8093
```

### WebSocket not connecting

- Use same host for page and WS (nginx `/ws` path).
- With HTTPS, `deploy.sh` sets `wss://` automatically.

---

## Cost tips

| Tip | Saving |
|-----|--------|
| **m7i-flex.large** free tier (new accounts) | $0 for 6 months / credits |
| **Spot** t3.xlarge | ~60% vs on-demand |
| **Stop EC2** when not demoing | Pay EBS only |
| Skip public Keycloak (8180) / Temporal UI (8088) | Safer + simpler |

---

## Port reference (host)

| Port | Service | Expose publicly? |
|------|---------|------------------|
| 80 | nginx → portal | Yes |
| 8093 | portal-sim (direct) | No — use nginx `/api/v1` |
| 8084 | policy | No |
| 8086 | saga-lib | No |
| 8085 | recon | No (nginx `/recon` only) |
| 8091 | rtgs-sim | No |
| 8092 | firefly-stub | No |

---

## Files in this folder

| File | Purpose |
|------|---------|
| [`user-data.sh`](user-data.sh) | Full automated bootstrap |
| [`deploy.sh`](deploy.sh) | Phase 7 — compose + portal + nginx |
| [`nginx/detp.conf`](nginx/detp.conf) | Reverse proxy |
| [`.env.example`](.env.example) | Env template |

---

## Quick reference — all phases

| Phase | What | Where |
|-------|------|--------|
| 0 | Region Jakarta | AWS Console |
| 1 | Key pair `.pem` | EC2 → Key pairs |
| 2 | Security group | EC2 → Security groups |
| 3 | Launch Ubuntu 24.04 x86 | EC2 → Launch |
| 4 | **Elastic IP** | EC2 → Elastic IPs |
| 5 | SSH + Docker/Node/nginx/swap | Terminal |
| 6 | `git clone` + `.env` | Terminal |
| 7 | `./deploy/aws/deploy.sh` | Terminal |
| ✓ | Open `http://ELASTIC_IP` | Browser |
