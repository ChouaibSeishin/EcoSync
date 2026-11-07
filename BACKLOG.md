# Backlog

## Phase 6 ordered checkpoints (authorized 2026-11-07)

Exact base `7afd445dd2690505d8da2a1e421dc941d66823ad`, branch `codex/phase-6-llm-cross-system-chat`. The following implementation checkpoints are delivered; live/browser/final-head verification is recorded separately in [Phase 6 verification](docs/development/PHASE_6_VERIFICATION.md). Historical earlier acceptance records below are preserved.

| Checkpoint | Delivered behavior and acceptance evidence |
|---|---|
| P6-A/B | ADR 0014, official compatibility/advisory review, resolved Spring AI 2.0.2 graph, optional digest-pinned local model experiment |
| P6-C | V6 tenant/owner conversations, ordered messages, idempotency, archive, cancellation and restart fencing |
| P6-D/E | Fresh per-user capability catalog, central proposal validation, existing MCP transports, 30-second/eight-call shared budget and bounded evidence |
| P6-F/G | Exact evidence references, derivation lineage, BigDecimal arithmetic, verified canonical sources and byte-preserved unique approved mappings 1.0.0 |
| P6-H | Untrusted prompts/results, structural final-answer validation, current-grant history filtering and injection/identity/URL/SQL rejection tests |
| P6-I/J | Persistent React chat, provider/test-double labels, actual source-backed development examples and provenance |
| P6-K/L | PostgreSQL authority tests, live MCP/source-counter races, real browsers, AI-disabled persistence, dedicated sixth CI job and exact remote-head review |

Phase 7 general mapping management and Phase 9 scientific evaluation are not started. No merge/default/history change is authorized.

## Phase 5 ordered checkpoints (authorized 2026-11-05)

Base: `ec310f8c25b4778396bdc5f698f3ef7253967401`, final Phase 4 run 41814237164. Historical application acceptance below is unchanged. P5-A through P5-I passed the evidence in [Phase 5 verification](docs/development/PHASE_5_VERIFICATION.md). P5-J application acceptance passed all five jobs in run 42018755865; the later documentation/guard commit requires its own final-head run, reported at handoff.

| Checkpoint | Dependency | Required exit evidence |
|---|---|---|
| P5-A | Exact base | Reconcile handoff, inspect runtime/network/governance, ADR 0013, preserve user edit and old CI jobs |
| P5-B | A | Strict JSON OpenAPI 3.0/3.1 subset importer, local references, complexity bounds, normalized review/fingerprints and adversarial tests |
| P5-C | B | Additive Flyway registry, immutable revisions, separate encrypted REST purpose, tenant/role/approval/revocation integration tests |
| P5-D | A,C | Separate private REST gateway, exact operator destinations, full DNS validation and pinned TLS sockets, redirects denied |
| P5-E | B,C,D | Typed bounded GET execution, schema validation, classified failures, cancellation and current-authority fencing |
| P5-F | C,E | Actual official-SDK generic MCP discovery/invocation, separate audience, single-use argument-bound delegation |
| P5-G | B,C,F | Accessible existing React integration management and bounded explorer, real state/history and secret clearing |
| P5-H | D,G | Independent Supplier and Partner APIs onboarded through the same workflow, no API-specific backend adapters |
| P5-I | All above | Importer/tenant/SSRF/DNS/HTTP/MCP/live-stack/browser negative and recovery tests; all Phase 1–4 regressions |
| P5-J | I | Dedicated fifth CI job, exact final remote SHA/run/counts, documentation/evidence/security review, no merge or Phase 6 |

