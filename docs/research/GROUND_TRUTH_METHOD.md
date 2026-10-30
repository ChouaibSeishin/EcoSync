# Ground-truth preparation method
No LLM computes expected answers. No accuracy, latency, cost or interoperability hypothesis is evaluated in Phase 2.

1. A deterministic fixture generator emits source-owned canonical JSON and seed SQL from fixed seed/version/time. It does not generate expected answers.
2. A separate oracle reads those committed artifacts and reviewed external mappings. Currency arithmetic uses integer minor units (BigInt); amounts are emitted as exact two-place strings. Inventory arithmetic is integer subtraction/grouping by unit. Time comparisons use explicit UTC boundaries.
3. Case definitions select inputs; the oracle derives explicit expected facts/provenance/calculations into development and separate held-out files. Default validation recomputes and compares, never overwrites results to conceal a discrepancy.
4. Live source checks compare every initialized business row with canonical fixture artifacts, using text representations for decimals/timestamps. Independent PostgreSQL NUMERIC aggregates and MySQL stock expressions cross-check oracle arithmetic. Supplier authenticated HTTP responses are compared with their JSON snapshot.
5. Versioned manifest hashes cover fixtures, migrations, mappings and case/expected artifacts. Generate to temporary memory and compare for a reproducibility check; updating the fixture baseline requires an explicit generator write.
6. Two fresh isolated Compose projects must produce identical business snapshots despite random credentials. Restart checks must retain data. Existing platform/demo volumes are never deleted as a test shortcut.

A mapping with no approved unambiguous target produces unresolved data, not a fabricated join. Partial cases retain source-local facts and identify missing dependencies. Unauthorized/fault cases contain declared future policy outcomes; actual source credential denials and malformed API requests are independently exercised now. This is a bounded fixture/oracle validation toolkit, not an agent or general platform query executor.
