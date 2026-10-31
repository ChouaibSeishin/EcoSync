# Project status

Updated: 2026-10-30 UTC. Authorized scope: **Phase 2 only**.

## Current delivery state

Phase 0 remains accepted at 6a1749f40d394933c0e4b421048eab96818da436. Phase 1 is complete at 603cfb993566f465d0c3328f175d71b27f6a4942 with both accepted CI runs preserved. **Phase 2 is complete.** Checkpoints A-G passed locally and in actual [GitHub Actions run 41061522829](https://github.com/ChouaibSeishin/EcoSync/actions/runs/41061522829) at code commit 002cdd6348dd1f39481255ae4472d66a967ce559; both the unchanged Phase 1 verify job and the independent demo job succeeded. See [Phase 2 verification](docs/development/PHASE_2_VERIFICATION.md) and [acceptance](docs/development/PHASE_2_ACCEPTANCE.md).

Work branch: codex/phase-2-demo-ecosystem, based on the exact verified Phase 1 final commit. Both prior branches remain unchanged. Repository visibility is intentionally public and has not been changed. No unrelated starting changes existed.

## Delivered systems

The Phase 1 Spring MVC/PostgreSQL identity/workspace application and React UI remain unchanged. Local workspace: http://127.0.0.1:5173, platform API: localhost:18080, metadata PostgreSQL: localhost:55432. [Phase 1 evidence](docs/development/PHASE_1_VERIFICATION.md) documents security, persistence, browser checks and historical corrections.

Phase 2 adds separately owned PostgreSQL sales (34 products, 18 anonymous customers, 144 orders, 432 lines), MySQL inventory (30 SKUs, three warehouses, 90 stock rows), and a Node 24/TypeScript/Fastify supplier API (10 suppliers, 40 offers). They have independent contracts, processes, credentials and lifecycle; the two databases have separate persistent volumes and restricted readers. [Demo setup](docs/development/DEMO_SYSTEMS_SETUP.md) gives reproducible commands and ports.

Versioned external mappings preserve 22 approved sales identities, 27 catalog memberships and unresolved/conflicting/incompatible cases. Dataset v1.0.0 has 30 development and six separate held-out cases, explicit independent expected facts/provenance and 27 immutable SHA-256 artifact hashes. Two fresh projects with different random credentials produced identical actual business snapshots. Database permission/constraint tests and real HTTP contract tests passed. This is dataset preparation; no scientific performance result exists.

## Limits and next boundary

Local Compose/HTTP and the Vite frontend are development tooling. Source migrations initialize empty fixture volumes; checksum verification detects drift, with no automatic live fixture upgrade. Supplier storage is a small immutable JSON snapshot. Phase 1 sessions/throttling remain single-instance/in-memory. Point-in-time npm audits and source checks do not establish production readiness.

There are no MCP clients/servers/gateway, platform database/API connectors, integration onboarding, AI/model calls, chat, mapping management UI, benchmark execution, Kubernetes/Argo or GHCR publishing. The next authorized starting point, only after explicit permission, is P3-01: refresh official MCP/SDK compatibility and prove real protocol negotiation/security against independently deployed native servers. Phase 3 has not started.

## Final Phase 1 handoff addendum (reconciled on Phase 2 branch)

Application implementation was fully verified at de83c20e94c90c42475133217fe6d06b5afc7c9b by [run 41047054438](https://github.com/ChouaibSeishin/EcoSync/actions/runs/41047054438). The later 603cfb993566f465d0c3328f175d71b27f6a4942 change clarified optional host-development documentation; it did not change application code. The final Phase 1 branch commit was itself fully verified by successful [run 41048264591](https://github.com/ChouaibSeishin/EcoSync/actions/runs/41048264591). Both actual run heads/statuses were rechecked through GitHub API. Phase 1 is complete. Historical failure/correction notes remain in its verification record; Phase 1 history/branch is not rewritten.

## Final Phase 2 branch-head handoff addendum (2026-10-31)

Phase 2 application code was accepted at 002cdd6348dd1f39481255ae4472d66a967ce559 by successful [run 41061522829](https://github.com/ChouaibSeishin/EcoSync/actions/runs/41061522829). The subsequent a38682428457cca417cbac8f20bc14e7c0792f21 commit updated documentation only. Its own complete [branch-head run 41062443616](https://github.com/ChouaibSeishin/EcoSync/actions/runs/41062443616) passed both verify and Independent demo sources and dataset jobs. Exact head/status/job conclusions were rechecked through GitHub API on 2026-10-31. This addendum completes the previously separate final-head report; it does not replace the code acceptance evidence or remove the initial failed run 41060229162 and its initialization portability correction.

Phase 3 work is based on the exact verified a386824 commit on codex/phase-3-native-mcp. Earlier phase branches, public visibility and default-branch settings are preserved.
