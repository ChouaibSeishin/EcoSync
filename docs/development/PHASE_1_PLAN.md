# Phase 1 implementation plan

Authorized 2026-10-30. Base verified: `6a1749f40d394933c0e4b421048eab96818da436` on local/remote `codex/phase-0-foundation`. Clean working tree before changes. Work branch: `codex/phase-1-foundation`; Phase 0 will not be rewritten or merged automatically. GitHub's authenticated repository API currently reports **public** visibility; no visibility change is part of this work.

1. **A / P1-01:** verify portable Node/JDK distributions, pin wrapper/checksum, create actual frontend package/lock, strict clean install, inspect diff, commit.
2. **B / P1-02-03:** Spring MVC/health/errors and Flyway/JPA organization/user/membership/role metadata, separate migration/runtime roles and digest-pinned PostgreSQL; compile and real PostgreSQL tests.
3. **C / P1-04:** Spring Security session/form authentication, CSRF endpoint, bounded login throttle, one-time administrator bootstrap and tenant/role checks; negative security tests.
4. **D / P1-05-06:** accessible responsive React application, real status/identity APIs, honest unavailable features, Compose with localhost ports and Vite proxy; real browser and persistence checks.
5. **E / P1-07-08:** unit, integration and browser suites, verified SHA-pinned read-only CI; inspect actual GitHub Actions run.
6. **F / P1-09:** source/secret/diff review, status/backlog/architecture/setup updates and actual verification record, incremental commits and verified Phase 1 branch push.

No later-phase integration, AI, source system, publishing or deployment features will be implemented. Existing Phase 0 probe evidence remains historical. New test/tool dependencies must be pinned from official metadata. Failed checks and environmental blockers will be recorded with their resolution or remaining limitation.
