.PHONY: up down logs backend-test frontend-test

up:
	docker compose up --build

down:
	docker compose down

logs:
	docker compose logs -f

backend-test:
	cd backend && mvn test

frontend-test:
	cd frontend && npm test
