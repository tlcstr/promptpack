SHELL := /bin/bash
.DEFAULT_GOAL := help

GRADLE ?= ./gradlew
ARGS ?=

.PHONY: help run build verify test test-one check ktlint lint fmt detekt clean ci

help:
	@echo "Available targets:"
	@echo "  run         - Launch sandbox IDE (runIde)"
	@echo "  build       - Build plugin ZIP (buildPlugin)"
	@echo "  verify      - Verify plugin against IDEs (verifyPlugin)"
	@echo "  test        - Run all tests"
	@echo "  test-one    - Run a single test (use TEST=...)"
	@echo "  check       - ktlintCheck + detekt + test"
	@echo "  ktlint      - Run ktlintCheck"
	@echo "  lint        - Alias for ktlint"
	@echo "  fmt         - Auto-format with ktlintFormat"
	@echo "  detekt      - Run detekt"
	@echo "  clean       - Clean build"
	@echo "  ci          - Clean, check, buildPlugin"

run:
	$(GRADLE) $(ARGS) runIde

build:
	$(GRADLE) $(ARGS) buildPlugin

verify:
	$(GRADLE) $(ARGS) verifyPlugin

test:
	$(GRADLE) $(ARGS) test

test-one:
	@if [ -z "$(TEST)" ]; then echo "Usage: make test-one TEST=dev.promptpack.ClassNameOrPattern"; exit 1; fi
	$(GRADLE) $(ARGS) test --tests '$(TEST)'

check:
	$(GRADLE) $(ARGS) check

ktlint:
	$(GRADLE) $(ARGS) ktlintCheck

lint: ktlint

fmt:
	$(GRADLE) $(ARGS) ktlintFormat

detekt:
	$(GRADLE) $(ARGS) detekt

clean:
	$(GRADLE) $(ARGS) clean

ci: clean check build
