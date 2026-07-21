#!/usr/bin/env bash
# Shared helpers for ceremony dry-run scripts.
set -euo pipefail

CEREMONY_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
export CEREMONY_ROOT

SOFTHSM_DIR="${CEREMONY_ROOT}/softhsm"
WORK_DIR="${SOFTHSM_DIR}/work"
EVIDENCE_BASE="${CEREMONY_ROOT}/evidence"
TOKEN_LABEL="${TOKEN_LABEL:-wrd-ceremony}"
KEY_LABEL="${KEY_LABEL:-wrd-issuer-1}"
SLOT="${SLOT:-0}"
SO_PIN="${SO_PIN:-1234}"
USER_PIN="${USER_PIN:-5678}"
CEREMONY_ID="${CEREMONY_ID:-KC-01}"
DATE_STAMP="${DATE_STAMP:-$(date -u +%Y-%m-%d)}"

export SOFTHSM_DIR WORK_DIR EVIDENCE_BASE TOKEN_LABEL KEY_LABEL SLOT SO_PIN USER_PIN CEREMONY_ID DATE_STAMP

abort() {
  echo "ABORT: $*" >&2
  exit 1
}

require_cmd() {
  command -v "$1" >/dev/null 2>&1 || abort "required command not found: $1"
}

have_all_cmds() {
  local cmd
  for cmd in "$@"; do
    command -v "$cmd" >/dev/null 2>&1 || return 1
  done
  return 0
}

detect_mode() {
  if have_all_cmds softhsm2-util pkcs11-tool; then
    MOCK_MODE=0
  else
    MOCK_MODE=1
  fi
  export MOCK_MODE
}

ensure_dirs() {
  mkdir -p "${SOFTHSM_DIR}/tokens" "${WORK_DIR}" "${EVIDENCE_BASE}"
}

log_step() {
  echo "[$(basename "$0")] $*"
}

write_state() {
  local key="$1"
  local value="$2"
  mkdir -p "${WORK_DIR}"
  printf '%s\n' "${value}" > "${WORK_DIR}/${key}"
}

read_state() {
  local key="$1"
  local file="${WORK_DIR}/${key}"
  [[ -f "${file}" ]] || abort "missing state file: ${key} (run prior steps first)"
  cat "${file}"
}

read_state_optional() {
  local key="$1"
  local default="${2:-unknown}"
  local file="${WORK_DIR}/${key}"
  if [[ -f "${file}" ]]; then
    cat "${file}"
  else
    printf '%s' "${default}"
  fi
}

setup_softhsm_env() {
  if [[ "${MOCK_MODE}" -eq 0 ]]; then
    local conf="${SOFTHSM_DIR}/softhsm2.conf"
    mkdir -p "${SOFTHSM_DIR}/tokens"
    cat > "${conf}" <<EOF
directories.tokendir = ${SOFTHSM_DIR}/tokens
objectstore.backend = file
log.level = INFO
EOF
    export SOFTHSM2_CONF="${conf}"
    export PKCS11_MODULE="${PKCS11_MODULE:-/usr/lib/softhsm/libsofthsm2.so}"
    if [[ ! -f "${PKCS11_MODULE}" ]]; then
      for candidate in \
        /usr/local/lib/softhsm/libsofthsm2.so \
        /opt/homebrew/lib/softhsm/libsofthsm2.so \
        /usr/lib64/softhsm/libsofthsm2.so; do
        if [[ -f "${candidate}" ]]; then
          export PKCS11_MODULE="${candidate}"
          break
        fi
      done
    fi
    [[ -f "${PKCS11_MODULE}" ]] || abort "PKCS#11 module not found (set PKCS11_MODULE)"
  fi
}

mock_split_3_of_5() {
  local secret="$1"
  local out_dir="$2"
  mkdir -p "${out_dir}"
  local i
  for i in 1 2 3 4 5; do
    printf 'MOCK-SHARE-%s-%s\n' "${i}" "$(printf '%s' "${secret}" | shasum -a 256 | awk '{print $1}')" \
      > "${out_dir}/share-${i}.txt"
  done
}

mock_combine_3_of_5() {
  local share_dir="$1"
  local share
  for share in "${share_dir}/share-1.txt" "${share_dir}/share-2.txt" "${share_dir}/share-3.txt"; do
    [[ -f "${share}" ]] || abort "missing mock share: ${share}"
  done
  cat "${share_dir}/share-1.txt"
}

sha256_file() {
  if command -v sha256sum >/dev/null 2>&1; then
    sha256sum "$1" | awk '{print $1}'
  else
    shasum -a 256 "$1" | awk '{print $1}'
  fi
}
