# ADR 0013: Isolated REST execution and configuration-driven MCP

Date: 2026-11-05. Status: accepted. [Observed implementation and verification evidence](../../development/PHASE_5_VERIFICATION.md) records five successful CI jobs, controlled TCP/TLS tests and both actual REST browser journeys. Final branch-head verification remains separately identified from application acceptance.

## Context and alternatives

Phase 4 application acceptance remains `18aafb7d41011171e64da25e14cc1bdf363fc533`, run 41812225715. Its final handoff is `ec310f8c25b4778396bdc5f698f3ef7253967401`, run 41814237164, all four jobs successful. Historical records are retained. Phase 5 branches from that exact final head.

Executing arbitrary HTTP in the authority backend would extend its data-plane privileges. Adding REST egress to the JDBC gateway would combine unrelated source credentials, protocols and attack surfaces. A dedicated REST gateway is selected. The backend keeps tenant identity, immutable revisions, explicit operation/role approvals, encrypted secrets and audit. The REST gateway has neither platform database access nor AES keys nor a delegation signing key. The database gateway remains unchanged.

## Network and request paths

Browser -> backend -> private REST MCP gateway -> operator-approved API. Backend and gateway share an internal REST control network. Backend does not join REST source networks. The supplier remains independently owned on its existing internal fixture network; a separate partner fixture runs independently. Only the REST gateway joins the intended REST source paths. Joining a Docker network permits connections to other members: Compose does not implement per-process/per-port firewall rules. This limitation must be documented and measured, not called complete egress isolation. Local internal networks prohibit ordinary public internet routing. Production deployment requires operator egress firewall/proxy rules in addition to application admission.

Operator entries specify the exact scheme, hostname, effective port, normalized base path and development/TLS mode. Administrators cannot extend them. HTTPS requires default trust and original-hostname verification. HTTP is permitted only for explicit local development entries. All DNS answers must pass IP classification and operator CIDRs. Resolve at admission and again before socket creation, reject changed address sets, and connect to an InetAddress (never let another HTTP client resolve the hostname). TLS wraps that connected socket with the original hostname and HTTPS endpoint identification. Bound DNS workers, socket deadlines and total execution. No proxy settings, redirects, cookies, connection reuse or automatic retries. A deliberately strict bounded HTTP/1.1 transport using the JDK socket/TLS APIs avoids introducing a mutable resolver hook or unverified HTTP library dependency. Chunked framing, gzip, headers and decoded bytes need independent tests. Unsupported framing/encoding fails closed. Closing each operation's socket makes pooling retention zero and disables unsafe reuse after DNS changes.

## Contracts and approvals

Accept JSON OpenAPI 3.0.x/3.1.x uploads/paste only. Never fetch a document URL. Strict duplicate-key detection, finite trees and bounded local JSON Pointer expansion precede review. Reject external/relative/cyclic/unresolved references. Imported servers are suggestions only. Supported GET operations use scalar path/query parameters and bounded JSON success schemas. Unsupported features receive explicit review reasons and cannot be approved. Document and normalized operation fingerprints bind approvals to immutable configuration, auth settings and credential reference. Import is not approval. No automatic approval during recovery/restart/revision.

No API-specific business operations are compiled into the backend or gateway. Supplier and partner documents pass the same importer and registry API. A neutral global catalog (`rest_describe_operation`, `rest_invoke_operation`) contains no source names or per-tenant schemas. Explicit refresh, no broadcast catalog notifications; only protocol 2025-11-25 is claimed with the existing SDK 2.1.0.

## Authority and secrets

REST gets its own service bearer, Ed25519 keypair, audience `ecosync-rest-gateway` and REST action set. Assertions bind issuer, actor, organization, connector, revision, opaque operation ID, canonical argument hash, execution ID, issue/expiry and unique ID. Verify on every request; consume invocation assertions once. Fresh backend callbacks check live membership, role, state, immutable revision, operation approval fingerprint and RUNNING execution before dispatch and result release. Backend repeats authorization and compare-and-set completion under its connector governance lock. Fail closed when authority is unavailable.

Credential ciphertext has a separate REST table and `ecosync-rest/organization/credential/keyId` AES-256-GCM associated data, fresh nonce and the external versioned backend-only keyring. Supported auth: NONE, BEARER, HEADER_API_KEY. No query credentials, arbitrary headers, Basic, cookies, OAuth or client certificates. API-key header names are validated and cannot replace transport/framing/forwarding headers. Secrets are write-only in user APIs; do not retain them in UI query caches. Gateway receives short-lived material only from authority callbacks; no secret persistence or raw request/response audit.

## Execution and residual risks

Validate typed allowlisted arguments independently on backend and gateway, percent-encode path segments/query components, and reject traversal, ambiguous templates, header/host/scheme injection. GET only, no body. Bound concurrency globally/per connector, DNS/connect/request/operation time, compressed/decompressed bytes, JSON depth/nodes/items. Schema mismatch, non-JSON, redirect, non-2xx, authentication and limit errors are explicit sanitized failures. External data is never executable instructions. Disable/revoke/cancel persists a fence before best-effort socket closure; late results must fail authorization. Cancellation cannot undo a remote GET already received. One authority replica remains supported; distributed fencing is outside this phase. Gateway/host compromise can expose in-memory source credentials; local development control HTTP and host networking are not production certification.

## Official references checked

- [OpenAPI 3.0.4](https://spec.openapis.org/oas/v3.0.4.html), [3.1.2](https://spec.openapis.org/oas/v3.1.2.html): subset policy, not a claim of full support.
- [JDK 21 SSLSocket](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/javax/net/ssl/SSLSocket.html): layered sockets and endpoint identification.
- [Existing official SDK 2.1.0 source](https://github.com/modelcontextprotocol/java-sdk/blob/v2.1.0/mcp-core/src/main/java/io/modelcontextprotocol/server/McpServer.java): programmatic neutral tools and request filters. No SDK upgrade selected.

Evidence of these controls must be recorded separately from this design decision.
