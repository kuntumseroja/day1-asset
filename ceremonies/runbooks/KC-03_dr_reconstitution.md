# KC-03 · Disaster Recovery Key Reconstitution Ceremony

## Purpose

Reconstitute the WRD issuer signing key from Shamir shares after HSM loss, site failure, or custodian unavailability, onto replacement HSM hardware. Verify functional equivalence (same public key) before resuming signing operations. Produce DR evidence pack for BC-12 and regulator notification if required.

## Roles

| Role | Name (fill at ceremony) | Responsibilities |
|------|-------------------------|------------------|
| Ceremony Master | __________________ | DR incident commander for ceremony |
| Key Custodian A | __________________ | Presents share(s); dual-control #1 |
| Key Custodian B | __________________ | Presents share(s); dual-control #2 |
| Key Custodian C (optional) | __________________ | Third share if A/B shares insufficient |
| Security Officer | __________________ | Replacement HSM initialization |
| Witness | __________________ | Independent observer |
| Scribe | __________________ | DR timeline and evidence |
| Validator Coordinator | __________________ | BR-011 re-activation liaison |

## Prerequisites

- DR incident declared; incident ticket ____________ opened
- Minimum 3 of 5 Shamir shares physically available and seal integrity verified
- Replacement HSM received, firmware attested, tamper seals intact
- Last known good public key fingerprint on file (from KC-01/KC-02 evidence pack)
- Isolated DR workstation; no production network until verification complete
- Legal/compliance notified if regulatory reporting threshold met

## Abort Criteria

Stop immediately if:

- Fewer than 3 shares available or any share seal broken/tampered
- Combined material fails unwrap or produces wrong public key
- Replacement HSM fails initialization or audit self-test
- Restored pubkey fingerprint ≠ last known good fingerprint
- Test signature verification fails
- Cannot produce complete evidence pack
- Any custodian cannot attest chain-of-custody for their share

---

## Ceremony Steps

| No | Aktor | Perintah / Aksi | Output diharapkan | Bukti | Paraf 1 | Paraf 2 |
|----|-------|-----------------|-------------------|-------|---------|---------|
| 1 | Ceremony Master | Open DR ceremony; scribe logs incident ref and start UTC | DR log started | Incident ticket link | | |
| 2 | Custodians A, B, (+C) | Present shares; witness verifies seal numbers vs custody log | Seals intact; IDs match | Photos of unopened envelopes | | |
| 3 | Witness + Scribe | Custodians open envelopes; scribe records share IDs used (not share contents) | Share index logged | Share index sheet | | |
| 4 | Security Officer | Init replacement HSM token (`00_init_softhsm.sh` on DR hardware) | Token ready | HSM init audit extract | | |
| 5 | Custodian A + B | Combine 3 shares (`03_restore_from_shares.sh`); import/unwrapped key into HSM | Key imported; handle created | Combine log (no secret data) | | |
| 6 | Ceremony Master | Compare restored public key fingerprint to last known good | Fingerprints match exactly | Side-by-side fingerprint sheet | | |
| 7 | Custodian A + B | Sign DR test payload (`04_sign_test_mint.sh`) | Signature verifies against known pubkey | Verification output | | |
| 8 | Validator Coordinator | If production resume required: BR-011 coordinated re-activation (same as KC-02 steps 7–9) | Validators accept restored key | Cutover ACK log | | |
| 9 | Scribe | Assemble DR evidence pack (`05_evidence_pack.sh`, CEREMONY_ID=KC-03) | `evidence/KC-03_<date>/` | manifest.json + PDF | | |
| 10 | Ceremony Master | Declare DR key reconstitution complete; hand off to operations | Close-out signed | Signed DR close-out | | |

---

## Share Selection Guidance

- Prefer shares from **different geographic sites** (minimum site diversity)
- If only 3 shares exist total, all must be present
- Never combine shares on networked machines prior to HSM import
- Destroy temporary combine artifacts on DR workstation after import

---

## Post-Ceremony

- Re-seal unused shares or re-split if policy requires post-DR refresh (schedule KC-01 if full re-generation mandated)
- Archive evidence to BC-12 within 24 h
- Post-incident review within 5 business days
- Update custodian roster if any share access was anomalous
