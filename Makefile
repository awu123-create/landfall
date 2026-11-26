ifeq ($(OS),Windows_NT)
	ifeq ($(findstring sh,$(notdir $(SHELL))),sh)
		MVNW := ./mvnw.cmd
	else
		MVNW := .\mvnw.cmd
	endif
	PYTHON := python
else
	MVNW := ./mvnw
	PYTHON := python3
endif

.PHONY: help setup-hooks up down backend-run backend-fmt backend-style-check backend-test backend-verify backend-it check-docs check-migrations check

help:
	@echo "Landfall common commands:"
	@echo "  make setup-hooks         Install pre-commit git hooks"
	@echo "  make up / down           Start or stop local PostgreSQL 16 + Redis 7"
	@echo "  make backend-run         Start Spring Boot backend locally"
	@echo "  make backend-fmt         Apply Spotless Java formatting"
	@echo "  make backend-style-check Run Spotless check + Checkstyle in one JVM"
	@echo "  make backend-test        Run container-free unit, WebMvc & ArchUnit tests"
	@echo "  make backend-verify      Full fast backend gate (unit test, package, spotless, checkstyle)"
	@echo "  make backend-it          Run real PostgreSQL 16 + Redis 7 integration tests (*IT)"
	@echo "  make check-docs          Verify Markdown links and doc path pointers"
	@echo "  make check-migrations    Verify Flyway naming and block baseline migration edits"
	@echo "  make check               Run check-docs + check-migrations + backend-verify"

setup-hooks:
	pre-commit install --install-hooks

up:
	docker compose up -d --wait

down:
	docker compose down

backend-run:
	cd backend && $(MVNW) spring-boot:run

backend-fmt:
	cd backend && $(MVNW) -B spotless:apply

backend-style-check:
	cd backend && $(MVNW) -B spotless:check checkstyle:check

backend-test:
	cd backend && $(MVNW) -B test

backend-verify:
	cd backend && $(MVNW) -B verify

backend-it:
	cd backend && $(MVNW) -B test-compile failsafe:integration-test failsafe:verify

check-docs:
	$(PYTHON) scripts/check-docs.py

check-migrations:
	$(PYTHON) scripts/check-migrations.py

check: check-docs check-migrations backend-verify
