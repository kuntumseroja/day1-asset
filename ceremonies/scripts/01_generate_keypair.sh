#!/usr/bin/env bash
# 01_generate_keypair.sh — Generate EC secp256k1 keypair label wrd-issuer-1.
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=lib/common.sh
source "${SCRIPT_DIR}/lib/common.sh"

detect_mode
ensure_dirs
read_state token_initialized >/dev/null

PUB_OUT="${WORK_DIR}/${KEY_LABEL}.pub.pem"
PRIV_FINGER="${WORK_DIR}/${KEY_LABEL}.fingerprint"

if [[ "${MOCK_MODE}" -eq 1 ]]; then
  log_step "simulating EC secp256k1 keypair generation (pkcs11-tool unavailable)"
  if command -v openssl >/dev/null 2>&1; then
    openssl ecparam -name secp256k1 -genkey -noout -out "${WORK_DIR}/${KEY_LABEL}.mock.key"
    openssl ec -in "${WORK_DIR}/${KEY_LABEL}.mock.key" -pubout -out "${PUB_OUT}" 2>/dev/null
  else
    cat > "${PUB_OUT}" <<'EOF'
-----BEGIN PUBLIC KEY-----
MFYwEAYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEmockCeremonyDryRunKeyForWRDIssuerOne==
-----END PUBLIC KEY-----
EOF
  fi
  FPR="$(sha256_file "${PUB_OUT}")"
  printf '%s\n' "${FPR}" > "${PRIV_FINGER}"
  write_state key_label "${KEY_LABEL}"
  write_state pubkey_path "${PUB_OUT}"
  write_state pubkey_fingerprint "${FPR}"
  write_state generate_mode "mock"
  log_step "mock keypair label=${KEY_LABEL} fingerprint=${FPR}"
  exit 0
fi

setup_softhsm_env
require_cmd pkcs11-tool

TOKEN_SLOT="$(softhsm2-util --show-slots 2>/dev/null | awk -v lbl="${TOKEN_LABEL}" '
  /Slot / { slot=$2 }
  /Label:.*'"${TOKEN_LABEL}"'/ { print slot; exit }
')"
[[ -n "${TOKEN_SLOT}" ]] || abort "cannot find slot for token label ${TOKEN_LABEL}"

log_step "generating EC:secp256k1 keypair label=${KEY_LABEL} slot=${TOKEN_SLOT}"
pkcs11-tool --module "${PKCS11_MODULE}" \
  --login --pin "${USER_PIN}" \
  --slot "${TOKEN_SLOT}" \
  --keypairgen \
  --key-type EC:secp256k1 \
  --label "${KEY_LABEL}" \
  --id 01 \
  || abort "pkcs11-tool --keypairgen failed"

pkcs11-tool --module "${PKCS11_MODULE}" \
  --login --pin "${USER_PIN}" \
  --slot "${TOKEN_SLOT}" \
  --read-object --type pubkey --label "${KEY_LABEL}" \
  --output-file "${WORK_DIR}/${KEY_LABEL}.pub.der" \
  || abort "failed to export public key"

if command -v openssl >/dev/null 2>&1; then
  openssl pkey -inform DER -pubin \
    -in "${WORK_DIR}/${KEY_LABEL}.pub.der" \
    -out "${PUB_OUT}" 2>/dev/null \
    || abort "failed to convert public key to PEM"
else
  cp "${WORK_DIR}/${KEY_LABEL}.pub.der" "${PUB_OUT}"
fi

FPR="$(sha256_file "${PUB_OUT}")"
printf '%s\n' "${FPR}" > "${PRIV_FINGER}"
write_state key_label "${KEY_LABEL}"
write_state pubkey_path "${PUB_OUT}"
write_state pubkey_fingerprint "${FPR}"
write_state generate_mode "live"
write_state token_slot "${TOKEN_SLOT}"
log_step "keypair label=${KEY_LABEL} fingerprint=${FPR}"
