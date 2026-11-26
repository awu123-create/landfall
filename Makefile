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

.PHONY: help setup-hooks up down backend-run backend-fmt backend-style-check backend-test backend-verify check-docs check

help:
	@echo "Landfall common commands:"
	@echo "  make setup-hooks         Install pre-commit git hooks"
	@echo "  make up / down           Start or stop local PostgreSQL 16 + Redis 7"
	@echo "  make backend-run         Start Spring Boot backend locally"
	@echo "  make backend-fmt         Apply Spotless Java formatting"
	@echo "  make backend-style-check Run Spotless check + Checkstyle in one JVM"
	@echo "  make backend-test        Run backend unit & ArchUnit tests"
	@echo "  make backend-verify      Full backend gate (test, package, spotless, checkstyle)"
	@echo "  make check-docs          Verify Markdown links and doc path pointers"
	@echo "  make check               Run check-docs + backend-verify"

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

check-docs:
	$(PYTHON) scripts/check-docs.py

check: check-docs backend-verify
