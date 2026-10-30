# Synthetic dataset specification
Version v1.0.0; generator seed 20261030; fixed reference time 2026-10-15T12:00:00Z. This is dataset preparation, **not benchmark execution or measured scientific evidence**.

Source artifacts are committed under each demo system's fixtures directory, with versioned migration/seed SQL for databases. evaluation/datasets/v1/manifest.json will reference their schema/fixture versions, counts, immutable SHA-256 hashes and generator. No runtime source loads the mappings, cases, expected results or another source's credentials.

Mappings live outside sources at evaluation/datasets/v1/mappings/. Every record includes mappingVersion, sourceSystem/sourceIdentifier, targetSystem/targetIdentifier or candidates, approvalStatus and notes. Approved product->SKU mappings have exactly one target and no conflicting approved record. Candidate similarity never approves a mapping. Inventory->catalog references allow multiple offers, not an implicit chosen supplier. Missing, conflicting and unit-mismatched relationships stay explicit.

Target coverage: 30 development cases, three in each of the ten research categories, plus six separately stored held-out cases. Categories: single_source, two_source, three_source, identifier_matching, ambiguous, unauthorized, unavailable, malformed_oversized, partial_results, complexity_boundaries.

Each case has stable id/question/category/split, requiredSources, expectedSourceFacts/calculations, expected identifiers/mapping IDs, status/completeness, permissions, fault condition, unresolved explanation, datasetVersion and mappingVersion. Expected business values are concrete numbers/decimal strings/IDs, computed from immutable fixtures by an independent oracle module. Fault/authorization outcomes are explicit policy expectations for later host tests; their presence does not claim Phase 2 executes an LLM or a benchmark engine.

Held-out cases are separate files and excluded from prompt examples. They are public synthetic reference data, not confidential test secrets; split discipline alone cannot prove an external model has never seen them.

Edge coverage includes repeated warehouse stock, zero/reserved/low stock, high-sales/low-inventory and low-sales/high-inventory items, overlapping offers, inactive suppliers, expired/boundary offers, minimum quantities, missing catalog refs, similar labels with distinct IDs, null optional values, multiple currencies/units, and order timestamps before/at/inside/after the UTC month boundary. Do not silently repair these differences.
