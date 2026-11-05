# Roadmap

2026-11-05: Phase 5 is authorized and in progress from exact Phase 4 final branch head `ec310f8c25b4778396bdc5f698f3ef7253967401`, final four-job run 41814237164. The older application acceptance record remains below. Phase 6 remains unstarted. Phase 5 completion requires its own final-head five-job run and two actual REST browser journeys.

Phase 3 final acceptance: `codex/phase-3-native-mcp` at `0e681ab5cb9a1930a159d3b459d9380fe660a363`; [run 41619804974](https://github.com/ChouaibSeishin/EcoSync/actions/runs/41619804974) passed all three jobs. Final verify ran 6 backend unit, 23 PostgreSQL integration and 17 frontend tests plus browser/persistence checks, zero skipped tests. The final metadata-refresh regression is included. Phase 4 is separately authorized from that head, beginning with a real gateway compatibility/tenant-isolation gate. Historical earlier acceptance references remain below.

Each phase ends with a reviewable Git state, updated status/backlog, relevant tests, an integration check where applicable, and an honest verification report. Phase 4 is complete; Phases 5-11 require separate authorization. Later rows describe acceptance criteria, not delivered functionality.

| Phase | Deliverable | Required exit evidence | State |
| --- | --- | --- | --- |
| 0 | Discovery and foundation | Architecture, module/data/security designs, sourced version pins, dependency compatibility assessment, local/deployment/research plans, prioritized Phase 1 backlog, documentation checks, Git commits | Complete (design scope) |
| 1 | Working skeleton | Backend/frontend production builds; platform PostgreSQL and Flyway baseline; Compose startup; useful status/dashboard/empty integrations screens; guarded endpoints; positive/negative tests; actual GitHub Actions result; ordinary tests need no provider secrets | Complete (local + actual CI verified) |
| 2 | Independent demo ecosystem | Separate sales PostgreSQL, inventory MySQL, supplier REST service; each runs independently; reproducible synthetic seed data and expected answers; identifiers and schema differences documented | Complete (local + actual CI verified; 30 development + 6 held-out cases) |
| 3 | Native MCP | Admin registration and real discovery/calls against separate servers; version/transport verification; namespace/schema checks; authorization denials; timeouts, disconnection, recovery, lifecycle tests | Complete at final 0e681ab; all three jobs passed in run 41619804974 |
| 4 | Dynamic DB connectors | Add PostgreSQL/MySQL through UI without core recompilation; approved schema inspection; parameterized bounded reads through gateway MCP; credential isolation; disable/revoke and malicious-query tests | Complete; all four jobs passed in run 41812225715 |
| 5 | Dynamic REST | Import/review supported OpenAPI operations; configure a second API without orchestrator changes; approved read operations through MCP; SSRF, redirect, external-ref, auth and payload limits tested | Planned |
| 6 | LLM cross-system chat | Persistent conversations; authorized tool selection/calling; budgets/deadlines; actual source answers; visible partial failures; deterministic stub tests plus optional real-model end-to-end evidence; joins only with explicit seeded approved mappings | Planned |
| 7 | Semantic mapping management | Versioned UI/API mapping review and approval; deterministic identity matching/normalization; ambiguous joins rejected; audit evidence and mapping provenance | Planned |
| 8 | Governance hardening | Expanded adversarial tests across sessions, CSRF, tenant isolation, tools, SQL/HTTP, rate limits, credentials, audit, prompt injection; known risks recorded | Planned |
| 9 | Observability and evaluation | Correlated metrics/traces; reproducible benchmarks and conventional baseline; stored run manifests and measured correctness/latency/security/effort; failures retained | Planned |
| 10 | Kubernetes and delivery | Validated images/manifests; successful CI publishes versioned images/digests; GitOps image updates; Argo CD sync and real development deployment verified if a cluster is explicitly configured for this project; otherwise remote acceptance remains open | Planned |
| 11 | Integrated demonstration and research package | Clean-checkout reproduction; core end-to-end flows; administrator/user/security/API/deployment guides; measured research report and limitations; demonstration scenarios | Planned |

## Phase 0 acceptance evidence

| Criterion | Evidence location | Interpretation |
| --- | --- | --- |
| Decisions documented | Architecture and ADRs 0001-0005 | Design decisions, not implemented controls |
| Repository structure defined | Architecture, repository layout | Future paths created only when useful |
| Dependencies compatible and pinned | versions.json, compatibility assessment, verification report | Official version/peer metadata and resolution checks; application build/runtime compatibility must be proved in Phase 1 and MCP interoperability in Phase 3 |
| First implementation phase ready | Backlog P1-01 through P1-09, local setup | Concrete order, dependencies, test expectations, environment prerequisites |

Security is implemented alongside each feature. Phase 8 strengthens existing protections; it never authorizes deferring enforcement until then. Phase 6 uses deliberately approved fixture mappings; general mapping management remains Phase 7. No next major phase starts automatically.

Phase 4 is complete at application head `18aafb7d41011171e64da25e14cc1bdf363fc533` on `codex/phase-4-managed-db-connectors`. [Actual run 41812225715](https://github.com/ChouaibSeishin/EcoSync/actions/runs/41812225715) passed all four jobs. Phase 5/6 remain unstarted. See [Phase 4 acceptance](docs/development/PHASE_4_ACCEPTANCE.md).
