# Architecture

Status: Phase 0 design, 2026-10-29. No component below is implemented yet. Decisions: [0001](decisions/0001-modular-platform.md), [0002](decisions/0002-supported-dependencies.md), [0003](decisions/0003-mcp-and-gateway.md), [0004](decisions/0004-security-and-mappings.md), [0005](decisions/0005-delivery-and-evaluation.md).

## Context and deployment boundaries

```mermaid
flowchart LR
  U[User or administrator] --> UI[React web frontend]
  UI -->|same-origin session and CSRF| API[Spring MVC platform API]
  API --> O[Controlled orchestration]
  O --> A[Spring AI ChatModel adapter]
  A --> L[Optional configured model provider]
  O --> M[MCP host client registry]
  M -->|Streamable HTTP| N[Independent native MCP servers]
  M -->|authenticated internal Streamable HTTP| G[Managed gateway MCP endpoint]
  G --> C[Connector implementations]
  C --> P[External PostgreSQL sales]
  C --> I[External MySQL inventory]
  C --> R[Independent supplier REST API]
  API --> D[(Platform metadata PostgreSQL)]
  G --> D
  N --> X[Independent source systems]
```

The platform backend initially includes the managed gateway as a bounded module and a dedicated internal MCP endpoint. It is one deployment, not a separate microservice. Its client-to-server path still uses the supported SDK transport over authenticated loopback/internal HTTP; invoking connector methods directly from chat would bypass the intended MCP boundary. Integration tests must exercise the wire path. If resource or security isolation requires a separate deployment, an ADR will extract the gateway using its existing interfaces. Native MCP demo servers remain independently deployable from the start.

The browser never connects to external databases, receives integration secrets, or calls remote MCP servers directly. Model providers see only authorized, minimized tool definitions and evidence. No paid model is necessary for startup or ordinary tests. In no-model mode chat reports unavailable; it must not fabricate an answer.

## Main platform module contracts

One Maven application with packages under `io.ecosync`; add separate build modules only when boundaries justify them. Expose module application interfaces/DTOs; repository classes and ORM entities stay private to their module. Use architecture dependency tests in the skeleton rather than creating empty packages merely to match this table.

| Boundary | Owns | Permitted collaborators |
| --- | --- | --- |
| identity | Local user auth, sessions, controlled admin provisioning | organizations, authorization, audit |
| organizations | Tenant membership and tenant-scoped lookup contracts | No integration/orchestration internals |
| authorization | Role, integration and tool policy evaluation | identity/organization read contracts; no connector I/O |
| integrations | Registrations, immutable config revisions, lifecycle, tool approval | authorization, credential store interface, audit; schedules discovery through MCP/connector contracts |
| connectors | PostgreSQL, MySQL, REST validation/inspection/execution | Credential and outbound policy interfaces; no model or chat dependencies |
| mcp | Native clients, gateway server adapter, protocol negotiation, namespacing | integration/authorization read contracts; connector execution contract for gateway |
| ai | Replaceable Spring AI model adapter and provider settings | Tool/result DTOs only; no DB credentials or connector implementations |
| orchestration | Bounded execution state machine, tool validation and dispatch | authorization, integration/tool catalog, MCP invocation, ai, mapping, audit |
| mapping | Approved canonical IDs, transforms, deterministic joins/calculations | Source/schema references and structured data; no model dependency |
| audit | Append-only sanitized events and restricted query API | Tenant and actor IDs; no dependency on business implementation classes |
| observability | Health, metrics, tracing and correlation | Technical instrumentation with payload redaction |
| evaluation | Versioned cases, run manifests and metrics | Public application execution contracts, independent expected-answer oracle |

Avoid a module cycle by splitting registration metadata from connection lifecycle adapters: integrations publishes configuration-change events; MCP/gateway handlers observe them. Results return through explicit ports. Authorization depends on policy DTOs rather than integration repositories. No generic shared module should become a business-logic dumping ground.

## Integration registration, approval, and invalidation

1. Administrator chooses existing MCP, PostgreSQL, MySQL, or REST. These have distinct forms and validation semantics.
2. Save an immutable configuration revision as DRAFT, with secret references separated. Validate destination policy before any connection attempt.
3. A bounded validation/discovery job snapshots the revision and enters VALIDATING. Discover only permitted metadata. Handle paginated MCP tool lists with limits.
4. Present capability/schema fingerprints and scopes for review. Validating connectivity alone grants no invocation permission.
5. Administrator approves specific capabilities for specific roles/subjects. Activation requires successful validation of the same current revision and explicit approval.
6. Maintain ACTIVE or DEGRADED health. DISABLED excludes discovery/export and new calls, closes clients/pools, invalidates caches and cancels in-flight work where possible. Already-returned data cannot be recalled.
7. Config or schema changes invalidate affected approvals and derived tools; never silently expand access. A disabled integration stays disabled after editing until explicit validation and reactivation.

