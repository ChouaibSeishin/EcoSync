# AI and orchestration security

The model, tool descriptions and returned content are untrusted. Authentication, owner/tenant checks, capability filtering and execution authorization remain backend responsibilities. [Architecture](CHAT_ORCHESTRATION.md) and [ADR 0014](decisions/0014-controlled-chat-orchestration.md) describe the implemented path; [verification](../development/PHASE_6_VERIFICATION.md) distinguishes observed evidence from pending gates.

## Enforced boundaries

- Model input contains bounded approved definitions and source results, never connection objects, keyrings, signing keys, service bearer tokens or environment dumps. Established registries redact known configured credentials; chat additionally removes secret-like fields and token/key patterns. This is not a general DLP or personal-data classifier.
- Tool aliases bind server-owned source IDs/revisions/fingerprints. The model cannot choose an actor, organization, execution identity, URL, HTTP method, header or SQL statement. Existing schema/column/GET restrictions and MCP authority checks apply again at execution.
- Native capabilities need an exact operator read-only declaration and source-side restricted credentials. Protocol annotations alone do not establish safe business semantics.
- Prompt injection can cause malformed or unauthorized proposals, but cannot expand the supplied catalog or backend permission set. No returned string is evaluated as code or privileged instructions; React renders it as text.
- The total deadline and eight-call budget include model turns and deterministic helpers. An expired/cancelled scope cannot start a new governed transport request; cleanup is bounded and best effort. Already admitted remote work may finish, but cannot overwrite a persisted terminal chat state.
- Source-dependent history is checked against current authority on retrieval and before reuse. Semantic binding changes also invalidate access to earlier source answers. Archived conversations remain owner-scoped and count toward the storage bound.
- Mapping-only answers retain the source authorization dependencies that allowed access to the seeded relationship. Only cited source evidence and actual derivation lineage may establish the final relationship.

## Threat tests

Real PostgreSQL tests cover tenant/owner isolation, AI disabled, stable ordering/restart, idempotency/concurrency, current grants and membership, revocation before dispatch, cancellation of admitted work, deadline, unknown/oversized/malformed proposals, eight-call loops, partial versus empty responses, prompt injection, secret-like result minimization, follow-up references and changed semantic bindings. Controlled peers isolate races; they are not live MCP proof.

Live verification separately uses actual native Java/TypeScript, managed PostgreSQL/MySQL and REST MCP paths. Negative admission checks use authenticated native SQL counters, database SQL-admission counters and the controlled Partner HTTP counter. The independent Supplier remains unchanged; its normal data is compared against the independent development oracle. Browser tests use actual backend sessions and actual Partner data. Exact counts/results appear only after observation.

## Local provider trust

Ollama is opt-in, pinned by image digest, isolated on a model control network and bound to host loopback for manual setup. Its download network is not a source network. The backend receives no new database/API source-network membership. The container has model weights, no source credentials or host keyring mounts, and cloud support is disabled. Compose network membership is not a per-process/per-port firewall; production egress/TLS/authentication and model-host policy need deployment controls.

The provider URL is operator-controlled and restricted to explicit local choices. Model tags are checked against a configured immutable manifest digest. A missing/changed model fails closed. Model selection does not imply latency or accuracy guarantees. Cold loading exceeded the 30-second query budget in the initial experiment; the warm synthetic tool-call test passed separately. Full real-model source orchestration is a distinct optional acceptance record, not inferred from CI.

Reviewed primary sources on 2026-11-07: [Spring AI 2.0.2 release](https://github.com/spring-projects/spring-ai/releases/tag/v2.0.2), [Spring AI advisories](https://github.com/spring-projects/spring-ai/security/advisories), [Ollama release 0.40.15](https://github.com/ollama/ollama/releases/tag/v0.40.15), [Ollama advisories](https://github.com/ollama/ollama/security/advisories), [official tool calling](https://docs.ollama.com/capabilities/tool-calling). Resolved Maven compatibility is observed separately; an advisory-page review is not a claim that every transitive dependency is vulnerability-free.

## Residual limits

One backend replica and in-memory sessions remain. Local HTTP and Vite are development tooling. Authorized source data may itself contain sensitive information; no compliance certification or guaranteed semantic truth of model-selected scope is claimed. An authorized user can retain information already delivered. A compromised provider/host can retain in-memory prompts/results. Conservative schema/evidence limits may reject a legitimate large response; the application does not turn that into an empty success. Cross-source ambiguity is preserved, not resolved by labels. No writes, autonomous/background retrieval, cloud provider fallback, general mapping UI, fuzzy matching or scientific benchmark claims are introduced.
