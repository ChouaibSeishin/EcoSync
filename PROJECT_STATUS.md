# Project status

2026-11-07: Phase 6 is authorized and in progress on `codex/phase-6-llm-cross-system-chat`, from exact Phase 5 final head `7afd445dd2690505d8da2a1e421dc941d66823ad`. Actual final run 42021478856 passed all five jobs. The older Phase 5 application head/run below remain historical evidence. [ADR 0014](docs/architecture/decisions/0014-controlled-chat-orchestration.md) and [Phase 6 verification](docs/development/PHASE_6_VERIFICATION.md) track current checkpoints; no completion or Phase 7 claim. Required containers were restarted; movies remains preserved.

Updated 2026-11-05 UTC: Phase 5 dynamic REST implementation is delivered on `codex/phase-5-dynamic-rest-connectors`, based on exact Phase 4 final head `ec310f8c25b4778396bdc5f698f3ef7253967401`. Application acceptance: `4c5c7404fedfc1abe1e1a1d99b40e02441e437b0`, [five-job run 42018755865](https://github.com/ChouaibSeishin/EcoSync/actions/runs/42018755865), all successful. Final documentation/guard commit verification is separately recorded in the final handoff; this application run does not prove a newer head. See [Phase 5 acceptance](docs/development/PHASE_5_ACCEPTANCE.md) and [verification](docs/development/PHASE_5_VERIFICATION.md). Phase 6 remains unstarted; no merge is authorized. Existing movies access and source volumes are preserved.

Historical Phase 4 application acceptance (2026-11-04 UTC): **Phase 4 is complete at application head `18aafb7d41011171e64da25e14cc1bdf363fc533` on `codex/phase-4-managed-db-connectors`. [Actual run 41812225715](https://github.com/ChouaibSeishin/EcoSync/actions/runs/41812225715) passed all four jobs. Phase 5/6 remain unstarted.** The exact Phase 3 base remains `0e681ab5cb9a1930a159d3b459d9380fe660a363` with final run 41619804974. Earlier phase acceptance/failure records are retained below.

## Current delivery state

Phase 0 remains accepted at 6a1749f40d394933c0e4b421048eab96818da436. Phase 1 is complete at 603cfb993566f465d0c3328f175d71b27f6a4942 with both accepted CI runs preserved. **Phase 2 is complete.** Checkpoints A-G passed locally and in actual [GitHub Actions run 41061522829](https://github.com/ChouaibSeishin/EcoSync/actions/runs/41061522829) at code commit 002cdd6348dd1f39481255ae4472d66a967ce559; both the unchanged Phase 1 verify job and the independent demo job succeeded. See [Phase 2 verification](docs/development/PHASE_2_VERIFICATION.md) and [acceptance](docs/development/PHASE_2_ACCEPTANCE.md).

Current work branch: codex/phase-5-dynamic-rest-connectors, based on exact Phase 4 final head ec310f8c25b4778396bdc5f698f3ef7253967401. Phase 4 final handoff [run 41814237164](https://github.com/ChouaibSeishin/EcoSync/actions/runs/41814237164) passed all four jobs; the older application acceptance head/run remain historical evidence. All previous phase branches remain unchanged. Repository visibility is intentionally public and has not been changed. One pre-existing supplier OpenAPI whitespace edit was preserved separately and will be restored at handoff; it is excluded from implementation commits.

## Delivered systems

The Phase 1 Spring MVC/PostgreSQL identity, session, tenant and least-privilege boundaries remain; Phase 3 extends the React UI with native MCP management. Local workspace: http://127.0.0.1:5173, platform API: localhost:18080, metadata PostgreSQL: localhost:55432. [Phase 1 evidence](docs/development/PHASE_1_VERIFICATION.md) documents security, persistence, browser checks and historical corrections.

Phase 2 adds separately owned PostgreSQL sales (34 products, 18 anonymous customers, 144 orders, 432 lines), MySQL inventory (30 SKUs, three warehouses, 90 stock rows), and a Node 24/TypeScript/Fastify supplier API (10 suppliers, 40 offers). They have independent contracts, processes, credentials and lifecycle; the two databases have separate persistent volumes and restricted readers. [Demo setup](docs/development/DEMO_SYSTEMS_SETUP.md) gives reproducible commands and ports.

Versioned external mappings preserve 22 approved sales identities, 27 catalog memberships and unresolved/conflicting/incompatible cases. Dataset v1.0.0 has 30 development and six separate held-out cases, explicit independent expected facts/provenance and 27 immutable SHA-256 artifact hashes. Two fresh projects with different random credentials produced identical actual business snapshots. Database permission/constraint tests and real HTTP contract tests passed. This is dataset preparation; no scientific performance result exists.

## Limits and next boundary

Local Compose/HTTP and the Vite frontend are development tooling. Source migrations initialize empty fixture volumes; checksum verification detects drift, with no automatic live fixture upgrade. Supplier storage is a small immutable JSON snapshot. Phase 1 sessions/throttling remain single-instance/in-memory. Point-in-time npm audits and source checks do not establish production readiness.

Phase 3 checkpoints A-H passed: dual-era official-SDK client, two source-backed native servers, persistent versioned registry, encrypted credentials, explicit role approvals, manual invocation UI and 30 actual stack checks. All three jobs passed at 794b267 in [run 41615632337](https://github.com/ChouaibSeishin/EcoSync/actions/runs/41615632337); the final handoff revision runs the same checks. See [Phase 3 acceptance](docs/development/PHASE_3_ACCEPTANCE.md) and [verification](docs/development/PHASE_3_VERIFICATION.md). The user-stopped services were restarted on November 3 and left healthy. Phase 4 now implements the managed gateway and dynamic PostgreSQL/MySQL connectors. Phase 5 now delivers dynamic REST onboarding through a separate isolated gateway. AI/model calls, chat, mapping management UI, benchmark executor, Kubernetes/Argo and publishing remain unimplemented.

## Final Phase 1 handoff addendum (reconciled on Phase 2 branch)

Application implementation was fully verified at de83c20e94c90c42475133217fe6d06b5afc7c9b by [run 41047054438](https://github.com/ChouaibSeishin/EcoSync/actions/runs/41047054438). The later 603cfb993566f465d0c3328f175d71b27f6a4942 change clarified optional host-development documentation; it did not change application code. The final Phase 1 branch commit was itself fully verified by successful [run 41048264591](https://github.com/ChouaibSeishin/EcoSync/actions/runs/41048264591). Both actual run heads/statuses were rechecked through GitHub API. Phase 1 is complete. Historical failure/correction notes remain in its verification record; Phase 1 history/branch is not rewritten.

## Final Phase 2 branch-head handoff addendum (2026-10-31)

Phase 2 application code was accepted at 002cdd6348dd1f39481255ae4472d66a967ce559 by successful [run 41061522829](https://github.com/ChouaibSeishin/EcoSync/actions/runs/41061522829). The subsequent a38682428457cca417cbac8f20bc14e7c0792f21 commit updated documentation only. Its own complete [branch-head run 41062443616](https://github.com/ChouaibSeishin/EcoSync/actions/runs/41062443616) passed both verify and Independent demo sources and dataset jobs. Exact head/status/job conclusions were rechecked through GitHub API on 2026-10-31. This addendum completes the previously separate final-head report; it does not replace the code acceptance evidence or remove the initial failed run 41060229162 and its initialization portability correction.

Phase 3 work is based on the exact verified a386824 commit on codex/phase-3-native-mcp. Earlier phase branches, public visibility and default-branch settings are preserved.

## Phase 4 delivery and acceptance

P4-01 through P4-06 are implemented: actual stable gateway proof/ADR, additive V4 tenant-bound immutable registry, AES-GCM credential lifecycle, Ed25519 delegation, real JDBC discovery/fingerprints, explicit per-role columns, bounded prepared reads, private pools/lifecycle and React registration/explorer/history.

Final checks passed 10 backend unit/38 PostgreSQL integration, 17 gateway unit/10 real PostgreSQL-MySQL integration, 22 frontend, 11 wire proof, 58 actual stack checks and two database plus two native browser journeys, with zero skipped tests. All four actual CI jobs passed at the application head recorded above. [Acceptance](docs/development/PHASE_4_ACCEPTANCE.md) and [verification](docs/development/PHASE_4_VERIFICATION.md) track the final evidence and retained failures. Phase 5/6 remain unstarted.

## Phase 5 delivery

JSON OpenAPI 3.0/3.1 subset import, immutable REST registry/V5, separate AES-GCM credential purpose, explicit per-role GET approvals, isolated pinned-DNS/TLS HTTP transport, fresh signed MCP delegation, bounded execution and React management/explorer/history are implemented. Supplier and the independent Partner Directory are onboarded without API-specific orchestrator code. Observed evidence: 25 REST gateway tests, 16 real REST PostgreSQL tests within 54 backend integration tests, 27 frontend tests, three Partner HTTP tests, 54 live REST checks and two REST browsers. The final constant-regex guard raises backend unit tests from the acceptance run's 24 to 25; its own final-head CI is required.

Limits: GET only, finite JSON contract subset, explicit operator destinations, private local fixtures, no production per-port firewall claim, one authority replica and no LLM/chat. See [REST security](docs/architecture/REST_SECURITY.md), [ADR 0013](docs/architecture/decisions/0013-isolated-rest-egress.md) and [startup/verification](docs/development/REST_LOCAL_SETUP.md). Older phase sections describe historical acceptance, not current exclusions.
