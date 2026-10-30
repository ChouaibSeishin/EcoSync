# Security design and threat model

Target security design with Phase 1 implementation evidence dated 2026-10-30. Session/CSRF/password/bootstrap/tenant enforcement, bounded login throttling, safe errors, public status-only probes and runtime DB least privilege are implemented and tested; see [Phase 1 verification](../development/PHASE_1_VERIFICATION.md). Connector/MCP/model defenses, persistent audit browsing, broader rate limits and multi-instance governance remain future requirements. The matrix below remains the target; it does not claim every control is already delivered.

## Assets and trust boundaries

Protect external-system credentials, tenant data and mapping decisions, session identities, tool approvals, audit integrity, provider data disclosure and deployment credentials. Boundaries: browser -> API; API -> policy/catalog; host -> model provider; host -> remote MCP; host -> internal gateway; gateway -> database/API; CI -> registry/GitOps; Argo -> cluster. Retrieved content, OpenAPI descriptions and model suggestions are untrusted across every boundary.

Actors include legitimate users with limited access, malicious users/tenant administrators, compromised MCP/API servers, prompt injections embedded in data, compromised dependencies and unauthorized infrastructure clients. A tenant administrator may configure a connection but may not bypass destination policy or grant global platform access.

## Threats, mitigations, and required negative evidence

| ID / threat | Planned control | Verification and delivery phase |
| --- | --- | --- |
| T01 session theft, CSRF, credential guessing | Same-origin server sessions, session ID rotation, HttpOnly/SameSite cookies, Secure with HTTPS outside explicit localhost dev, CSRF for unsafe requests incl login/logout, password hashing, bootstrap without a default password, throttling and session expiry | Phase 1 tests missing CSRF, invalid login, logout reuse and anonymous API access; Phase 8 extends abuse suite |
| T02 tenant/role escape | Default deny; tenant from authenticated membership; scoped integration/tool/evidence authorization at execution; composite foreign keys; per-org cache/client keys | Cross-org IDs, forged org fields, wrong role and revoked grants rejected in phases 1/3-7 |
| T03 stale approvals / confused deputy | Approval bound to config and schema fingerprint; live reauthorization; gateway verifies short-lived delegated context, service identity, audience and actor scope | Phase 3-4 tests edited config, schema drift, expired/forged delegation, replay outside bound execution and direct gateway access |
| T04 SQL injection or writes | Read-only DB role and transactions, approved identifiers from introspected metadata, quoted dialect identifiers, parameterized values, typed filters, no raw SQL tool, schema/column allowlists | Phase 4 tests tautologies, injected identifiers, DDL/DML, multiple statements, unapproved tables and expensive reads |
| T05 SSRF, DNS rebinding and credential exfiltration | Default deny outbound destinations; operator allowlists exact scheme/host/port; HTTPS; resolve/check all IPv4/IPv6 addresses at connect; block loopback/private/link-local/metadata by default; pin/revalidate destination at connection time; reject redirects by default; no forwarding auth across origins; network egress enforcement | Phases 3/5 test encoded IPs, mixed DNS answers, redirects, rebinding and metadata endpoints; same policy applies to JDBC hosts and model endpoints where applicable |
| T06 malicious OpenAPI refs/payloads | Start with documented v3.0/v3.1 JSON subset and GET operations; reject unsupported constructs visibly; local refs only; cap document size/depth/refs; no arbitrary remote URL dereference; bounds while reading responses | Phase 5 tests external/cyclic refs, unsupported auth/parameters, zip/JSON bombs, overlong response and malformed content |
| T07 remote MCP impersonation or session abuse | TLS/server identity validation, proper scoped server auth, no token passthrough, network policy, Origin validation on our endpoints; reject unsupported protocol version, cap connection/discovery resources | Phase 3 tests wrong auth, bad Origin/version, malformed JSON-RPC, tool-list pagination limits, disconnect/recovery; service endpoint authentication is explicit |
| T08 tool poisoning / prompt injection | Treat descriptions/results as data; select only approved catalog; validate schemas and budgets independently of model; block model access to credential resolver and commands; disable unneeded sampling/elicitation/roots | Phase 3/6 tests instructions to ignore policy, call hidden tools, leak secrets, change tenant or loop; text never changes approval |
| T09 resource exhaustion | Query/call deadlines, bounded concurrency/pools, row/byte/page limits, cancellation, rate limits, circuit breaker policies and retry budget only for idempotent transient reads | Phases 3-6 test huge pages/results, slow streams, recursive tool loops, retry exhaustion and cancellation |
| T10 leaked secrets in UI, logs or providers | Credential references and environment/secret-store keys; encrypt stored integration credentials; redact connection strings/headers, exception chains and audit diffs; minimal source evidence to provider; no automatic raw payload logs | Each feature uses sentinel secret tests and review; Phase 8 rotation/retention review |
| T11 untrusted STDIO/transform code | No user-supplied process execution; no STDIO onboarding initially; no arbitrary scripts/SQL in mapping rules | Phases 3/7 reject command/path fields and unapproved transforms |
| T12 CI/supply chain and deployment compromise | Exact dependency locks/BOMs, verified distributions, SHA-pinned actions, least-privilege job tokens, no privileged fork workflow checkout, scan dependencies/images/secrets, signed provenance where available | Phase 1 CI review; Phase 10 publish/digest/GitOps checks; no cluster admin credentials in normal CI |
| T13 wrong semantic conclusions | Explicit approved identifier/type/unit/currency mappings; deterministic decimal arithmetic; missing/conflicting relationship flags; source/range/mapping provenance | Phases 6/7 tests mismatched IDs, missing rows, time boundaries, duplicate keys and invalid unit conversions |
| T14 audit tampering and excess retention | Append-only application audit permissions; restrict auditor reads; independent export/retention policy; no claims of tamper-proof storage from a single DB role alone | Relevant phases assert audit events for denial/config changes; Phase 8 tests least privilege and deletion policy |

