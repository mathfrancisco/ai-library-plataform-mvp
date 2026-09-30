.PHONY: up down logs dev-db test backend-test frontend-test lint format verify

up: ## Full stack in Docker (works without .env)
	docker compose up --build

down:
	docker compose down

logs:
	docker compose logs -f

dev-db: ## Only PostgreSQL + pgvector, for running backend/frontend on the host
	docker compose -f docker-compose.dev.yml up -d

test: backend-test frontend-test

backend-test: ## Unit tests (no Docker)
	cd backend && ./mvnw -B test

frontend-test:
	cd frontend && npm test

lint:
	cd backend && ./mvnw -B -q spotless:check
	cd frontend && npm run lint && npm run format:check

format:
	cd backend && ./mvnw -B -q spotless:apply
	cd frontend && npm run format

verify: ## What CI runs (backend integration tests need Docker)
	cd backend && ./mvnw -B verify
	cd frontend && npm ci && npm run format:check && npm run lint && npm test && npm run build
