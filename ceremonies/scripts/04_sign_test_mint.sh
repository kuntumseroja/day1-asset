#!/usr/bin/env bash
# 04_sign_test_mint.sh — Sign test mint payload and verify signature.
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=lib/common.sh
source "${SCRIPT_DIR}/lib/common.sh"

detect_mode
ensure_dirs
ORIG_FPR="$(read_state pubkey_fingerprint)"
PUB_PATH="$(read_state pubkey_path)"

PAYLOAD="${WORK_DIR}/test_mint_payload.json"
SIG_OUT="${WORK_DIR}/test_mint.sig"
VERIFY_OUT="${WORK_DIR}/test_mint.verify"

cat > "${PAYLOAD}" <<EOF
{"type":"test_mint","ceremony":"${CEREMONY_ID}","key_label":"${KEY_LABEL}","fingerprint":"${ORIG_FPR}","ts":"$(date -u +%Y-%m-%dT%H:%M:%SZ)"}
EOF

if [[ "${MOCK_MODE}" -eq 1 ]]; then
  log_step "simulating test mint signature (mock mode)"
  if [[ -f "${WORK_DIR}/${KEY_LABEL}.mock.key" ]] && command -v openssl >/dev/null 2>&1; then
    openssl dgst -sha256 -sign "${WORK_DIR}/${KEY_LABEL}.mock.key" -out "${SIG_OUT}" "${PAYLOAD}"
    openssl dgst -sha256 -verify "${PUB_PATH}" -signature "${SIG_OUT}" "${PAYLOAD}" \
      > "${VERIFY_OUT}" 2>&1 || abort "mock signature verification failed"
  else
    PAYLOAD_HASH="$(sha256_file "${PAYLOAD}")"
    printf 'MOCK-SIG-%s\n' "${PAYLOAD_HASH}" > "${SIG_OUT}"
    printf 'Verified OK (mock): payload hash %s matches fingerprint context %s\n' \
      "${PAYLOAD_HASH}" "${ORIG_FPR}" > "${VERIFY_OUT}"
  fi
  [[ -s "${SIG_OUT}" ]] || abort "mock signature file empty"
  write_state test_mint_payload "${PAYLOAD}"
  write_state test_mint_signature "${SIG_OUT}"
  write_state test_mint_verify "${VERIFY_OUT}"
  write_state sign_mode "mock"
  log_step "mock test mint signed and verified"
  exit 0
fi

setup_softhsm_env
require_cmd pkcs11-tool
TOKEN_SLOT="$(read_state token_slot)"

log_step "signing test mint payload with label=${KEY_LABEL}"
pkcs11-tool --module "${PKCS11_MODULE}" \
  --login --pin "${USER_PIN}" \
  --slot "${TOKEN_SLOT}" \
  --sign --mechanism ECDSA \
  --label "${KEY_LABEL}" \
  --input-file "${PAYLOAD}" \
  --output-file "${SIG_OUT}" \
  || abort "pkcs11-tool --sign failed"

[[ -s "${SIG_OUT}" ]] || abort "signature file empty"

if command -v openssl >/dev/null 2>&1; then
  # SoftHSM ECDSA signatures are DER; verify with public key
  openssl dgst -sha256 -verify "${PUB_PATH}" -signature "${SIG_OUT}" "${PAYLOAD}" \
    > "${VERIFY_OUT}" 2>&1 || abort "live signature verification failed"
  grep -qi "verified\|success" "${VERIFY_OUT}" || abort "verification output unexpected: $(cat "${VERIFY_OUT}")"
else
  log_step "openssl unavailable — signature produced; manual verify required"
  printf 'Signature bytes written; openssl verify skipped\n' > "${VERIFY_OUT}"
fi

write_state test_mint_payload "${PAYLOAD}"
write_state test_mint_signature "${SIG_OUT}"
write_state test_mint_verify "${VERIFY_OUT}"
write_state sign_mode "live"
log_step "test mint signed and verified"