## Authorization model

| Role | Intended permissions |
| --- | --- |
| Platform administrator | Explicit global platform operations, tenant provisioning, destination/secret-store policy; sensitive data access remains separately governed |
| Integration administrator | Within assigned org: register/test/inspect approved connection classes, review tools/mappings and assign permitted scoped grants; cannot override operator network rules |
| User | See eligible sources; execute approved read tools; own conversations and authorized result evidence |
| Auditor | Read permitted sanitized audit/execution metadata; no tool invocation or credential access by default |

Roles are a coarse gate. The backend also checks organization membership, integration state, current config, tool/schema approval, subject grants, allowed data scope and result sensitivity. Frontend hiding is convenience only. Read-only remote tool annotations are hints; tools are not considered safe until reviewed and enforced.

## Credential, session, and network policy

Local development uses external environment values and a clearly marked localhost HTTP cookie exception. Production-like use requires HTTPS and secure cookies, narrow trusted proxy configuration and explicit CORS/Origin policy; never trust arbitrary forwarded headers. Hash passwords using Spring Security's maintained adaptive encoder with migration support; do not store tokens in browser localStorage.

For configured integrations use a secret-store interface: local encrypted AES-GCM storage with a separately supplied random master key and versioned key IDs, or external managed secret references. Fresh nonces, authenticated metadata, tenant-bound lookup, rotation and least-privileged resolver access are required. Secrets are not sent to the LLM, returned by GET APIs, or stored in plaintext config JSON. Local encrypted storage is not a substitute for key management.

Development demo network exceptions are operator-defined exact service names/ports in a separate profile; they cannot be registered or expanded by a tenant admin. Production refuses that profile. Do not disable TLS verification to solve connectivity. Dynamic MCP auth must support the selected server's documented auth mechanism; unsupported OAuth flows must fail visibly until implemented. No reuse of GitHub credentials for source connections.

## Residual risks and release gates

Authorized read data may itself be sensitive; require data minimization and documented provider disclosure/retention consent before real external-model use. A compromised approved source can still return incorrect facts; provenance and deterministic cross-checks reduce but do not eliminate that risk. Software process cancellation cannot undo a remote read already performed. Host compromise can expose encryption keys; protect runtime and backups. No generated dataset is evidence of real-world generalization.

Before exposing any feature beyond localhost, verify its applicable controls and tests above, publish actual limitations, and recheck supported dependencies. No public deployment or cluster changes are part of Phase 0.

## Implemented Phase 2 demo controls and limits

The source Compose project has separate DB volumes/accounts and a dedicated bridge with no platform-network attachment. Published ports bind only 127.0.0.1. Setup generates seven distinct random owner/migrator/reader/token values in ignored local configuration. Source readers can SELECT only intended business tables; actual DML, DDL, control-table/admin access and invalid-password tests are required and passing. Migration histories are verified against committed SQL hashes.

Supplier data requires a configurable bearer token checked without logging it; request logging is disabled, auth errors are bounded, pagination/body/URL/header resources are bounded, and invalid snapshots fail readiness. The API container uses a non-root user, read-only root filesystem, dropped capabilities and memory/CPU/PID limits. No database credentials are passed to this service.

These are local synthetic-source accounts, not tenant-level platform integration authorization. No connector/host permission system is delivered by these fixtures. Plain loopback HTTP/SQL, local configuration, immutable fixture storage and point-in-time npm/source checks are development choices. Production TLS, operational rotation/secret stores, multi-instance governance and the future MCP/connector threat controls above remain future acceptance work. [Phase 2 verification](../development/PHASE_2_VERIFICATION.md) records actual source denials and known limitations.
