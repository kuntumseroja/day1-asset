#!/usr/bin/env bash
# 05_evidence_pack.sh — Collect artifacts, checksum manifest, render evidence PDF.
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=lib/common.sh
source "${SCRIPT_DIR}/lib/common.sh"

detect_mode
ensure_dirs

for required in pubkey_fingerprint pubkey_path share_dir test_mint_signature; do
  read_state "${required}" >/dev/null
done

PACK_DIR="${EVIDENCE_BASE}/${CEREMONY_ID}_${DATE_STAMP}"
mkdir -p "${PACK_DIR}"

log_step "assembling evidence pack in ${PACK_DIR}"

copy_if_exists() {
  local src="$1"
  local dest_name="$2"
  if [[ -f "${src}" ]]; then
    cp "${src}" "${PACK_DIR}/${dest_name}"
  fi
}

copy_if_exists "$(read_state pubkey_path)" "public_key.pem"
copy_if_exists "${WORK_DIR}/${KEY_LABEL}.fingerprint" "public_key.fingerprint"
copy_if_exists "$(read_state wrapped_key)" "wrapped_key.bin"
copy_if_exists "$(read_state test_mint_payload)" "test_mint_payload.json"
copy_if_exists "$(read_state test_mint_signature)" "test_mint.sig"
copy_if_exists "$(read_state test_mint_verify)" "test_mint.verify"
copy_if_exists "$(read_state restored_pubkey)" "restored_public_key.pem"

SHARE_DIR="$(read_state share_dir)"
if [[ -d "${SHARE_DIR}" ]]; then
  mkdir -p "${PACK_DIR}/shares"
  cp "${SHARE_DIR}"/* "${PACK_DIR}/shares/" 2>/dev/null || true
fi

# Ceremony metadata
cat > "${PACK_DIR}/ceremony_meta.json" <<EOF
{
  "ceremony_id": "${CEREMONY_ID}",
  "date": "${DATE_STAMP}",
  "key_label": "${KEY_LABEL}",
  "token_label": "${TOKEN_LABEL}",
  "pubkey_fingerprint": "$(read_state pubkey_fingerprint)",
  "modes": {
    "init": "$(read_state_optional init_mode)",
    "generate": "$(read_state_optional generate_mode)",
    "backup": "$(read_state_optional backup_mode)",
    "restore": "$(read_state_optional restore_mode)",
    "sign": "$(read_state_optional sign_mode)"
  },
  "generated_at": "$(date -u +%Y-%m-%dT%H:%M:%SZ)"
}
EOF

# Checksum manifest
MANIFEST="${PACK_DIR}/manifest.json"
{
  echo '{'
  echo '  "ceremony_id": "'"${CEREMONY_ID}"'",'
  echo '  "date": "'"${DATE_STAMP}"'",'
  echo '  "generated_at": "'$(date -u +%Y-%m-%dT%H:%M:%SZ)'",'
  echo '  "files": {'
  first=1
  while IFS= read -r f; do
    rel="${f#${PACK_DIR}/}"
    hash="$(sha256_file "${f}")"
    if [[ "${first}" -eq 1 ]]; then first=0; else echo ','; fi
    printf '    "%s": "%s"' "${rel}" "${hash}"
  done < <(find "${PACK_DIR}" -type f ! -name 'manifest.json' ! -name 'evidence_pack.pdf' | sort)
  echo
  echo '  }'
  echo '}'
} > "${MANIFEST}"

[[ -s "${MANIFEST}" ]] || abort "manifest.json empty"

MANIFEST_FPR="$(sha256_file "${MANIFEST}")"
printf '%s\n' "${MANIFEST_FPR}" > "${PACK_DIR}/manifest.sha256"

# Render PDF via pandoc (or stub)
PDF_OUT="${PACK_DIR}/evidence_pack.pdf"
MD_SRC="${PACK_DIR}/evidence_pack.md"
cat > "${MD_SRC}" <<EOF
# Evidence Pack: ${CEREMONY_ID}

**Date:** ${DATE_STAMP}  
**Key label:** ${KEY_LABEL}  
**Public key fingerprint:** $(read_state pubkey_fingerprint)  
**Manifest SHA-256:** ${MANIFEST_FPR}

## Summary

Ceremony dry-run completed. Artifacts and checksums are listed in \`manifest.json\`.

## Modes

| Step | Mode |
|------|------|
| Init | $(read_state_optional init_mode) |
| Generate | $(read_state_optional generate_mode) |
| Backup | $(read_state_optional backup_mode) |
| Restore | $(read_state_optional restore_mode) |
| Sign | $(read_state_optional sign_mode) |
EOF

if command -v pandoc >/dev/null 2>&1; then
  if pandoc "${MD_SRC}" -o "${PDF_OUT}" 2>/dev/null; then
    log_step "evidence_pack.pdf rendered via pandoc"
  else
    log_step "pandoc PDF render failed — writing stub PDF marker"
    printf '%%PDF-1.4\n%% Stub evidence pack — pandoc/LaTeX unavailable\nCeremony: %s\nDate: %s\n' \
      "${CEREMONY_ID}" "${DATE_STAMP}" > "${PDF_OUT}"
  fi
else
  log_step "pandoc unavailable — writing stub PDF marker"
  printf '%%PDF-1.4\n%% Mock evidence pack — install pandoc for real PDF\n' > "${PDF_OUT}"
fi

[[ -s "${PDF_OUT}" ]] || abort "evidence_pack.pdf empty"
[[ -s "${MANIFEST}" ]] || abort "manifest validation failed"

write_state evidence_pack_dir "${PACK_DIR}"
log_step "evidence pack complete: ${PACK_DIR}"
log_step "manifest fingerprint: ${MANIFEST_FPR}"
