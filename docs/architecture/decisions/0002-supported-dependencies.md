# ADR 0002: Supported baseline and managed dependency versions

Date: 2026-10-29. Status: accepted design, runtime proof pending feature phases.

## Context

The brief prefers Java 21 and JUnit 5, with versions verified against current official documentation. Old Spring tutorials no longer describe the currently supported release pair. See the primary-source [compatibility assessment](../COMPATIBILITY.md).

## Decision

Retain Java release 21 and target Temurin 21.0.13+9. Pin Boot 4.1.2 and Spring AI BOM 2.0.2, matching the exact AI parent release; inherit SDK 2.1.0 and Boot-managed versions. Use Maven 3.9.16 wrapper in Phase 1 and springdoc 3.2.0. Follow the Boot 4 modular starter/Jackson 3 conventions rather than mixing examples across majors.

Use Boot-managed **JUnit Jupiter 6.0.3** rather than the brief's JUnit 5 preference. This is a deliberate compatibility deviation: starting on the supported Spring baseline avoids adopting the now-ended Boot 3.5 OSS line or overriding the framework's tested test dependency management. Jupiter tests remain the intended style; do not introduce JUnit Vintage without a concrete need.

Pin Node 24.22.2/npm 11.19.1, React/DOM 19.3.1, Vite 8.3.5/plugin-react 6.1.2, TypeScript 6.0.3 and the exact ancillary packages in versions.json. Reject current TypeScript 7 for now because the selected maintained ESLint adapter declares `<6.1.0`. Revisit only as a coordinated peer-compatible upgrade.

## Consequences and alternatives

Boot 3.5/AI 1.1 would reduce tutorial differences but starts after Boot 3.5 OSS support ended. Boot 4.2/AI 2.1 preview releases do not meet the stable-release goal. Newer major versions alone are not sufficient grounds to upgrade. The installed Node 20.17 must change for Phase 1 application tooling; Phase 0's dependency-free checker can still use it. No global toolchain modification is part of this decision.

Official metadata and dependency graph resolution do not prove application runtime correctness. Phase 1 must build/start/test the application; Phase 3 must test real MCP negotiation; Phase 4-5 must test actual connectors. Container image digests, action SHAs, infrastructure tool patches and an actual model artifact are selected before those artifacts are first implemented.
