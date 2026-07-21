.PHONY: dry-run clean init generate backup restore sign evidence pack help

SCRIPTS_DIR := scripts
CEREMONY_ID ?= KC-01
export CEREMONY_ID

help:
	@echo "Ceremony dry-run targets:"
	@echo "  make dry-run   Run scripts 00→05 (full ceremony simulation)"
	@echo "  make clean     Remove softhsm work dir and evidence packs"
	@echo "  make init      Run 00_init_softhsm.sh only"
	@echo "  make generate  Run 01_generate_keypair.sh only"
	@echo "  make backup    Run 02_backup_shamir.sh only"
	@echo "  make restore   Run 03_restore_from_shares.sh only"
	@echo "  make sign      Run 04_sign_test_mint.sh only"
	@echo "  make evidence  Run 05_evidence_pack.sh only"

dry-run: init generate backup restore sign evidence
	@echo "Ceremony dry-run complete (00→05)."

init:
	@bash $(SCRIPTS_DIR)/00_init_softhsm.sh

generate: init
	@bash $(SCRIPTS_DIR)/01_generate_keypair.sh

backup: generate
	@bash $(SCRIPTS_DIR)/02_backup_shamir.sh

restore: backup
	@bash $(SCRIPTS_DIR)/03_restore_from_shares.sh

sign: restore
	@bash $(SCRIPTS_DIR)/04_sign_test_mint.sh

evidence: sign
	@bash $(SCRIPTS_DIR)/05_evidence_pack.sh

pack: evidence

clean:
	rm -rf softhsm/work evidence/KC-*
