.PHONY: up down health demo-duplicate demo-replay demo-contract-break demo-limit-change demo-break dry-run

up:
	docker compose up -d

down:
	docker compose down

health:
	@curl -sf http://localhost:8091/health && echo " rtgs-sim OK"
	@curl -sf http://localhost:8092/health && echo " firefly-stub OK"
	@curl -sf http://localhost:8093/health && echo " portal-sim OK"

demo-duplicate:
	./saga-lib/demo-duplicate.sh

demo-replay:
	./firefly-kit/demo-replay.sh

demo-contract-break:
	./firefly-kit/demo-contract-break.sh

demo-limit-change:
	./policy/demo-limit-change.sh

demo-break:
	./recon/demo-break.sh

dry-run:
	$(MAKE) -C ceremonies dry-run
