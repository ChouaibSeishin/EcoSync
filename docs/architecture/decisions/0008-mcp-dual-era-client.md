# ADR 0008: Official dual-era MCP client behind a scoped internal service

Date: 2026-10-31. Status: selected after the real compatibility gate; application-specific integration remains checkpoint B onward.

## Evidence and alternatives

The [matrix](../MCP_COMPATIBILITY.md) proves stable Java SDK 2.1.0 works with the legacy peer and cannot initialize a modern-only 2026 peer. The official TypeScript 2.4.0 client interoperates with both. Spring AI's RC adds a Boot RC dependency while retaining the same Java SDK; upgrading does not fix the client gap.

Option A keeps the stable official Java client for 2025 and adds a custom modern Tools adapter. JDK HTTP is maintained, but the application would own discovery/version metadata, JSON/SSE validation, cancellation, remote schemas, error mapping and future protocol changes in a second protocol stack. A narrow adapter is possible, but its conformance and maintenance burden is greater than using a supported dual-era SDK.

Option B adds an isolated Node 24 client process using the official dual-era SDK. It adds a service credential, network hop, readiness/cleanup policy and bounded process resources. Those costs are explicit; no comparative performance benefit is claimed. Node 24 is already a pinned project runtime. The official SDK owns negotiation and wire behavior rather than application JSON-RPC code.

## Decision and boundaries

Select B. Expose only authenticated internal connect/discover/list/call/close operations, with opaque org/integration/revision-scoped client keys and bounded request/response handling. This is a native MCP **client transport adapter**, not the future managed MCP gateway. Java remains the persistent registry and execution-time tenant/tool/role/credential authority. It never copies discovered tools into a global model catalog.

Restrict the supported surface to Tools over Streamable HTTP for tested 2025-11-25 and 2026-07-28 revisions; reject unsupported protocols/auth/schema forms visibly. Disable user STDIO launching, client sampling/roots/elicitation and model callback exposure. The service must enforce operator destination policy, pinned DNS, no redirects, byte/deadline/concurrency bounds and explicit resource retirement. Credentials are tenant-bound AES-GCM records with external versioned keys; only authorized operations resolve them.

Use a Java legacy Sales server and a TypeScript modern Inventory server after B. They use the existing independently owned reader accounts and fixed bounded SQL. Supplier remains REST. No platform DB/REST connector onboarding, general query generation, LLM, mapping UI or deployment publishing is added.

Keep the implementation replaceable behind a Java client interface. Revisit Java-native modern client support when an official stable SDK demonstrates it; preserve wire tests to evaluate migration. Application-scoped Spring AI sessions are not tenant isolation, and its static startup configuration is not the dynamic registry.
