#!/usr/bin/env bash
# 00_init_softhsm.sh — Initialize SoftHSM2 token with separate SO and User PINs.
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=lib/common.sh
source "${SCRIPT_DIR}/lib/common.sh"

detect_mode
ensure_dirs
log_step "mode=$([[ ${MOCK_MODE} -eq 0 ]] && echo live || echo mock)"

if [[ "${MOCK_MODE}" -eq 1 ]]; then
  log_step "simulating token init (softhsm2-util unavailable)"
  mkdir -p "${SOFTHSM_DIR}/tokens/${TOKEN_LABEL}"
  printf 'mock-so-pin:%s\n' "${SO_PIN}" > "${SOFTHSM_DIR}/tokens/${TOKEN_LABEL}/.so_pin"
  printf 'mock-user-pin:%s\n' "${USER_PIN}" > "${SOFTHSM_DIR}/tokens/${TOKEN_LABEL}/.user_pin"
  printf 'mock-token-init\n' > "${SOFTHSM_DIR}/tokens/${TOKEN_LABEL}/token.info"
  write_state token_initialized "mock-${TOKEN_LABEL}"
  write_state init_mode "mock"
  log_step "mock token '${TOKEN_LABEL}' initialized"
  exit 0
fi

setup_softhsm_env

if softhsm2-util --show-slots 2>/dev/null | grep -q "Label:.*${TOKEN_LABEL}"; then
  log_step "token '${TOKEN_LABEL}' already exists on slot — reusing"
  write_state token_initialized "${TOKEN_LABEL}"
  write_state init_mode "live-reuse"
  exit 0
fi

log_step "initializing token label=${TOKEN_LABEL} slot=${SLOT}"
softhsm2-util --init-token --free \
  --label "${TOKEN_LABEL}" \
  --so-pin "${SO_PIN}" \
  --pin "${USER_PIN}" \
  || abort "softhsm2-util --init-token failed"

if ! softhsm2-util --show-slots 2>/dev/null | grep -q "Label:.*${TOKEN_LABEL}"; then
  abort "token '${TOKEN_LABEL}' not visible after init"
fi

write_state token_initialized "${TOKEN_LABEL}"
write_state init_mode "live"
log_step "token '${TOKEN_LABEL}' initialized with distinct SO and User PINs"
