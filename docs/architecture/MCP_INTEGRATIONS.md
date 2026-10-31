# Native MCP integrations

Observed 2026-10-31. Native MCP uses a replaceable Java NativeMcpClient interface and an isolated official-SDK client process. PostgreSQL registration, immutable revisions, AES-GCM credentials, discovered capabilities, explicit role grants and the existing React management UI are implemented. Verification and remaining acceptance work are tracked in [the Phase 3 record](../development/PHASE_3_VERIFICATION.md).

## Client boundary

Java owns authenticated actor/org context, immutable registration revisions, credential resolution and execution-time grants. The internal process uses official TypeScript client/core 2.4.0 for real Streamable HTTP; it is a client adapter, not the future managed MCP gateway. No tools are hardcoded in its catalog and no Spring AI model callbacks are globally exported.

Internal bearer-authenticated POST operations are /v1/connect, /v1/discover, /v1/invoke, /v1/cancel and /v1/close. Inputs are exact bounded objects. Connection keys contain organization UUID/integration UUID/configuration version; leases additionally fence replaced clients. Disabling a revision can revoke its lease; the persistent Java policy remains authoritative. Idle clients retire after five minutes and process shutdown closes clients. Endpoint and credential values are never returned.

The implementation exposes only tested Tools functionality for 2025-11-25 and 2026-07-28. Protocol policies are LEGACY_2025, MODERN_2026 and AUTO_DUAL; other negotiated revisions are rejected. Root/sampling/elicitation handlers, STDIO onboarding, task execution, resource/prompt UI, OAuth onboarding and LLM execution are absent.

## Operational bounds

Connect/socket inactivity: five seconds. Individual tool request: ten seconds. Complete connect/discovery/invocation operation: thirty seconds. At most four concurrent operations per integration and 64 cached client slots. Discovery has eight pages, 100 tools, repeated-cursor rejection and two MiB total raw evidence. Each JSON/SSE response is limited to one MiB while streaming. Schemas are at most 32 KiB, depth eight and 200 schema nodes; descriptions are bounded. Invocation arguments are at most 16 KiB; internal requests at most 64 KiB. Java's internal HTTP response subscriber enforces a two MiB limit while receiving and cancels requests at the operation deadline.

No application retry is performed for tool calls. SDK probe/transport retries are disabled. Failed connections never produce a fake catalog/result. A previously authorized remote read cannot be undone after execution, but cancellation retires active transport work and disabled/configuration revisions cannot start new invocations through Java.

## Schema and metadata policy

Remote names/descriptions/annotations/results remain untrusted data. Names are dispatch data only after scoped capability authorization; the persistent capability UUID and UUID-derived alias are opaque and unique across integrations. Schema fingerprints cover the discovered input/output/description/annotation metadata. Before every invocation the client refreshes the real catalog and rejects changed fingerprints before tools/call. The registry retains stale metadata and approval history.

The safe JSON Schema subset supports bounded object/array structures, primitives, null unions, required/properties, boolean additionalProperties, enums, numeric/string/item bounds and UTC date-time (seconds or up to three fractional digits in the client). Draft 7 and 2020-12 are supported for this subset. External/local references, executable transforms, regex patterns, combinators and unbounded arrays are visibly unsupported; unsupported tools cannot be approved or invoked. No remote schema document is fetched. AJV 8.20.0 compiles schemas during discovery and validates without coercion or removing fields. Java independently validates persisted schemas/arguments and declared output using NetworkNT 3.0.8, its Java 17+/Jackson 3 line. Configured token literals are redacted from text and JSON keys; a schema/name altered by redaction becomes noninvocable.

The official SDK's convenience list aggregator terminates repeated identical pages; the host instead uses its public per-request method and performs its own explicit cursor walk, so a cycle becomes an error rather than a silently truncated complete catalog.

## Network boundary

Operator configuration lists exact canonical MCP endpoint URLs. HTTPS is required outside an explicit development mode. Development HTTP exceptions are exact operator endpoints; tenant admins cannot expand them. DNS is re-resolved and every returned IPv4/IPv6 address checked before each HTTP request, then the socket is pinned to a validated address. Metadata/link-local/unspecified and unsafe IPv6 forms remain denied even in development. Redirects never forward credentials, TLS verification remains enabled, and no credential is sent to a different path/origin.

The internal client service rejects browser Origin headers, has its own distinct service credential, and has no published host port. It joins only the MCP control network; source DB credentials remain exclusively with the native servers.

## Persistent lifecycle and API