Transitions: DRAFT -> VALIDATING -> ACTIVE after approval, VALIDATING -> DRAFT on failure, ACTIVE -> DEGRADED on failed health, DEGRADED -> ACTIVE only if configuration/approvals are unchanged and health recovers. Any state may be explicitly disabled. Re-enablement goes through DRAFT/VALIDATING. Health does not override governance state. Compare-and-set revision checks prevent a late validation job from activating stale config.

## MCP and managed connector execution

Registry key: `(organizationId, integrationId, configurationVersion)`. Resolve clients at runtime from persisted registrations; application startup YAML alone does not meet dynamic onboarding requirements. Start with a uniform synchronous SDK client API and bounded worker pools; explicitly disable unused sampling, roots, elicitation and STDIO launch capabilities. Scope clients and callbacks per integration; do not export all discovered callbacks globally into a shared ChatClient.

Each published tool has an opaque stable alias derived from integration UUID plus operation UUID, a retained original remote name, validated schemas and a fingerprint. Do not use untrusted display names as dispatch keys. Enforce name length/format constraints of the selected SDK/provider. Tool descriptions and annotations cannot grant trust or permissions.

The SDK provides `McpSyncServer.addTool(...)` and `removeTool(...)`; the managed gateway will generate tool specifications from approved connector capabilities and register/unregister them at runtime. Spring's static annotated tools remain suitable for independent demo services, not dynamic customer connections. See [compatibility evidence](COMPATIBILITY.md).

Connector contract (conceptual, no Java interface implemented yet): validate typed configuration; test bounded connectivity; inspect allowlisted schemas/capabilities; describe approved operations; execute validated operation; return structured result/error; report health; close resources. Lookup implementations by explicit connector type, not giant switches in the orchestrator. Adding another supported connection is configuration; a new protocol requires an adapter.

For each managed invocation the host mints a short-lived internal delegated context bound to actor, org, integration revision, operation, execution ID, expiry and audience. Authenticate the host service separately. The gateway verifies both, checks current approval/revocation and refuses a browser session as a substitute. Credentials are resolved only inside the connector. No tenant identity or permission from model arguments is trusted. Restrict endpoint routing/Origin, never publish its port externally by default, and protect it even on loopback. Do not forward browser, GitHub, or external-system bearer tokens as delegation credentials.

## Query execution and provenance

```mermaid
sequenceDiagram
  participant User
  participant Host
  participant Policy
  participant Model
  participant MCP
  participant Mapping
  User->>Host: Authenticated question and CSRF token
  Host->>Policy: Resolve current tenant permissions
  Host->>Model: Minimized approved catalog and question
  Model-->>Host: Proposed tool and typed arguments
  Host->>Policy: Reauthorize revision, operation and subject
  Host->>MCP: Bounded tools/call
  MCP-->>Host: Structured data or explicit sanitized error
  Host->>Mapping: Approved version and deterministic calculation
  Mapping-->>Host: Rows, totals, unmatched identifiers, evidence
  Host->>Model: Bounded evidence for explanation
  Host-->>User: Answer, source references and execution timeline
```

Proposed starting limits: 30s total deadline, 8 tool calls, 4 concurrent calls, 5s connect timeout, 10s tool timeout capped by remaining deadline, 100 rows by default/500 maximum, 1 MiB per tool response and 2 MiB aggregate evidence. At most one transient retry for an idempotent read, charged against call/time budget. Enforce byte limits while streaming; truncation and partial failures are explicit. These are initial policy defaults to measure, not benchmark results.

Execution status distinguishes COMPLETED, PARTIAL, FAILED, CANCELLED. Evidence records integration/config version, tool/schema fingerprint, operation parameters with sensitive fields removed, source record identifiers, retrieval time, explicit UTC time range, mapping version and deterministic calculations. Report unknown or conflicting data. No inferred identifier equivalence, currency conversion or hidden chain of thought. Phase 6 may use explicitly seeded approved mappings; Phase 7 adds general mapping management.

## Repository layout by introduction phase

| Path | Phase | Purpose |
| --- | --- | --- |
| `config/versions.json`, `scripts/`, `docs/` and root planning files | 0 | Pins, compatibility/documentation verification and design |
| `backend/`, `frontend/`, `infra/docker/`, `.github/workflows/` | 1 | Main platform, initial local stack and CI |
| `demo-systems/sales/`, `demo-systems/inventory/`, `demo-systems/suppliers/`, `evaluation/datasets/` | 2 | Independent systems and reproducible synthetic data |
| `mcp-servers/`, gateway module inside `backend/` | 3-4 | Native servers and runtime-managed connector tools |
| `evaluation/` execution/results tooling | 9 | Benchmarks, run manifests and measured reports |
| `infra/kubernetes/`, isolated `infra/gitops/` fallback, `infra/argocd/` | 10 | Kustomize sources, desired environment state and Argo bootstrap; prefer a separate GitOps repo when provisioned |

Do not create the future empty directories now. Backend is Spring MVC/JPA for metadata, JDBC for controlled external reads. PostgreSQL metadata is distinct from demo PostgreSQL even when local containers share a host. React uses strict TypeScript, React Query, React Router and accessible Radix primitives with a small CSS token system. Evaluate errors, focus states and real empty data explicitly.
