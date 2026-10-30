# Ecosync AI

A controlled workspace for connecting heterogeneous systems and, in later phases, answering questions with traceable evidence.

Research: **AI-Driven Interoperability: Exploring the Role of Large Language Models and the Model Context Protocol in Digital Ecosystem Integration.**

## Current release

Phase 1 implements a Spring MVC/PostgreSQL foundation, secure session login, tenant-scoped workspace access, and a responsive React interface. It includes a dashboard, empty integrations page, system status, and an explicitly unavailable AI workspace. Phase 2 adds independently running sales PostgreSQL, inventory MySQL and a supplier REST API, with deterministic source data and external evaluation fixtures. No MCP, platform database/API connectors, AI orchestration or deployment automation is implemented.

The repository is currently [public](https://github.com/ChouaibSeishin/EcoSync) (verified 2026-10-30); visibility was not changed by this work. Current work is on `codex/phase-2-demo-ecosystem`, based on the accepted Phase 1 commit. The default Phase 0 branch remains preserved.

## Run locally

Requires Java 21 (Temurin 21.0.13+9), Node 24.22.2/npm 11.19.1 and Docker with Linux containers. The Maven wrapper pins Maven 3.9.16 and verifies its distribution checksum. See [local setup](docs/development/LOCAL_SETUP.md) for Windows/POSIX commands and portable tool usage.

```powershell
# The default branch still contains Phase 0; select the implementation branch.
git clone --branch codex/phase-2-demo-ecosystem https://github.com/ChouaibSeishin/EcoSync.git
Set-Location EcoSync
Push-Location backend
./mvnw.cmd -B -ntp verify
Pop-Location
Push-Location frontend
npm ci
npm run lint
npm run typecheck
npm test -- --run
npm run build
npx --no-install playwright install chromium
Pop-Location
node scripts/init-local.mjs
docker compose -f infra/docker/compose.yaml config --quiet
docker compose -f infra/docker/compose.yaml up --build -d --wait --wait-timeout 180
```

Open [the local workspace](http://127.0.0.1:5173). Read your unique administrator username/password locally from ignored `infra/docker/.env`. There is no universal password. A one-shot setup process applies migrations and provisions the first administrator; later starts cannot reset it. The runtime backend does not receive bootstrap credentials.

No paid model credentials or live model are needed. The frontend container is a local development server, not a production deployment.

## Verification

```powershell
node scripts/verify-foundation.mjs
node scripts/run-e2e.mjs
node scripts/verify-local-stack.mjs
git diff --check
```

The last script restarts **only this project's backend**, temporarily stops **only this project's database**, checks liveness/readiness/API/UI failure behavior, restores the database, and verifies persisted identity. It never deletes a volume. Run against the initialized local stack; it must not target production infrastructure.

[Phase 1 results and CI evidence](docs/development/PHASE_1_VERIFICATION.md) distinguish passed tests, corrected failures and remaining checks. [Project status](PROJECT_STATUS.md) is the completion authority.

## Independent demo sources

The demo lifecycle is separate from the platform. Sales has 34 products/144 orders/432 lines; inventory has 30 SKUs/3 warehouses/90 stock rows; supplier has 10 suppliers/40 offers. No platform integration is registered.

```powershell
node scripts/init-demo.mjs
docker compose --env-file infra/docker/demo.env -f infra/docker/demo-systems.compose.yaml config --quiet
docker compose --env-file infra/docker/demo.env -f infra/docker/demo-systems.compose.yaml up --build -d --wait --wait-timeout 240
```

All ports are localhost-only: sales 55433, inventory 53307 and supplier API 18081. Unique local reader passwords/token stay in ignored configuration. [Demo setup and verification commands](docs/development/DEMO_SYSTEMS_SETUP.md), [data dictionary](docs/architecture/DEMO_DATA_DICTIONARY.md) and [API contract](docs/architecture/DEMO_API_CONTRACT.md) explain access and semantics.

[Dataset v1](docs/research/DATASET_SPECIFICATION.md) has 30 development cases (three per ten categories), six separate held-out cases, external reviewed synthetic mappings and independently computed expected facts with immutable hashes. [Phase 2 verification](docs/development/PHASE_2_VERIFICATION.md) and [acceptance](docs/development/PHASE_2_ACCEPTANCE.md) record passed local verification and both successful jobs in [actual CI run 41061522829](https://github.com/ChouaibSeishin/EcoSync/actions/runs/41061522829). No benchmark or model evaluation has run.

## Architecture and next work

- `backend/`: Spring Boot 4.1.2, Spring Security, JPA, JDBC and Flyway; identity, organizations, authorization, web and observability modules. Spring AI 2.0.2 BOM only; no runtime AI/MCP dependencies.
- `frontend/`: React 19.3.1, strict TypeScript 6.0.3, Vite 8.3.5, React Query, React Router, Radix dialog and CSS tokens.
- `infra/docker/`: digest-pinned platform PostgreSQL, one-shot migration/bootstrap, backend and frontend; localhost ports and a persistent named volume.
- `.github/workflows/ci.yml`: actual build/test/browser/runtime workflow with verified action SHAs and read-only repository permissions; no publishing/deployment credentials.
- [Version pins](config/versions.json), [image pins](config/container-images.json), [compatibility and official sources](docs/architecture/COMPATIBILITY.md), [architecture](docs/architecture/ARCHITECTURE.md), [security](docs/architecture/SECURITY.md), [data model](docs/architecture/DATA_MODEL.md).
- [Roadmap](ROADMAP.md), [backlog](BACKLOG.md), [Phase 1 identity/runtime decision](docs/architecture/decisions/0006-phase1-identity-and-local-stack.md), [research methodology](docs/research/METHODOLOGY.md), [deployment design](docs/deployment/DEPLOYMENT.md), [historical Phase 0 report](docs/development/PHASE_0_VERIFICATION.md).

Phase 2 is the authorized scope. Phase 3 starts only after explicit authorization, beginning with the MCP/SDK compatibility gate. Preserve the existing phase branches and keep local credentials out of Git.
