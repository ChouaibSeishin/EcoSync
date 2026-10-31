# ADR 0010: Independent fixed read-only source MCP servers

Accepted 2026-10-31. Sales uses the official Java SDK 2.1.0 on the stable Boot 4.1.2 / Spring AI 2.0.2 BOM and MCP 2025-11-25. Inventory uses official TypeScript server/core 2.4.0 and Node adapter 2.1.2, explicitly rejecting the legacy protocol family. These are separately deployed authenticated MCP endpoints, not host-local tool catalogs.

Each server has three fixed prepared-query tools and the existing source reader account. No schema changes, source copies, query generators, administrative tools, or model dependencies are introduced. Sales retains decimal strings, per-currency grouping, FULFILLED realized revenue and UTC half-open intervals. Inventory retains exact-case SKU identity, reservations, per-warehouse reorder thresholds, and units. Bounded top-N/list results explicitly report truncation. Supplier remains REST for Phase 5.
