# Backlog

Phase 1 P1-01 through P1-09 are complete with local and actual GitHub Actions acceptance evidence. Phase 2 checkpoints A-G are complete with local and actual GitHub Actions evidence; see docs/development/PHASE_2_ACCEPTANCE.md. Phases 3-11 remain unimplemented. See PROJECT_STATUS.md and the Phase 1 verification report for evidence. P0 = foundation-critical priority, P1 = required phase scope, P2 = later extension. IDs remain stable between phases.

## Phase 1: ordered implementation plan

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
