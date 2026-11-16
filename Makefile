.PHONY: run format format-check lint check

run:
	@bash scripts/dev.sh

format:
	@bash scripts/format.sh write

format-check:
	@bash scripts/format.sh check

lint:
	@cd frontend && pnpm lint

check: format-check lint
	@cd frontend && pnpm check && pnpm test
	@cd backend && bash ../scripts/gradle.sh test --console=plain
