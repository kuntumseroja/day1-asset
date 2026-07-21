# KC-01 · Root / Issuer Key Generation Ceremony

## Purpose

Generate the WRD issuer signing key pair inside a FIPS 140-2 Level 3 HSM under dual control, distribute Shamir 3-of-5 backup shares to geographically separated custodians, verify restore capability, and produce a BC-12 evidence pack. The resulting public key becomes the FireFly/Besu signing identity (Asset 6).

## Roles

| Role | Name (fill at ceremony) | Responsibilities |
|------|-------------------------|------------------|
| Ceremony Master | __________________ | Owns script adherence, time-boxing, abort decisions |
| Key Custodian A | __________________ | Dual-control credential #1; receives Shamir share 1 |
| Key Custodian B | __________________ | Dual-control credential #2; receives Shamir share 2 |
| Security Officer | __________________ | HSM SO PIN holder; firmware attestation sign-off |
| Witness | __________________ | Independent observer; no key material access |
| Scribe | __________________ | Records step evidence, timestamps, paraf initials |

## Prerequisites

- Scheduled ceremony window with all roles physically or video-present
- HSM powered, tamper seals intact; firmware version matches approved baseline (record in evidence)
- Tamper / audit log reviewed in last 24 h with no unexplained events
- Sealed envelope inventory reconciled; blank Shamir share envelopes (×5) pre-numbered
- PKCS#11 slot empty or pre-approved for new token label `wrd-ceremony`
- Runbook KC-01 printed; abort contact tree posted
- Dry-run scripts 00–05 available on isolated ceremony workstation (no network)

## Abort Criteria

Stop immediately, log incident reference, and reschedule if **any** of the following occur:

- HSM returns unexpected status, PIN lockout, or tamper alarm
- Generated public key fingerprint differs from scribe pre-read baseline (when rotating)
- Shamir combine test fails or restored public key ≠ original
- Test signature verification fails
- Any step deviates from this script (no improvisation)
- Dual-control witness unavailable for a sensitive step
- Evidence capture (photo / audit extract) cannot be completed for a completed step

---

## Ceremony Steps

| No | Aktor | Perintah / Aksi | Output diharapkan | Bukti | Paraf 1 | Paraf 2 |
|----|-------|-----------------|-------------------|-------|---------|---------|
| 1 | Security Officer | Power-on HSM; Security Officer attests firmware hash and tamper-log review | HSM ready; attestation form signed | Photo of HSM display + signed attestation | | |
| 2 | Ceremony Master | Call roles to order; scribe records start UTC timestamp | Attendance sheet complete | Signed attendance sheet | | |
| 3 | Security Officer + Custodian A | Init token via PKCS#11 (`00_init_softhsm.sh` / HSM console equivalent); set distinct SO and User PINs | Token `wrd-ceremony` initialized; PINs split per role | HSM audit log extract (init event) | | |
| 4 | Custodian A + Custodian B | Dual-control login; generate EC secp256k1 keypair label `wrd-issuer-1` (`01_generate_keypair.sh`) | Key handle created; public key exported | Public key PEM + fingerprint on scribe sheet | | |
| 5 | Ceremony Master | Scribe records public key fingerprint; witness confirms match to pre-ceremony envelope (if applicable) | Fingerprint logged twice independently | Fingerprint worksheet | | |
| 6 | Custodian A + Custodian B | Export wrapped key material; run Shamir 3-of-5 split (`02_backup_shamir.sh`) | Five sealed shares; threshold documented | Photo of sealed envelopes + share index | | |
| 7 | Custodian A + B + Witness | Custodians place shares 1–2 in assigned vaults; shares 3–5 to pre-agreed off-site custodians | Chain-of-custody forms signed | Custody forms + envelope photos | | |
| 8 | Custodian A + Custodian B | DR verification: combine any 3 shares, restore, verify pubkey identical (`03_restore_from_shares.sh`) | Restored pubkey matches step 4 | Combine log + pubkey diff = none | | |
| 9 | Custodian A + Custodian B | Sign test mint payload; verify signature offline (`04_sign_test_mint.sh`) | Valid ECDSA signature over test payload | Signature hex + verification OK | | |
| 10 | Scribe + Ceremony Master | Assemble evidence pack (`05_evidence_pack.sh`); checksum manifest | `evidence/KC-01_<date>/` with manifest + PDF | manifest.json SHA-256 on scribe sheet | | |
| 11 | Ceremony Master | Read abort criteria reminder; declare ceremony complete or aborted | Close-out statement signed | Signed ceremony close-out | | |

---

## Post-Ceremony

- Archive evidence pack to BC-12 within 24 h
- Register public key with key-manager (FireFly/Besu) per Asset 6 runbook
- Securely destroy ceremony workstation ephemeral PIN notes
- Schedule KC-02 rotation date per key lifecycle policy (default: 12 months)
