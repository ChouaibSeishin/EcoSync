# Project status

Updated: 2026-10-29 UTC. Current authorized scope: **Phase 0 only**.

## Delivery state

Phase 0 foundation is written; final verification and Git recording are in progress. Phases 1-11 are unimplemented. No application, source connector, MCP server, database schema, CI workflow, container image, benchmark result or deployment is being claimed.

The private repository origin was verified as `https://github.com/ChouaibSeishin/EcoSync.git`. Inspection found a new local repository with no commits and no remote refs. No existing project files, history or user changes needed preservation. No applicable AGENTS.md was found in the inspected ancestor/workspace paths.

## Delivered foundation

- Architecture with explicit module contracts, real MCP gateway path, integration lifecycle and provenance.
- Initial tenant-scoped data model and threat/control/test matrix.
- Dated official-source compatibility assessment, exact version manifest and five ADRs.
- Phase acceptance criteria, prioritized Phase 1 backlog, local and GitOps design.
- Research hypotheses, controlled baseline, metric definitions and reproducibility plan.
- Dependency-free documentation checker and disposable source-free dependency probe generator.

## Verification and limitations

See [Phase 0 verification report](docs/development/PHASE_0_VERIFICATION.md) for actual commands and results. Version pins are design decisions backed by official documentation/package metadata; application runtime verification remains a later-phase requirement.

Installed Node 20.17 is unsuitable for the chosen frontend stack. Phase 1 must use Node 24.22.2/npm 11.19.1 and a maintained JDK 21 patch; Maven wrapper pins 3.9.16. Docker responds in the full user context; the restricted context denied its pipe/config. Existing external Kubernetes context presence does not establish connectivity or deployment permission. No cloud cluster was contacted.

MCP SDK 2.1.0 lists protocol constants through 2025-11-25. Current specification 2026-07-28 compatibility remains explicitly unverified; P3-01 must prove interoperability or update the coordinated dependency baseline. JUnit Jupiter 6 is the documented compatibility deviation from the brief's JUnit 5 preference. Model artifact selection and infrastructure tool/image-digest pins occur before their first implementation.

## Next authorized-work boundary

Await authorization for Phase 1 after Phase 0 reporting. Start with P1-01 toolchain/wrapper/lockfile, then P1-02 backend skeleton and P1-03 real PostgreSQL/Flyway baseline. Follow [BACKLOG.md](BACKLOG.md) through the real frontend/Compose/CI acceptance evidence. Do not begin those implementation tasks during Phase 0.
