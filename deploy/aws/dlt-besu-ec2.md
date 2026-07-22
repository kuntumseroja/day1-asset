# Separate EC2 for FireFly + Besu (DLT node)

Run a **real Hyperledger Besu + FireFly stack** on a **second EC2** in the same VPC as your main D-ETP app instance. The app stack (EC2 A) keeps portal, saga, policy, recon, etc.; the DLT node (EC2 B) runs only the `ff` CLI stack.

**Related:** [`docs/DLT-DEMO.md`](../../docs/DLT-DEMO.md) · [`docker-compose.dlt.yml`](../../docker-compose.dlt.yml)

---

## Topology

```text
                    VPC (ap-southeast-3)
┌─────────────────────────────────────────────────────────────┐
│  EC2 A — detp-app (existing)          EC2 B — detp-besu     │
│  t3.xlarge · Elastic IP · nginx       t3.large/xlarge       │
│                                                             │
│  portal, saga-lib, firefly-kit ──TCP 5000──► FireFly Core   │
│  recon, policy, rtgs-sim, postgres         ff + Besu QBFT   │
│  firefly-stub STOPPED                      private IP only  │
└─────────────────────────────────────────────────────────────┘
```

| Instance | Name tag | Role | Suggested type |
|----------|----------|------|----------------|
| **EC2 A** | `detp-app` | Full day-one stack | `t3.xlarge` (16 GiB) |
| **EC2 B** | `detp-besu` | FireFly + Besu only | **`t3.large`** min, **`t3.xlarge`** safer |

Do **not** attach a public Elastic IP to EC2 B unless you have a strong reason. Use **private IP** from EC2 A.

---

## Phase 1 — Launch EC2 B (Besu node)

### 1. Security groups

Create **`detp-besu-sg`**:

