# Phase 4 acceptance

Phase 4 is complete at application head `18aafb7d41011171e64da25e14cc1bdf363fc533` on `codex/phase-4-managed-db-connectors`. [Actual run 41812225715](https://github.com/ChouaibSeishin/EcoSync/actions/runs/41812225715) passed all four jobs. Phase 5/6 remain unstarted.

The branch began at exact accepted Phase 3 head `0e681ab5cb9a1930a159d3b459d9380fe660a363`. Phase 3 handoff reconciliation was committed separately before the 11-check actual compatibility proof and ADR 0012; full implementation followed that gate. [Sanitized CI evidence](evidence/phase-4-code-ci.json) records inspected job IDs, conclusions and counts.

| Checkpoint | Accepted evidence |
|---|---|
| P4-01 | 11 actual authenticated Java/TypeScript wire checks, runtime mutations/failures, reconstruction and tenant isolation; ADR 0012 |
| P4-02 | Additive V4 immutable tenant registry, AES-GCM credentials, operator destinations and signed delegated identity; 10 unit/38 PostgreSQL integration tests |
| P4-03 | Actual bounded JDBC discovery/fingerprints, explicit role/table/column scope and stale-scope fencing; real schema changes tested only on disposable fixtures |
| P4-04 | Prepared single-table typed reads, allowlisted dialect identifiers, actual SELECT-only grants; 17 unit/10 real PostgreSQL-MySQL integration tests, including byte/row/deadline recovery |
| P4-05 | Private managed gateway, fresh authorization, neutral catalog, approval/revision epochs, bounded pools, concurrent cancellation/disable and active/in-flight/disabled restart reconstruction |
| P4-06 | Real React management/explorer/history, 22 component tests, lint/types/build, two actual database browser journeys and two native regression journeys |
| P4-07 | 58-case local suite plus three final recovery checks; all 58 latest checks in CI, zero skips, including both original source databases, dynamic second-table approval, adversarial denials with zero protected SQL, outages, keys/network and known-secret review |
| P4-08 | All four actual CI jobs passed; original three job bodies/checks preserved, feature remote SHA verified, documentation and meaningful incremental commits |

The foundation job passed 10 unit/38 PostgreSQL integration/22 frontend tests plus browser/persistence/outage checks. Independent sources passed 14 supplier tests, real reader/constraint/lifecycle checks, 36 immutable dataset cases/oracle/hash verification and two independent fresh initializations. Native MCP passed 25 client tests, the dual-era matrix, 30 actual stack checks and two browsers. The new job passed the counts above and the final source-fixture/documentation/secret review. No test was skipped; failure-only diagnostic steps were unnecessary in the successful run.

[Verification](PHASE_4_VERIFICATION.md) retains the initial failed CI run, missing host AJV install correction, Windows Docker/Testcontainers failures and CLI teardown correction. [Actual local stack evidence](evidence/phase-4-stack.json), [setup](DATABASE_LOCAL_SETUP.md), [architecture](../architecture/DATABASE_CONNECTORS.md) and [security](../architecture/DATABASE_SECURITY.md) document reproduction, manual facts and limits. Later documentation-only heads require their own actual CI run and are reported in the final handoff; they do not replace this application acceptance record.

Gateway protocol is **MCP 2025-11-25 only**, using stable Java SDK 2.1.0. Stable SDK broadcast notifications are disabled in the tenant-filtered production catalog; refresh explicitly. Engines are PostgreSQL 17.11/MySQL 8.4.13, drivers pgJDBC 42.7.14/Connector-J 26.10.0. Native Phase 3 protocol claims and limitations remain.

Existing source business schemas/data, old migrations, named volumes, bootstrap identity, generated credentials and external keys are preserved. The user's OpenAPI whitespace edit is excluded from commits and restored at handoff. No earlier branch/history, public visibility or default branch is changed. One backend replica is supported; local Docker/CI do not imply production readiness, horizontal scaling, exhaustive MCP conformance or performance claims. Cancellation cannot undo a completed remote read. REST onboarding, AI/chat, general mappings/joins, arbitrary SQL/writes, unsupported drivers and deployment automation are outside this phase. Phase 5/6 require explicit authorization.
