# Phase 4 acceptance

Branch: `codex/phase-4-managed-db-connectors`, created from exact Phase 3 head `0e681ab5cb9a1930a159d3b459d9380fe660a363`. Phase 3 reconciliation was committed first. **Final acceptance is pending actual final-head GitHub Actions.**

Dynamic PostgreSQL/MySQL registration, actual bounded JDBC metadata, explicit per-role columns, immutable configuration/credential revisions, typed prepared single-table reads, private managed MCP discovery/calls, restart/revocation/cancellation fencing and real React management/explorer/history are implemented. Another permitted connector/table requires configuration/review without recompiling core or frontend.

| Evidence | Observed result |
|---|---|
| P4-01 before implementation | 11 actual authenticated wire checks; ADR 0012 accepted separately |
| Registry/crypto/governance | 10 unit and 38 real PostgreSQL integration tests, zero skips |
| Gateway query/destination/security | 17 unit tests; expanded real-engine verification pending |
| Original independent Sales/Inventory | 52 actual stack checks; final concurrent expansion pending |
| Existing React and actual source browsers | 22 component tests and two database browser journeys |
| Prior phases and final remote head | Three jobs preserved, dedicated fourth added; final run pending |

[Verification](PHASE_4_VERIFICATION.md) retains failures/corrections and actual final counts. [Setup](DATABASE_LOCAL_SETUP.md), [architecture](../architecture/DATABASE_CONNECTORS.md) and [security](../architecture/DATABASE_SECURITY.md) define the supported workflow, limits and trust boundary.

Gateway protocol is MCP 2025-11-25 through stable Java SDK 2.1.0; production list-change broadcasts are disabled for tenant privacy. Engines are PostgreSQL 17.11/MySQL 8.4.13, drivers pgJDBC 42.7.14/Connector-J 26.10.0. Native Phase 3 claims/limits remain.

Existing source business schemas/data, migrations, volumes, bootstrap identity, credentials and keys are preserved. The user's OpenAPI whitespace edit is excluded from commits and retained. One backend replica is supported; Docker/CI are not production certification, horizontal scalability, exhaustive MCP conformance or performance evidence. REST onboarding, AI/chat, mappings, arbitrary SQL/writes, unsupported drivers and deployment automation remain outside scope. Phase 5/6 require explicit authorization.
