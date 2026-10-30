# ADR 0007: Independent, deliberately heterogeneous demo sources
Date: 2026-10-30. Status: accepted Phase 2 design; implementation evidence will follow.

Choose PostgreSQL sales, MySQL inventory and a Node 24/Fastify supplier REST service to exercise two SQL dialects plus an authenticated HTTP contract. Keep independent DBs, volumes, credentials, schema histories and runtime ownership. Keep the platform unchanged and independently startable.

Sales uses integer product IDs and exact NUMERIC revenue. Inventory uses case-sensitive alphanumeric SKUs and warehouse stock/unit semantics. Supplier offers use catalog references and their own supplier/offer IDs, decimal strings and validity ranges. Do not introduce a common product key or infer matches from labels.

Supplier persistence is a versioned immutable JSON snapshot, sufficient for this read-only synthetic source; it avoids an unnecessary fourth database. Fastify is selected after current Node compatibility and dependency metadata checks. The OpenAPI artifact is versioned and validated independently. No source requires another source or the reference mappings to run.

Use one deterministic fixture authoring process, independent expected-result derivation, explicit reviewed mapping statuses and immutable versioned hashes. Separate held-out cases from development examples. Source initialization is migration/seed-hash checked, idempotent and non-destructive. Actual reader write/schema denials and live snapshots are required acceptance evidence.

A dedicated localhost-only demo Compose lifecycle has no platform depends_on links. Future MCP servers/approved connector tests can explicitly join the private source network and use reader/API credentials; this phase implements none of those integrations. Revisit larger supplier persistence, schema evolution or additional protocols only when evidence justifies them.
