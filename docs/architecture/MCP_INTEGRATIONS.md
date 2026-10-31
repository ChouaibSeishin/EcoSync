# Native MCP integrations

Phase 3 in progress, 2026-10-31. Checkpoint B delivers a replaceable Java NativeMcpClient interface and an isolated official-SDK client process; persistence, approvals and UI are subsequent checkpoints and are not yet claimed.

## Client boundary

Java owns authenticated actor/org context, immutable registration revisions, credential resolution and execution-time grants. The internal process uses official TypeScript client/core 2.4.0 for real Streamable HTTP; it is a client adapter, not the future managed MCP gateway. No tools are hardcoded in its catalog and no Spring AI model callbacks are globally exported.

Internal bearer-authenticated POST operations are /v1/connect, /v1/discover, /v1/invoke, /v1/cancel and /v1/close. Inputs are exact bounded objects. Connection keys contain organization UUID/integration UUID/configuration version; leases additionally fence replaced clients. Disabling a revision can revoke its lease; the persistent Java policy remains authoritative. Idle clients retire after five minutes and process shutdown closes clients. Endpoint and credential values are never returned.

The implementation exposes only tested Tools functionality for 2025-11-25 and 2026-07-28. Protocol policies are LEGACY_2025, MODERN_2026 and AUTO_DUAL; other negotiated revisions are rejected. Root/sampling/elicitation handlers, STDIO onboarding, task execution, resource/prompt UI, OAuth onboarding and LLM execution are absent.

## Operational bounds

Connect/socket inactivity: five seconds. Individual tool request: ten seconds. Complete connect/discovery/invocation operation: thirty seconds. At most four concurrent operations per integration and 64 cached client slots. Discovery has eight pages, 100 tools, repeated-cursor rejection and two MiB total raw evidence. Each JSON/SSE response is limited to one MiB while streaming. Schemas are at most 32 KiB, depth eight and 200 schema nodes; descriptions are bounded. Invocation arguments are at most 16 KiB; internal requests at most 64 KiB. Java's internal HTTP response subscriber enforces a two MiB limit while receiving and cancels requests at the operation deadline.

No application retry is performed for tool calls. SDK probe/transport retries are disabled. Failed connections never produce a fake catalog/result. A previously authorized remote read cannot be undone after execution, but cancellation retires active transport work and disabled/configuration revisions cannot start new invocations through Java.

## Schema and metadata policy

Remote names/descriptions/annotations/results remain untrusted data. Names are dispatch data only after scoped capability authorization; the persistent capability ID/alias will be opaque and integration-aware. Schema fingerprints cover the discovered input/output/description/annotation metadata. Before every invocation the client refreshes the real catalog and rejects changed fingerprints before tools/call.

The safe JSON Schema subset supports bounded object/array structures, primitives, null unions, required/properties, boolean additionalProperties, enums, numeric/string/item bounds and UTC date-time. Draft 7 and 2020-12 are supported for this subset. External/local references, executable transforms, regex patterns, combinators and unbounded arrays are visibly unsupported; unsupported tools cannot be invoked. No remote schema document is fetched. AJV performs validation, without coercion or removing fields. The Java application will validate again against the persisted schema before dispatch.

The official SDK's convenience list aggregator terminates repeated identical pages; the host instead uses its public per-request method and performs its own explicit cursor walk, so a cycle becomes an error rather than a silently truncated complete catalog.

## Network boundary

Operator configuration lists exact canonical MCP endpoint URLs. HTTPS is required outside an explicit development mode. Development HTTP exceptions are exact operator endpoints; tenant admins cannot expand them. DNS is re-resolved and every returned IPv4/IPv6 address checked before each HTTP request, then the socket is pinned to a validated address. Metadata/link-local/unspecified and unsafe IPv6 forms remain denied even in development. Redirects never forward credentials, TLS verification remains enabled, and no credential is sent to a different path/origin.

The internal client service rejects browser Origin headers, has its own distinct service credential, and is not intended for a published host port. Future Compose setup joins only the MCP control network; source DB credentials remain exclusively with the native servers.
