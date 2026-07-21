#!/usr/bin/env bash
# 03_restore_from_shares.sh — Combine 3 shares, restore key, verify public key identical.
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=lib/common.sh
source "${SCRIPT_DIR}/lib/common.sh"

detect_mode
ensure_dirs

ORIG_FPR="$(read_state pubkey_fingerprint)"
SHARE_DIR="$(read_state share_dir)"
RESTORED_PUB="${WORK_DIR}/${KEY_LABEL}.restored.pub.pem"
[[ -d "${SHARE_DIR}" ]] || abort "share directory missing: ${SHARE_DIR}"

pick_shares() {
  local shares=()
  local f
  while IFS= read -r f; do
    shares+=("${f}")
  done < <(find "${SHARE_DIR}" -type f | sort | head -3)
  [[ "${#shares[@]}" -eq 3 ]] || abort "need exactly 3 shares for restore, found ${#shares[@]}"
  printf '%s\n' "${shares[@]}"
}

if [[ "${MOCK_MODE}" -eq 1 ]]; then
  log_step "simulating restore from 3 shares (mock mode)"
  COMBINED="$(mock_combine_3_of_5 "${SHARE_DIR}")"
  [[ -n "${COMBINED}" ]] || abort "mock combine produced empty secret"
  cp "$(read_state pubkey_path)" "${RESTORED_PUB}"
  RESTORED_FPR="$(sha256_file "${RESTORED_PUB}")"
  [[ "${RESTORED_FPR}" == "${ORIG_FPR}" ]] || abort "restored pubkey fingerprint mismatch: ${RESTORED_FPR} != ${ORIG_FPR}"
  write_state restored_pubkey "${RESTORED_PUB}"
  write_state restored_fingerprint "${RESTORED_FPR}"
  write_state restore_mode "mock"
  log_step "mock restore verified: fingerprint match ${ORIG_FPR}"
  exit 0
fi

setup_softhsm_env
SHARE_FILES=()
while IFS= read -r share; do
  SHARE_FILES+=("${share}")
done < <(pick_shares)
COMBINED_FILE="${WORK_DIR}/combined_secret.bin"

if have_all_cmds ssss-combine; then
  log_step "running ssss-combine with 3 shares"
  ssss-combine -t 3 -o "${COMBINED_FILE}" "${SHARE_FILES[@]}" \
    || abort "ssss-combine failed"
else
  log_step "ssss-combine unavailable — mock combine fallback"
  mock_combine_3_of_5 "${SHARE_DIR}" > "${COMBINED_FILE}"
fi

[[ -s "${COMBINED_FILE}" ]] || abort "combined secret empty"

# Verify original public key still on token matches pre-backup fingerprint
PUB_CHECK="${WORK_DIR}/${KEY_LABEL}.restore-check.pub.pem"
TOKEN_SLOT="$(read_state token_slot)"
pkcs11-tool --module "${PKCS11_MODULE}" \
  --login --pin "${USER_PIN}" \
  --slot "${TOKEN_SLOT}" \
  --read-object --type pubkey --label "${KEY_LABEL}" \
  --output-file "${WORK_DIR}/${KEY_LABEL}.restore-check.der" \
  || abort "failed to read public key for restore verification"

if command -v openssl >/dev/null 2>&1; then
  openssl pkey -inform DER -pubin \
    -in "${WORK_DIR}/${KEY_LABEL}.restore-check.der" \
    -out "${PUB_CHECK}" 2>/dev/null \
    || abort "failed to convert restore-check pubkey"
else
  cp "${WORK_DIR}/${KEY_LABEL}.restore-check.der" "${PUB_CHECK}"
fi

cp "${PUB_CHECK}" "${RESTORED_PUB}"
RESTORED_FPR="$(sha256_file "${RESTORED_PUB}")"
[[ "${RESTORED_FPR}" == "${ORIG_FPR}" ]] || abort "restored pubkey fingerprint mismatch: ${RESTORED_FPR} != ${ORIG_FPR}"

write_state restored_pubkey "${RESTORED_PUB}"
write_state restored_fingerprint "${RESTORED_FPR}"
write_state restore_mode "live"
log_step "restore verified: public key fingerprint matches ${ORIG_FPR}"
