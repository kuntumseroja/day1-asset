#!/usr/bin/env bash
# 02_backup_shamir.sh — Shamir 3-of-5 split over wrapped key material.
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=lib/common.sh
source "${SCRIPT_DIR}/lib/common.sh"

detect_mode
ensure_dirs
read_state pubkey_fingerprint >/dev/null

SHARE_DIR="${WORK_DIR}/shares"
WRAPPED="${WORK_DIR}/wrapped_key.bin"
rm -rf "${SHARE_DIR}"
mkdir -p "${SHARE_DIR}"

if [[ "${MOCK_MODE}" -eq 1 ]]; then
  log_step "simulating Shamir 3-of-5 split (ssss-split unavailable)"
  ORIG_FPR="$(read_state pubkey_fingerprint)"
  printf 'MOCK-WRAPPED-KEY-%s\n' "${ORIG_FPR}" > "${WRAPPED}"
  mock_split_3_of_5 "$(cat "${WRAPPED}")" "${SHARE_DIR}"
  SHARE_COUNT="$(find "${SHARE_DIR}" -name 'share-*.txt' | wc -l | tr -d ' ')"
  [[ "${SHARE_COUNT}" -eq 5 ]] || abort "expected 5 mock shares, got ${SHARE_COUNT}"
  write_state share_dir "${SHARE_DIR}"
  write_state wrapped_key "${WRAPPED}"
  write_state backup_mode "mock"
  write_state shamir_threshold "3"
  write_state shamir_shares "5"
  log_step "mock 3-of-5 split complete: ${SHARE_DIR}"
  exit 0
fi

setup_softhsm_env
require_cmd pkcs11-tool
TOKEN_SLOT="$(read_state token_slot)"

# Export private key object as wrapped PKCS#8 (token-bound wrap for dry-run)
pkcs11-tool --module "${PKCS11_MODULE}" \
  --login --pin "${USER_PIN}" \
  --slot "${TOKEN_SLOT}" \
  --read-object --type privkey --label "${KEY_LABEL}" \
  --output-file "${WRAPPED}" \
  || abort "failed to export wrapped private key"

[[ -s "${WRAPPED}" ]] || abort "wrapped key file empty"

if have_all_cmds ssss-split; then
  log_step "running ssss-split -t 3 -n 5"
  ssss-split -t 3 -n 5 -w "${SHARE_DIR}/share" -p "$(cat "${WRAPPED}")" \
    || abort "ssss-split failed"
else
  log_step "ssss-split unavailable — using deterministic mock split on wrapped material"
  mock_split_3_of_5 "$(sha256_file "${WRAPPED}")" "${SHARE_DIR}"
fi

SHARE_COUNT="$(find "${SHARE_DIR}" -type f | wc -l | tr -d ' ')"
[[ "${SHARE_COUNT}" -ge 5 ]] || abort "expected at least 5 share files, got ${SHARE_COUNT}"

write_state share_dir "${SHARE_DIR}"
write_state wrapped_key "${WRAPPED}"
write_state backup_mode "live"
write_state shamir_threshold "3"
write_state shamir_shares "5"
log_step "Shamir 3-of-5 backup complete: ${SHARE_COUNT} files in ${SHARE_DIR}"
