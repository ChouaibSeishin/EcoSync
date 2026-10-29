# ADR 0003: Dynamic MCP clients and managed connector gateway

Date: 2026-10-29. Status: accepted initial target; current-spec interoperability gate open for Phase 3.

## Context

Admin runtime registration must work for existing MCP servers and for supported databases/APIs. MCP transport naming does not imply compatibility with every protocol revision. The selected SDK supports explicit older version constants while the July 2026 specification changes session/transport semantics.

## Decision

Start with Streamable HTTP and the SDK-supported 2025-11-25 target, negotiated and verified against actual servers in Phase 3. No new STDIO launch workflow or legacy HTTP+SSE default. Do not claim 2026-07-28 support until the Spring AI/SDK combination demonstrates it. Unsupported servers fail with a clear compatibility status; coordinated upgrades require updating the manifest/evidence.

Manage clients programmatically per org/integration/config revision. Use a uniform synchronous API initially, not a mixture of sync and async clients. Disable automatic global tool callback exposure so a registered server cannot gain access through shared model context. Evaluate the documented WebFlux transport recommendation under measured production needs.

Expose approved PostgreSQL/MySQL/REST connector operations through an authenticated internal MCP server using the Spring MVC Streamable starter and runtime SDK `addTool/removeTool`. Rebuild the catalog from current persisted approvals after restart. Removal/disable revokes execution immediately even if transport cleanup fails. Native server tool lists are discovered from the actual remote source, never replaced by a hardcoded demo catalog.

## Alternatives and consequences

Direct connector calls from the orchestrator are simpler but would not demonstrate the same MCP execution path. A separate gateway deployment improves isolation but adds service auth/deployment overhead too early; keep its boundary extractable. Startup YAML connections alone cannot implement dynamic onboarding. Protocol compatibility is an explicit acceptance gate, not silently assumed. API details and exact tagged evidence are in [compatibility](../COMPATIBILITY.md).