Phase 4 is complete at application head `18aafb7d41011171e64da25e14cc1bdf363fc533` on `codex/phase-4-managed-db-connectors`. [Actual run 41812225715](https://github.com/ChouaibSeishin/EcoSync/actions/runs/41812225715) passed all four jobs. This historical Phase 4 record is preserved; Phase 5 delivery is recorded above and Phase 6 remains unstarted. P4-01 through P4-08 are complete with actual evidence. Historical earlier work-package descriptions below remain as the project record.

## Phase 1: ordered implementation plan

Phase 4 ordered checkpoints (new authorization, 2026-11-03): P4-01 real authenticated gateway compatibility/tenant-isolation proof and ADR; P4-02 additive connector persistence, encrypted credentials and destination policy; P4-03 JDBC metadata and explicit fingerprint-bound scope approval; P4-04 bounded prepared single-table reads for both dialects with separate real DB reader grants; P4-05 managed gateway lifecycle, delegated authorization and restart reconstruction; P4-06 real accessible React management/explorer; P4-07 adversarial/live-stack/browser verification; P4-08 preserve all three previous CI jobs, add a dedicated job, inspect actual final remote head/run and complete documentation. Each checkpoint requires evidence before being reported complete.

| ID / priority | Work and affected paths | Dependencies | Acceptance and meaningful verification |
| --- | --- | --- | --- |
| P1-01 / P0 | Establish pinned toolchains and build wrappers: backend Maven wrapper, frontend package/lockfile, Node version file, developer setup | Phase 0 | Refresh official metadata and security advisories; update JDK 21 patch and Node 24; Maven 3.9.16 wrapper with distribution checksum; exact npm deps and committed lockfile; clean dependency install using npm ci; fail unsupported engines; no global host upgrades without need |
| P1-02 / P0 | Build `backend/` Spring MVC skeleton under `io.ecosync`, configure Boot parent, module package conventions, Actuator and consistent API error format | P1-01 | Java 21 compile/test/package pass; readiness distinguishes unavailable DB from healthy process; public health reveals no internals; malformed request and unexpected error do not reveal stack traces/configuration; no AI/MCP auto-connect needed |
| P1-03 / P0 | `infra/docker/` platform PostgreSQL and backend Flyway baseline; JPA metadata persistence; initial org/user/role model | P1-02 | Fresh database migration works; rerun is stable; versioned checksum tracked; Testcontainers PostgreSQL test verifies schema and constraints; no H2 substitute; credentials external to source; platform account cannot read future demo databases |
| P1-04 / P0 | Minimum identity/session protections and controlled admin bootstrap in backend | P1-03 | Same-origin session auth, hash local passwords, CSRF token API, login throttling, session rotation/logout; initial admin created only via explicit one-time local provisioning input; no shipped default password; tests reject anonymous protected access, missing CSRF, wrong role and cross-org lookup; reserve full user management for later |
| P1-05 / P1 | `frontend/` strict TypeScript React/Vite shell with accessible navigation, login, dashboard, empty integrations list, system status and honest AI-unavailable state | P1-01, P1-02, P1-04 | ESLint/typecheck/Vitest/production build pass; React Query for server state; Radix dialog plus CSS tokens; router; keyboard/focus/labels and responsive layout reviewed; errors/loading/retry tested; no fake integration counts or functioning-chat claims |
| P1-06 / P0 | Connect actual UI/backend/DB through development proxy and Compose | P1-03, P1-04, P1-05 | Compose config validates; stack starts without LLM credentials; browser login/status journey uses real backend and persists state after service restart; CSRF/cookie path works through proxy; DB failure visible; ports bound to localhost in dev; no volume deletion required |
| P1-07 / P1 | `scripts/` verification commands and backend/frontend integration tests | P1-02 through P1-06 | Unit and DB integration tests separated; Playwright smoke and unauthorized access run against actual services; no paid or live model in normal suite; test failure cannot be hidden by blanket skips; report runtime and cleanup commands without deleting persistent volumes |
| P1-08 / P0 | `.github/workflows/` first CI workflow | P1-07 | SHA-pin actions after checking official releases; contents read; no publish/deploy credentials in PR jobs; Java/Node matrix is intentionally minimal; backend tests incl Docker/Testcontainers and frontend lint/type/test/build pass on a real run; record URL and result; no placeholder green jobs |
| P1-09 / P1 | Review and update README/status/backlog/local setup and Phase 1 evidence | P1-08 | Clean-checkout reproduction; secret and Git diff review; actual command results and CI run linked; small commits; Phase 1 acceptance mapped explicitly; no Phase 2 starts without authorization |

Phase 1 deliberately includes minimum identity security because exposing a skeleton with protected data endpoints must not depend on the Phase 8 hardening milestone. It does not include dynamic integrations or LLM orchestration.

## Cross-phase work packages and remaining risks

| ID | Phase | Scope / gate |
| --- | --- | --- |
| P2-01 / P1 | 2 | Delivered independent schemas/fixtures/mappings/36 expected cases; complete Phase 2 CI passed |
| P3-01 / P0 | 3 | Prove protocol negotiation against SDK 2.1.0 and remote servers; assess 2026 specification support; update ADR if a supported stable SDK is required; deny unsupported revisions rather than inventing compatibility |
| P3-02 / P0 | 3 | Runtime client registry keyed by org/integration/config version; disable built-in global tool export; stale schema approval revocation; disconnect/cancel/close tests |
| P3-03 / P0 | 3 | Secure native servers and host authorization; first independent real discovery/invocation test; capabilities are untrusted |
| P4-01 / P0 | 4 | Gateway MCP server runtime addTool/removeTool; restart rebuild and registration failure recovery; internal authenticated delegated context; enforce tenant/tool permissions again in handler |
| P4-02 / P0 | 4 | JDBC PostgreSQL/MySQL read-only connectors; schema allowlists, parameterized filters, row/byte/time bounds, credential separation, no arbitrary SQL |
| P5-01 / P0 | 5 | OpenAPI subset policy (JSON, v3.0/v3.1 initially), local refs only by default, approved GET operations, destination policy with DNS/redirect defenses and egress controls |
| P6-01 / P0 | 6 | ChatModel adapter and controlled execution loop; no-model default; optional Ollama provider; select and pin a tool-capable model/digest after hardware/protocol evaluation; deterministic stub is only a test double |
| P6-02 / P0 | 6 | 30s query deadline, 8 call budget, bounded evidence, partial failures; seeded explicitly approved mapping versions for demonstration cross-source joins |
| P7-01 / P1 | 7 | Mapping UI/API lifecycle, identifier conversions, audits, deterministic joins and confidence display |
| P8-01 / P0 | 8 | Expand threat-model negative suite; retention/privacy review, rotation/revocation, rate-limit and tenant escape tests |
| P9-01 / P1 | 9 | Benchmark executor, fixed baseline, experiment manifests, independent oracle; actual results and uncertainty |
| P10-01 / P1 | 10 | Recheck supported Kubernetes/Argo versions and compatibility; select local kind tool/node image with checksums; never reuse ambient cloud context implicitly |
| P10-02 / P1 | 10 | Kustomize, migration Job, GHCR digests, GitOps update workflow, Argo project boundaries, dev rollout/rollback exercise |
| P11-01 / P1 | 11 | Reproduction package, measured research report, final end-to-end demonstration and known limits |
| EXT-01 / P2 | Future | SOAP adapter, write operations with confirmation, approved STDIO launch model, external identity federation, alternate model providers |

## Open decisions with owners and deadlines

The implementing developer owns each decision unless the user must supply external access. Exact later-phase infrastructure, observability, model and image-digest pins are selected before their first implementation, not guessed now. P1-01 owns advisory checks and runtime tool upgrades. P3-01 owns the current-spec interoperability gap. P6-01 owns model choice after resource measurement. P10-01 owns a project-specific development deployment destination and GitOps repository provisioning with the owner. None blocks writing the Phase 1 skeleton once its toolchain prerequisites are met.

## Phase 2 ordered work packages (A-G complete)

| ID | Checkpoint | Required verification |
| --- | --- | --- |
| P2-A | Handoff reconciliation and data/API design | Exact Phase 1 head/run addendum; source boundaries, dictionaries, mapping/case schema and ADR; documentation checks |
| P2-B | Sales PostgreSQL | Versioned constrained schema/seed; exact currency/time revenue; actual reader write/DDL denials and canonical rows |
| P2-C | Inventory MySQL | InnoDB schema/seed, warehouse/reservation/unit rules, canonical rows and actual reader denials |
| P2-D | Supplier REST | Pinned Node/Fastify service, strict auth/pagination/filter/errors, versioned validated OpenAPI, tests and container |
| P2-E | Isolated demo lifecycle | Three healthy independent systems, private source network/localhost ports, persistent volumes, platform unchanged |
| P2-F | Reference mappings and ground truth | 30 development plus 6 held-out cases, independent oracle/SQL comparisons, manifest hashes, two clean initializations |
| P2-G | CI and handoff | Preserve complete Phase 1 job; add real demo checks; inspect actual run; update status/acceptance and push without merges |

## Phase 3 prioritized work packages (authorized, 2026-10-31)

| Priority / checkpoint | Work | Exit evidence |
| --- | --- | --- |
| P3-01 / A (passed) | Reconcile Phase 2 final head, inspect official specs/graphs, actual legacy/modern wire proof, ADR 0008 | Exact branch a386824; actual SDK matrix and failure limits, stable baseline preserved |
| P3-04 / B (passed foundation checks) | Scoped official-SDK client adapter and clean Java port | Actual JSON/SSE discovery/calls, bounds, cancellation, typed failures, clean leases |
| P3-02 / C (passed) | Independent Java Sales and TypeScript Inventory native servers | Restricted real source readers, fixed parameterized SQL, protocol/auth/origin tests, exact fixture facts |
| P3-03 / D (passed) | Versioned PostgreSQL registry, AES-GCM credential references and lifecycle | Persistent revisions, CAS fencing, restart reconstruction, no plaintext credentials |
| P3-05 / D-E (passed) | Capability identities, fingerprints, role approvals and revocation | Discovery never authorizes; changed schemas stale; opaque integration-aware aliases |
| P3-06 / E (passed) | Existing Integrations UI, registration, explorer, manual playground | Actual API state, accessible/responsive controls, real results and cancellation |
| P3-07 / F (passed) | Destination/credential/tenant/tool enforcement | Negative URL/DNS/redirect/secret/Origin/schema tests and zero unauthorized source operations |
| P3-08 / F (passed) | Concurrency/deadline/cancellation/recovery | Disabled/revised clients retired; transport health cannot override governance |
| P3-09 / G (passed) | Separate MCP Compose and explicit network overrides | Backend only reaches client/server control path; independent source lifecycles/data preserved |
| P3-10 / D-G (passed) | Sanitized actor/org/revision/tool/protocol execution/audit metadata | Recent UI history, duration/correlation/denials without raw credentials/results |
| P3-11 / H (passed implementation CI) | Add real native MCP CI job; preserve Phase 1/2 jobs | Actual final-head jobs/test counts, docs/ADRs/security review, incremental Git push |

At the historical Phase 3 boundary, dynamic DB/REST connectors were excluded. They have since been implemented in Phases 4/5. General joins/mapping UI, AI/chat, benchmark execution and deployment/publishing remain excluded.

## Phase 4 checkpoint status

| Checkpoint | Delivery | Status |
|---|---|---|
| P4-01 | Stable runtime/authentication/tenant-isolation proof, ADR 0012 | Passed, 11 actual wire checks |
| P4-02 | V4 registry, encrypted credential lifecycle, operator policy | Implemented and PostgreSQL tested |
| P4-03 | JDBC metadata/fingerprints, explicit role/table/column approvals | Implemented; two actual source browser journeys passed |
| P4-04 | Typed prepared bounded reads, actual separate reader grants | Passed; ten real PostgreSQL/MySQL integration tests |
| P4-05 | Private managed gateway, live authorization, restart/cancel fencing | Passed; actual concurrent cancel/disable/restart/authority outage |
| P4-06 | Existing React management/explorer/results/history | Implemented; 22 component tests, two actual browser journeys |
| P4-07 | Adversarial/outage/restart/secret/limit checks | 58 actual stack checks passed locally and in CI |
| P4-08 | Preserve all three old CI jobs; dedicated fourth job; final head/docs | Passed; actual four-job run 41812225715 |

Phase 5 REST was separately authorized and is delivered above. Phase 6 AI/chat still requires separate explicit authorization.
