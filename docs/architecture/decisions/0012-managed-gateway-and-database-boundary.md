# ADR 0012: Managed database data plane and tenant-neutral MCP tools

Date: 2026-11-03. Status: accepted after 11 actual HTTP compatibility checks; [sanitized evidence](../../development/evidence/phase-4-gateway-proof.json). The gateway implementation can now proceed. Source database behavior remains subject to later checkpoints.

## Compatibility and alternatives

Keep Java 21, Boot 4.1.2, Spring AI 2.0.2 BOM and explicit official Java SDK 2.1.0 core/Jackson 3. Programmatic servlet transport and SDK registration avoid implicit callback scanning and unused starter defaults. Serve the tested 2025-11-25 protocol only; modern 2026-07-28 is not a gateway claim. The Phase 3 official TS client can still consume this legacy endpoint. SDK 2.2.0-M1 offers a modern server but introduces prerelease APIs/Reactor; that maintenance cost is unnecessary here. A TS gateway is possible but would introduce a second connector authorization implementation and leave JDBC outside the selected Java boundary.

Option A generates tenant/table-specific names and schemas, using SDK request-time list filters. Its global process catalog still contains tenant metadata; filtering does not protect calls, and unfiltered session-level change notifications reveal catalog activity. Correctness requires more sensitive global state and careful filtering of every primitive.

Choose option B: a small catalog of tenant-neutral `database_describe_table` and `database_read_rows` tools. Structured arguments carry opaque connector/table UUIDs and exact revision, never trusted actor/org/role claims or raw SQL. A request-time filter consults current platform authority to decide whether the caller has any eligible approved active data. Call handlers independently reauthorize the exact table/revision/current grant. No tenant table names, descriptions or connection details appear in the global MCP schema. Descriptions return only the selected caller's approved metadata. The SDK's runtime add/remove is used when the persisted active catalog becomes empty/nonempty; scope changes also reconcile/retire connections. Runtime mutation failure cannot grant access because persisted authorization is always checked. Reconcile retries reconstruct from authority at startup and periodically, never from local durable grants.

SDK notifications are proven in the disposable gate but disabled in the filtered production catalog (`listChanged=false`) because the stable SDK broadcasts them per session, not per authenticated request. Clients explicitly refresh discovery. This is a deliberate tenant confidentiality limit, not missing authorization or a claim of tenant-filtered notification support.

## Process and trust boundaries

Extract a separate Java `managed-gateway` process now: the mandatory source-network boundary prevents placing JDBC in the platform process. It joins dedicated internal control and the explicitly approved source network; backend stays on metadata/control networks only. Phase 3 client and native servers remain separate. No host gateway port, platform DB connection, full AES keyring or signing private key is mounted into the gateway.

Backend owns separate database connector tables, immutable revisions, metadata snapshots, opaque table identities, approvals and sanitized audit. This shares identity/governance patterns with native MCP but does not overload remote-native endpoints. The existing platform credential resolver is extended with a separate purpose-bound encrypted database secret table; only the backend has encryption keys. The gateway obtains short-lived connection material over authenticated internal authorization callbacks and keeps it only in bounded connection resources. Responses, exceptions and audit never expose it. Operator key compromise and gateway process compromise remain trust-boundary risks.

Transport service authentication and delegation are separate: a generated database gateway bearer authenticates the internal service; backend signs Ed25519 assertions with issuer, gateway audience, actor UUID, organization UUID, exact connector/revision, action, execution UUID, issue/expiry times (at most 30 seconds) and unique assertion ID. Gateway holds only the public verification key. Every HTTP request verifies signature, audience and time; callback authority verifies again and checks live membership/role/state/revision/metadata/scope. Ordinary tool arguments cannot override that context. Assertions are scoped to a single execution; no cross-operation replay is accepted. Loss of authority fails closed. Session handles never replace request authentication.

Manual data exploration uses an official Java SDK client from backend over the private MCP HTTP endpoint. Test/discovery control operations use a separate bounded authenticated internal interface; user queries always cross MCP. No global model tool callback catalog is introduced. Both official TS and Java clients are tested against the gateway. One backend replica remains the supported deployment.

## JDBC and lifecycle

Only typed PostgreSQL/MySQL settings; no supplied JDBC URLs/properties/classes. Operator policy fixes host/port/database/schema and allowed private addresses. Each socket creation resolves/checks all addresses and pins one validated address while preserving the original TLS hostname. Production requires verified TLS; only explicit development entries permit plaintext. Metadata uses bounded JDBC APIs without sample rows. Fingerprints cover table identity, columns/types/nullability and PK/FK structure. Schema/scope/config changes stale approvals.

Single-table structured reads resolve approved identifiers, dialect-quote them and bind typed values. Deterministic primary-key ordering, limit-plus-one truncation, row/byte/deadline/concurrency limits and database grants bound execution. Separate source reader accounts are provisioned without modifying business schemas/data or existing native accounts. Disable/revision/revoke persists the fence first, retires pools/cancels statements best effort, and rejects late results after reauthorization. An already admitted read cannot be undone; no subsequent admission can rely on a cached grant. Catalog/connection cleanup failure does not reactivate a connector. Bounded startup/shutdown and periodic retry are required.

## Official sources inspected

- [SDK 2.1.0 server builder and filter contract](https://github.com/modelcontextprotocol/java-sdk/blob/v2.1.0/mcp-core/src/main/java/io/modelcontextprotocol/server/McpServer.java)
- [SDK 2.1.0 runtime mutations](https://github.com/modelcontextprotocol/java-sdk/blob/v2.1.0/mcp-core/src/main/java/io/modelcontextprotocol/server/McpSyncServer.java)
- [SDK 2.1.0 servlet request context](https://github.com/modelcontextprotocol/java-sdk/blob/v2.1.0/mcp-core/src/main/java/io/modelcontextprotocol/server/transport/HttpServletStreamableServerTransportProvider.java)
- [Spring AI 2.0.2 server documentation and unauthenticated endpoint warning](https://docs.spring.io/spring-ai/reference/api/mcp/mcp-server-boot-starter-docs.html)
- [SDK releases and modern milestone limitations](https://github.com/modelcontextprotocol/java-sdk/releases)
- [pgJDBC TLS](https://jdbc.postgresql.org/documentation/ssl/) and [security/release information](https://jdbc.postgresql.org/security/)
- [MySQL JDBC TLS identity verification](https://dev.mysql.com/doc/connector-j/en/connector-j-reference-using-ssl.html)

Actual proof evidence and operational limits must accompany acceptance. This supersedes ADR 0003's initial in-process gateway deployment proposal; it does not replace the Phase 3 native client decision.