Registration creates DRAFT revision 1. Test/discover claims VALIDATING with a compare-and-set; success persists actual identity, version, schemas and timestamps and returns DRAFT/HEALTHY awaiting review. Failure is DEGRADED/UNREACHABLE. Explicit grants plus successful validation permit Activate. ACTIVE governance and HEALTHY/UNREACHABLE transport health remain distinct. Disable persists DISABLED, invalidates grants and revokes the entire revision's adapter generation, including after backend restart when its previous lease is no longer cached. Edit creates a new immutable revision, DRAFT state and stale old grants. Removed registrations are excluded from normal API views; revisions/audit persist. A successful connection never authorizes tools or reactivates a disabled registration.

All routes below are under `/api/organizations/{organizationId}/integrations` and require current membership. All writes require the existing session and CSRF token. INTEGRATION_ADMIN controls registration/governance; invocation requires an explicit current capability grant for one of the actor's organization roles. AUDITOR never receives an execution grant. The action is tools/call only, not arbitrary MCP method dispatch.

| Route | Operation |
|---|---|
| GET /, POST / | List bounded summaries / register a permitted native server |
| GET /{id}, PUT /{id} | Current metadata/history / create a new configuration revision |
| POST /{id}/validate | Actual connection and discovery, never approval |
| POST /{id}/capabilities/{cap}/approval | Approve or revoke INTEGRATION_ADMIN or USER for this current schema |
| POST /{id}/activate, POST /{id}/disable, DELETE /{id} | Govern registration and retire access |
| POST /{id}/capabilities/{cap}/invocations | Validated JSON arguments and caller-generated invocation UUID |
| POST /{id}/invocations/{invocation}/cancel | Best-effort cancellation, initiator or integration administrator only |

Audit stores actor/org/integration/revision/capability/protocol/correlation/start/end/duration/status/authorization/error codes. Arguments/results are not stored. The UI shows the latest 30 events, 20 revisions, up to 100 current tools plus bounded stale metadata, and bounded approval history. Full retained history remains an operator database concern. There are at most 64 nonremoved registrations per deployment and 64 adapter connections. One backend replica is supported; horizontal deployment needs distributed admission/cancellation ownership. Startup marks unfinished events INTERRUPTED and reconstructs active clients lazily with fresh schema checks. Tool calls are never automatically retried.

## Independent source tools

| Server / protocol | Tool | Fixed source query semantics |
|---|---|---|
| Java Sales / 2025-11-25 | sales_get_revenue_summary | Orders joined to lines, FULFILLED only, SUM(quantity * unit_price - discount_amount), grouped by currency; optional product/currency filters |
| Java Sales / 2025-11-25 | sales_get_top_products | Same realized revenue joined to products, one required currency, full aggregation before ranking, stable product-ID ties, maximum 50 plus a truncation probe |
| Java Sales / 2025-11-25 | sales_get_order_summary | Group by order and currency, status/order filters, zero realized revenue for non-FULFILLED, maximum 100 plus truncation probe |
| TypeScript Inventory / 2026-07-28 | inventory_get_stock_by_sku | Exact-case SKU, per-warehouse on-hand/reserved/available/threshold/status/unit, bounded three rows plus truncation probe |
| TypeScript Inventory / 2026-07-28 | inventory_list_low_stock | Available <= threshold; OPEN warehouses and active items by default, explicit overrides, stock-ID cursor, limit <=100 |
| TypeScript Inventory / 2026-07-28 | inventory_get_warehouse_stock | Exact warehouse and optional unit, case-sensitive SKU cursor, limit <=100 |

All filters are prepared parameters; SQL/identifiers/order clauses are never caller-supplied. Sales windows require UTC [startInclusive,endExclusive), positive length <=366 days, with exact decimal strings and no mixed-currency totals. Inventory never converts units. The existing reader roles cannot write, migrate or read administrative history. Source query timeouts are bounded to four/five seconds. No source schema/fixture was changed. [Native-source evidence](../development/evidence/phase-3-native-sources.json) compares actual results to immutable fixtures, including USD 145444.58, EUR 32033.59 and GBP 29495.75 for October 2026 FULFILLED revenue.

Run commands, key ownership, operator endpoint policy and complete UI steps are in [MCP_LOCAL_SETUP.md](../development/MCP_LOCAL_SETUP.md). Security decisions are in [MCP_SECURITY.md](MCP_SECURITY.md). Supplier remains REST; neither the native servers nor the adapter implement Phase 4/5 managed connectors or Phase 6 AI execution.
