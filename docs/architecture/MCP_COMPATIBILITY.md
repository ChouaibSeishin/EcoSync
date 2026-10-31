# MCP compatibility: observed 2026-10-31

The gate uses current official specifications, exact release/tag sources and actual HTTP peers. Boot 4.1.2 / Spring AI 2.0.2 remain the stable platform baseline. This is Tools interoperability evidence, not full MCP conformance, AI execution or a performance benchmark.

## Verified release graph

| Component | Observed selection / consequence |
| --- | --- |
| Java MCP SDK | Stable 2.1.0; legacy 2025-11-25 client/server family |
| Java SDK 2.2.0-M1 | Prerelease adds a separate modern server; release explicitly excludes a modern client and upgrades Reactor to a milestone |
| Spring AI 2.0.2 | Exact parent uses Boot 4.1.2 and Java MCP SDK 2.1.0 |
| Spring AI 2.1.0-RC1 | Exact parent uses Boot 4.2.0-RC1 and still Java MCP SDK 2.1.0; does not solve modern client support |
| TypeScript MCP SDK | Stable client/server/core 2.4.0, Node adapter 2.1.2; supports both eras; Node >=20, tested using project Node 24.22.2 |
| Proof validation dependency | Zod 4.6.7 exact; locked clean install/audit returned zero current advisories |
| Host schema validation | AJV 8.20.0 and NetworkNT 3.0.8 (Java 17+/Jackson 3); explicit bounded subset, no remote refs |
| Inventory database reader | mysql2 3.24.6, exact npm lock, prepared execute API; existing restricted inventory_reader |

Official ledger: [modern spec](https://modelcontextprotocol.io/specification/2026-07-28), [versioning](https://modelcontextprotocol.io/specification/2026-07-28/basic/versioning), [HTTP](https://modelcontextprotocol.io/specification/2026-07-28/basic/transports/streamable-http), [discovery](https://modelcontextprotocol.io/specification/2026-07-28/server/discover), [2025 lifecycle](https://modelcontextprotocol.io/specification/2025-11-25/basic/lifecycle), [Java stable](https://github.com/modelcontextprotocol/java-sdk/releases/tag/v2.1.0), [Java milestone](https://github.com/modelcontextprotocol/java-sdk/releases/tag/v2.2.0-M1), [TS 2.4.0](https://github.com/modelcontextprotocol/typescript-sdk/releases/tag/v2.4.0), [dual-era behavior at exact tag](https://github.com/modelcontextprotocol/typescript-sdk/blob/v2.4.0/docs/protocol-versions.md), [stable AI parent](https://github.com/spring-projects/spring-ai/blob/v2.0.2/pom.xml), [RC AI parent](https://github.com/spring-projects/spring-ai/blob/v2.1.0-RC1/pom.xml), [client lifecycle](https://docs.spring.io/spring-ai/reference/api/mcp/mcp-client-boot-starter-docs.html), [server security](https://docs.spring.io/spring-ai/reference/api/mcp/mcp-server-boot-starter-docs.html). Full modern specification pages were inspected from an exact official repository tree; the versioning web page was inaccessible to the browser tool, so its official source was used.

## Protocol families

2025-11-25 uses initialize/initialized, negotiated capabilities and optional transport sessions/GET streams. A legacy stateless HTTP deployment still speaks this handshake family; its transport label does not establish 2026 support.

2026-07-28 is per-request: server/discover advertises identity, capabilities and versions; request params._meta carries io.modelcontextprotocol/protocolVersion plus client identity/capabilities. HTTP MCP-Protocol-Version must match the body; standard MCP request metadata headers also apply. Requests are independent, with no protocol sessions or legacy GET stream. Responses can be bounded JSON or per-request SSE; closing a response stream is cancellation. Optional resources/prompts/sampling/roots/elicitation/subscriptions are outside the selected host's Phase 3 surface.

## Actual disposable wire matrix

[Initial gate evidence](../development/evidence/phase-3-compatibility.json) and [selected-client evidence](../development/evidence/phase-3-client-foundation.json) comes from verification/mcp-compatibility. Tests use real loopback HTTP and official SDK peers, authenticated with randomly generated process-local credentials. No source fixture or existing volume is changed.

| Client | Server | Revision | Actual result |
| --- | --- | --- | --- |
| Official Java SDK 2.1.0 | Independent Java SDK 2.1.0 servlet server | 2025-11-25 | PASS initialize, tool schemas/list/call, structured result |
| Official Java SDK 2.1.0 | Official TS 2.4.0 modern-only handler | 2026-07-28 | UNSUPPORTED during initialize; no modern-client claim |
| Official TS client 2.4.0, auto negotiation | Java legacy peer | 2025-11-25 | PASS fallback, schemas/list/call, structured result, auth, timeout, disconnect |
| Official TS client 2.4.0, auto negotiation | TS modern-only peer | 2026-07-28 | PASS server/discover, per-request matching metadata/headers, no session, schemas/list/call, auth, timeout, disconnect |
| Selected Ecosync client foundation | Java SDK legacy proof process | 2025-11-25 | PASS real discovery, structured invocation and retired lease at B |
| Selected Ecosync client foundation | TS modern-only proof peer | 2026-07-28 | PASS real discovery, structured invocation and retired lease at B |

Negative wire checks reject invalid bearer/Origin, inconsistent version header/body and modern GET. Java/Node builds use the exact pinned Linux runtime images. The first native Windows launch failed before protocol communication because the JDK could not create its NIO selector; the isolated Linux proof passed and its disposable container exited cleanly.

## Architecture selection

[ADR 0008](decisions/0008-mcp-dual-era-client.md) selects an isolated official TypeScript client service behind a narrow authenticated Java interface. Spring remains the application, identity, authorization, registry, encryption and audit authority. No global Spring AI callback export or model dependency is introduced. Exact supported methods and schema/response bounds must be enforced by the client foundation before production registration is enabled.

Compatibility claims are restricted to revisions actually exercised: 2025-11-25 and 2026-07-28 over controlled Streamable HTTP. Bearer service credentials are the initial supported auth mechanism; OAuth onboarding and STDIO launch are not implemented.

The selected client also interoperates with both independently deployed source servers: Java Sales and TypeScript Inventory. Their six actual advertised tools and source-derived results are recorded in [source evidence](../development/evidence/phase-3-native-sources.json). Explicit wrong-era pinning is rejected in both directions. The official TS SDK reports CLIENT_HTTP_NOT_IMPLEMENTED when legacy initialize reaches its modern-only handler; the host maps that specific legacy handshake failure to MCP_UNSUPPORTED_PROTOCOL, while preserving network/auth/timeout errors separately.

Schema-library compatibility was verified against the [NetworkNT 3.0.8 publisher documentation](https://github.com/networknt/json-schema-validator/blob/3.0.8/README.md), the resolved Maven graph and compilation on the existing Boot BOM. MySQL client usage was checked against the [official prepared-statement documentation](https://sidorares.github.io/node-mysql2/docs/examples/queries/prepared-statements). No Spring milestone/RC upgrade or model-provider starter is needed.
