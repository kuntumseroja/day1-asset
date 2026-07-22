.PHONY: up down health dev-up dev-down demo-duplicate demo-replay demo-contract-break demo-limit-change demo-break dry-run demos deploy-aws-portal docker-build debezium-smoke portal-e2e

up:
	docker compose up -d

down:
	docker compose down

dev-up:
	./scripts/dev-up.sh

dev-down:
	./scripts/dev-down.sh

demos: demo-replay demo-limit-change demo-break demo-duplicate

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

debezium-smoke:
	./scripts/debezium-smoke.sh

portal-e2e:
	./scripts/portal-e2e.sh

docker-build:
	docker compose build policy recon saga-lib firefly-kit rtgs-sim firefly-stub portal-sim

dry-run:
	$(MAKE) -C ceremonies dry-run

# Build portal for AWS — same-origin paths (no Elastic IP baked into JS)
deploy-aws-portal:
	cd portal && VITE_API_BASE="/api/v1" \
		VITE_RECON_BASE="/recon/api/v1" \
		npm run build
