# Supplier API contract
Implemented: OpenAPI 3.0.3, version 1.0.0. The versioned artifact is at demo-systems/supplier-api/openapi.json and is served unchanged at GET /openapi.json.

Public GET /health/live, /health/ready and /openapi.json contain no credentials. Readiness requires a validated, internally consistent supplier snapshot and an externally supplied token. Data endpoints require Authorization: Bearer <local-demo-token>; absent/invalid credentials return 401 with WWW-Authenticate. No write endpoint is implemented.

| Endpoint | Response / filtering |
| --- | --- |
| GET /v1/suppliers | Stable supplier_id ascending; page default 1, pageSize default 10/max 50; active and exact region filters |
| GET /v1/suppliers/{supplierId} | One supplier or structured 404 |
| GET /v1/offers | Stable offer_id ascending; page/pageSize; exact supplierId/catalogReference/currency/unitCode, supplierActive and explicit validAt timestamp filters |
| GET /v1/offers/{offerId} | One offer or structured 404 |

Lists return items, page, pageSize, total and snapshotVersion. Empty pages return 200 with [] and accurate total. Invalid/unknown/duplicate query parameters, bad integer/boolean/timestamp values or page sizes above 50 return 400, not silent coercion/clamping. Invalid path identifiers return 400; identifiers over the 64-character router bound or URLs over 2048 characters return structured 414; well-formed unknown IDs return 404. Unsupported methods return 405 on known resources. Errors have error.code/message/requestId and optional bounded field names, never submitted tokens or raw input.

Runtime request validation and Fastify response schemas and the committed contract must agree. A pinned OpenAPI parser validates the artifact; API tests verify operations and response shapes. Decimal prices remain strings, preventing floating-point rounding in JSON clients. No current-clock default eligibility filter: callers provide validAt when needed. Body, URL/header and connection limits are bounded; request logging excludes credentials and is disabled for the demo by default.
