# KC-02 · Issuer Key Rotation Ceremony

## Purpose

Rotate the WRD issuer signing key on a scheduled basis without service interruption beyond the agreed maintenance window. Coordinate cross-validator entity cutover per BR-011 so Besu QBFT validators and FireFly signing identities switch atomically to the new public key. Retire the old key per policy after confirmation period.

## Roles

| Role | Name (fill at ceremony) | Responsibilities |
|------|-------------------------|------------------|
| Ceremony Master | __________________ | Owns script adherence; coordinates rotation window |
| Key Custodian A | __________________ | Dual-control credential #1; old-key share custodian |
| Key Custodian B | __________________ | Dual-control credential #2; new-key share custodian |
| Security Officer | __________________ | HSM SO operations; audit log attestation |
| Witness | __________________ | Independent observer |
| Scribe | __________________ | Evidence and cross-entity coordination log |
| Validator Coordinator | __________________ | BR-011 liaison across validator entities |

## Prerequisites

- KC-01 completed; current key label `wrd-issuer-1` operational in FireFly/Besu
- Rotation window approved; BR-011 coordination memo signed by all validator entities
- New token slot or label pre-approved (`wrd-issuer-2`)
- Old Shamir shares available for DR verification only (not reused for new key)
- New blank Shamir envelopes (×5) prepared
- Staged `key-manager` config with new pubkey fingerprint (not yet active)
- Rollback plan documented: revert to old pubkey if cutover fails within 30 min

## Abort Criteria

Stop immediately if:

- New key generation or backup fails any KC-01-equivalent check
- Cross-validator coordination channel unreachable at T-0
- Any validator entity reports pubkey mismatch pre-cutover
- Dual signature test on new key fails
- Old key cannot be placed in `retired` state without error
- Evidence pack incomplete
- Deviation from coordinated rotation sequence (BR-011 §3)

---

## Ceremony Steps

| No | Aktor | Perintah / Aksi | Output diharapkan | Bukti | Paraf 1 | Paraf 2 |
|----|-------|-----------------|-------------------|-------|---------|---------|
| 1 | Validator Coordinator | Confirm BR-011 rotation window; all entities on bridge call | Signed coordination ack from each entity | BR-011 ack sheet | | |
| 2 | Security Officer | Attest HSM firmware + tamper log (same as KC-01 step 1) | Clean attestation | Signed attestation | | |
| 3 | Custodian A + B | Generate **new** keypair label `wrd-issuer-2` (`01_generate_keypair.sh` with KEY_LABEL override) | New pubkey fingerprint recorded | New pubkey PEM | | |
| 4 | Custodian A + B | Shamir 3-of-5 backup of new wrapped key (`02_backup_shamir.sh`) | Five new shares sealed | Envelope photos | | |
| 5 | Custodian A + B | Restore test on new shares (`03_restore_from_shares.sh`) | Pubkey match confirmed | Restore log | | |
| 6 | Custodian A + B | Sign test mint with **new** key (`04_sign_test_mint.sh`) | Signature verifies | Test sig evidence | | |
| 7 | Validator Coordinator | **T-30 min** — distribute new pubkey fingerprint to all entities; each confirms receipt | All entities ACK same fingerprint | ACK log with UTC timestamps | | |
| 8 | Validator Coordinator | **T-0** — coordinated cutover: entities update signing identity to `wrd-issuer-2` in lockstep (Besu QBFT config + FireFly key-manager) | All validators report active new key | Per-entity cutover log extract | | |
| 9 | Ceremony Master | **T+15 min** — smoke test: one mint on production path signed with new key | Mint confirmed on-chain | Tx hash + event log | | |
| 10 | Security Officer | Mark old key `wrd-issuer-1` as `retired` in HSM (no delete until retention period) | HSM shows retired state | Audit log extract | | |
| 11 | Scribe | Evidence pack for rotation (`05_evidence_pack.sh`, CEREMONY_ID=KC-02) | `evidence/KC-02_<date>/` complete | manifest.json | | |
| 12 | Validator Coordinator | **T+24 h** — confirm no entity still signing with old key (monitoring query) | Zero old-key signatures | Monitoring screenshot | | |

---

## BR-011 Cross-Validator Coordination Protocol

1. **Pre-window (T-7 days):** Validator Coordinator sends rotation notice with new pubkey fingerprint, window UTC, and rollback contact.
2. **Pre-window (T-24 h):** Each entity runs dry-run cutover in staging; returns pass/fail to coordinator.
3. **Live window:** Coordinator calls "cutover" only when all entities ACK ready on signed channel.
4. **Cutover order:** (a) FireFly key-manager staging → active, (b) Besu validator configs reload, (c) smoke mint, (d) monitor 15 min.
5. **Rollback:** If smoke mint fails, coordinator calls "rollback"; entities revert to old pubkey within 30 min; ceremony aborted and rescheduled.

---

## Post-Ceremony

- Old Shamir shares remain archived per retention policy (7 years)
- Update key inventory register; notify compliance within 48 h
- Schedule next rotation (+12 months)
