# Ecosync AI

An AI interoperability platform for discovering approved capabilities across independent databases, REST APIs, and MCP servers, answering questions with traceable source evidence.

Research topic: **AI-Driven Interoperability: Exploring the Role of Large Language Models and the Model Context Protocol in Digital Ecosystem Integration.**

## Current state

Phase 0 establishes the architecture, security model, version baseline, research design, and implementation plan. There is no application, running chat, connector, database migration, deployment, or CI workflow yet. Compatibility probes are design verification tools, not application modules.

The existing private repository is [ChouaibSeishin/EcoSync](https://github.com/ChouaibSeishin/EcoSync). The product name remains Ecosync AI; no replacement repository is needed.

## Start here

- [Project status and verification](PROJECT_STATUS.md)
- [Roadmap and phase acceptance criteria](ROADMAP.md)
- [Phase 1 implementation backlog](BACKLOG.md)
- [Architecture and proposed repository structure](docs/architecture/ARCHITECTURE.md)
- [Dependency compatibility and official sources](docs/architecture/COMPATIBILITY.md)
- [Security and threat model](docs/architecture/SECURITY.md)
- [Initial data model](docs/architecture/DATA_MODEL.md)
- [Local prerequisites and planned development workflow](docs/development/LOCAL_SETUP.md)
- [Deployment and GitOps design](docs/deployment/DEPLOYMENT.md)
- [Research methodology](docs/research/METHODOLOGY.md)
- [Phase 0 verification report](docs/development/PHASE_0_VERIFICATION.md)

## Design baseline

Java 21, Spring Boot 4.1.2, Spring AI 2.0.2, React 19.3.1, TypeScript 6.0.3, Vite 8.3.5, Node.js 24.22.2, and PostgreSQL 17.11. Exact pins are in [config/versions.json](config/versions.json). Boot-managed JUnit Jupiter 6 replaces the brief's JUnit 5 preference for compatibility with the supported Spring baseline; see [ADR 0002](docs/architecture/decisions/0002-supported-dependencies.md).

The main platform starts as a modular monolith. Independently deployable demo systems and MCP servers demonstrate actual heterogeneity. Existing MCP servers and managed database/API connectors have distinct configuration workflows. The managed gateway will expose approved connector capabilities through actual MCP calls. MCP does not establish semantic equivalence between source identifiers.

## Verify this foundation

With an available Node.js runtime (20.17+ is sufficient for this documentation checker):

```powershell
node scripts/verify-foundation.mjs
git diff --check
```

Application tooling requires the pinned Node 24 baseline. See [local setup](docs/development/LOCAL_SETUP.md) for compatibility probes and Phase 1 prerequisites. There is no application start command until Phase 1 is implemented.

## Working agreement

Implement one authorized phase at a time. Inspect before editing; preserve unrelated work; test real behavior; record failures and skipped checks. Update status, roadmap, backlog, and ADRs with evidence. Keep secrets, external personal data, and generated credentials out of Git. Do not claim production readiness or research outcomes from design documents or model-generated examples.