| Type | Port | Source | Notes |
|------|------|--------|-------|
| SSH | 22 | Your IP / EC2 Instance Connect | Admin only |
| Custom TCP | **5000** | **`detp-app-sg`** (EC2 A's SG) | FireFly REST + WS |
| — | — | — | **Do not** open 5000 to `0.0.0.0/0` |

EC2 A's SG does **not** need inbound from EC2 B (one-way: app → FireFly).

### 2. Launch instance

| Setting | Value |
|---------|--------|
| Region | **ap-southeast-3** (same as EC2 A) |
| AMI | Ubuntu Server **24.04 LTS** x86 |
| Type | **t3.large** (8 GiB) or **t3.xlarge** |
| Disk | **30 GB gp3** minimum (**50 GB** recommended) |
| VPC / subnet | **Same VPC** as EC2 A |
| Public IP | Optional (SSH only); FireFly stays private |
| Security group | `detp-besu-sg` |
| Key pair | Same `.pem` as EC2 A |

**Optional bootstrap:** paste [`dlt-besu-user-data.sh`](dlt-besu-user-data.sh) into **Advanced → User data**. First boot installs Docker, Go, `ff`, initializes stack `detp-besu`, and starts it (15–30 min).

### 3. Note private IP

EC2 → **detp-besu** → **Private IPv4 address** (e.g. `10.0.2.87`). You will use this on EC2 A as `FIREFLY_HOST`.

### 4. Manual setup (if not using user-data)

```bash
ssh -i your-key.pem ubuntu@<besu-public-or-bastion-ip>

sudo apt-get update && sudo apt-get install -y docker.io docker-compose-plugin golang-go git
sudo usermod -aG docker ubuntu
# re-login or: newgrp docker

export PATH="$PATH:$(go env GOPATH)/bin"
go install github.com/hyperledger/firefly-cli/ff@latest

git clone https://github.com/kuntumseroja/day1-asset.git ~/day1-asset
cd ~/day1-asset
make dlt-init
make dlt-start   # first run: long image pull

curl -sf http://localhost:5000/api/v1/status | head
ff list
```

### 5. Swap (recommended on t3.large)

```bash
sudo fallocate -l 4G /swapfile
sudo chmod 600 /swapfile
sudo mkswap /swapfile
sudo swapon /swapfile
echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab
```

---

## Phase 2 — Wire EC2 A (app stack) to EC2 B

SSH to **EC2 A** (main instance):

```bash
cd ~/day1-asset
git pull

# EC2 B private IP from Phase 1
export BESU_PRIVATE_IP=10.0.2.87

# Test connectivity before changing compose
curl -sf "http://${BESU_PRIVATE_IP}:5000/api/v1/status" | head

# Stop stub so nothing hits the in-memory fake chain
docker compose stop firefly-stub

# Wire saga, firefly-kit, recon to remote FireFly
export FIREFLY_HOST="http://${BESU_PRIVATE_IP}:5000"
export FIREFLY_WS_HOST="ws://${BESU_PRIVATE_IP}:5000/ws"

docker compose -f docker-compose.yml -f docker-compose.dlt.yml up -d \
  saga-lib firefly-kit recon

# Or use helper script:
./scripts/dlt-wire-remote.sh "$BESU_PRIVATE_IP" --apply
```

Verify from inside a container:

```bash
docker compose exec saga-lib curl -sf "http://${BESU_PRIVATE_IP}:5000/api/v1/status" | head
```

### Persist env on EC2 A (optional)

Append to `~/day1-asset/.env`:

```bash
FIREFLY_HOST=http://10.0.2.87:5000
FIREFLY_WS_HOST=ws://10.0.2.87:5000/ws
```

Then recreate affected services:

```bash
docker compose -f docker-compose.yml -f docker-compose.dlt.yml up -d saga-lib firefly-kit recon
```

---

## Phase 3 — Run DLT demos

On **EC2 A**, CLI demos must target real FireFly (not stub port 8092):

```bash
export FIREFLY_URL="http://${BESU_PRIVATE_IP}:5000"
export FIREFLY_WS_URL="ws://${BESU_PRIVATE_IP}:5000/ws"

make demo-replay
make demo-break
make demo-duplicate   # saga mint path uses FIREFLY_URL via saga-lib container env
```

Portal flows (issuance → scripted day → recon) work through the wired containers; no portal rebuild needed.

---

## Operations

| Task | Command (EC2 B) |
|------|-----------------|
| Start stack | `ff start detp-besu` or `make dlt-start` |
| Stop stack | `ff stop detp-besu` |
| Logs | `ff logs detp-besu --follow` |
| Remove stack | `ff remove detp-besu` (destructive) |
| Stack data | `~/.firefly/stacks/detp-besu/` |

| Task | Command (EC2 A) |
|------|-----------------|
| Revert to stub | `docker compose up -d firefly-stub` and `docker compose up -d saga-lib firefly-kit recon` (without dlt overlay) |
| Cost save | **Stop EC2 B** when not demoing; app stack keeps running on stub if you revert |

---

## Troubleshooting

| Symptom | Fix |
|---------|-----|
| `curl` to `:5000` times out from EC2 A | Check EC2 B SG allows **5000 from EC2 A SG**; same VPC |
| `ff start` OOM | Resize to `t3.xlarge` or add swap |
| Saga mint fails | Token pool `wRD` must exist on FireFly; check saga logs for `FIREFLY_URL` |
| Demos still hit stub | Export `FIREFLY_URL` to EC2 B IP before `make demo-*` |
| WS replay fails | Set `FIREFLY_WS_HOST=ws://<private-ip>:5000/ws` and recreate `firefly-kit` |

---

## Cost estimate — EC2 B only (Jakarta, on-demand)

Rough **USD/month** (730 h), excluding EC2 A:

| Type | ~$/hr | ~$/month |
|------|-------|----------|
| t3.large | 0.09 | **~65** |
| t3.xlarge | 0.17 | **~125** |
| EBS 50 GB gp3 | — | **~5** |

**Stop instance when idle** → pay only EBS (~$5/mo). Spot can cut compute ~60% but is less stable for demos.

Compare to **Kaleido managed Besu + FireFly**: see [Kaleido cost section in DLT-DEMO.md](../../docs/DLT-DEMO.md#kaleido-cost-besu--firefly).

---

## Production path

This two-EC2 layout mirrors production: **app tier** calls **FireFly API** over the network; validators sit behind FireFly. For regulated production, replace EC2 B with **Kaleido-managed Besu QBFT + FireFly Enterprise** (mTLS, SOC2, multi-region) — same `FIREFLY_URL` seam on EC2 A or EKS.
