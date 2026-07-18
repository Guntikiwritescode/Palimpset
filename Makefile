# PALIMPSEST — top-level developer entrypoints.
# Local dev assumes a PostgreSQL 16 server on 127.0.0.1:5432 with roles
# migrate/engine_rw/analytics_ro (see scripts/dev_db.sh).

SHELL := /usr/bin/env bash
PY    := pipeline/.venv/bin/python
PIP   := pipeline/.venv/bin/pip

.PHONY: help
help: ## Show this help
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | sort | \
	  awk 'BEGIN{FS=":.*?## "}{printf "  \033[36m%-20s\033[0m %s\n",$$1,$$2}'

# ---- gates -----------------------------------------------------------------
.PHONY: gates
gates: contracts no-dump no-ingress ## Run all hard gates

.PHONY: contracts
contracts: ## Contract gate: schema self-validate, sample validates, checksums
	$(PY) scripts/contract_gate.py

.PHONY: migrations
migrations: ## Apply V1+V2 to an ephemeral PG 16 (byte-identity check included)
	bash scripts/apply_migrations.sh

.PHONY: no-dump
no-dump: ## Assert the SDFB dump is not in git
	bash scripts/check_no_dump.sh tree

.PHONY: no-ingress
no-ingress: ## Assert no Ingress/public exposure in manifests
	bash scripts/check_no_ingress.sh tree

.PHONY: hooks
hooks: ## Install the pre-commit hard-gate hooks
	bash scripts/install_hooks.sh

# ---- venv / pipeline -------------------------------------------------------
.PHONY: venv
venv: ## Create the pipeline venv with dev deps
	python3.12 -m venv pipeline/.venv
	$(PIP) install -q --upgrade pip
	$(PIP) install -q -e 'pipeline[dev]'

.PHONY: pipeline-test
pipeline-test: ## Run the fast pipeline test suite (golden + rules)
	cd pipeline && .venv/bin/pytest -q -m 'not slow'

.PHONY: fixture
fixture: ## (Re)generate the synthetic CI fixture
	$(PY) -m palimpsest_pipeline.cli synth --out fixtures/synthetic

# ---- engine ----------------------------------------------------------------
.PHONY: engine-build
engine-build: ## Build the engine (skip tests)
	cd services/engine && mvn -q -DskipTests package

.PHONY: engine-test
engine-test: ## Run engine tests (needs PALIMPSEST_TEST_JDBC_URL or Docker)
	cd services/engine && mvn -q test

.PHONY: engine-run
engine-run: ## Run the engine against the local dev DB
	cd services/engine && \
	  SPRING_DATASOURCE_URL=jdbc:postgresql://127.0.0.1:5432/palimpsest \
	  SPRING_DATASOURCE_USERNAME=engine_rw SPRING_DATASOURCE_PASSWORD=engine_pw \
	  PALIMPSEST_SCHOLAR_TOKEN=dev-scholar-token PALIMPSEST_PIPELINE_TOKEN=dev-pipeline-token \
	  mvn -q spring-boot:run

# ---- sdk / explorer --------------------------------------------------------
.PHONY: sdk
sdk: ## Regenerate the TypeScript SDK from the engine OpenAPI (and check drift)
	bash sdk/typescript/generate.sh

.PHONY: explorer-build
explorer-build: ## Build the explorer
	cd explorer && (pnpm install && pnpm build)

# ---- db helper -------------------------------------------------------------
.PHONY: dev-db
dev-db: ## Create local roles + palimpsest database
	bash scripts/dev_db.sh

# ---- images ----------------------------------------------------------------
CLUSTER_NAME ?= palimpsest-smoke

.PHONY: images
images: ## Build the engine + explorer container images, tagged :local (needs Docker)
	# Engine build context is the REPO ROOT (the build copies contracts/ onto the
	# classpath); explorer builds from explorer/.
	docker build -f services/engine/Dockerfile -t palimpsest/engine:local .
	docker build -f explorer/Dockerfile -t palimpsest/explorer:local explorer
	@docker images --format '{{.Repository}}:{{.Tag}}' | grep -E '^palimpsest/(engine|explorer):local$$'

.PHONY: kind-load
kind-load: images ## Load the :local images into the kind cluster (needs kind)
	kind load docker-image palimpsest/engine:local   --name $(CLUSTER_NAME)
	kind load docker-image palimpsest/explorer:local --name $(CLUSTER_NAME)

# ---- demo ------------------------------------------------------------------
.PHONY: demo
demo: ## Bring up the stack and load the synthetic fixture (kind; needs Docker)
	bash scripts/demo.sh
