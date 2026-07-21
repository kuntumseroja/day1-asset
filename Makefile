.PHONY: up down health dev-up dev-down demo-duplicate demo-replay demo-contract-break demo-limit-change demo-break dry-run demos deploy-aws-portal

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

dry-run:
	$(MAKE) -C ceremonies dry-run

# Build portal for AWS (set PUBLIC_URL first, e.g. http://13.212.xxx.xxx)
deploy-aws-portal:
	@test -n "$(PUBLIC_URL)" || (echo "Set PUBLIC_URL=http://your-elastic-ip" && exit 1)
	cd portal && VITE_API_BASE="$(PUBLIC_URL)/api/v1" \
		VITE_RECON_BASE="$(PUBLIC_URL)/recon/api/v1" \
		VITE_WS_URL="$$(echo $(PUBLIC_URL) | sed 's|^http:|ws:|')/ws" \
		npm run build
