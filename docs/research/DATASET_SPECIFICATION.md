# Dataset specification: v1.0.0

Scenario: Meridian Office & Electronics, a wholly synthetic electronics/office distributor. Fixed seed 20261030 and reference instant 2026-10-15T12:00:00Z. Source schemas and fixtures have independent version labels: sales-v1 (V001 schema/V002 seed), inventory-v1 (V001/V002), supplier-v1 (OpenAPI 1.0.0). No customer names, contacts or real suppliers exist.

| Source | Delivered records |
| --- | ---: |
| Sales products / anonymous customers | 34 / 18 |
| Sales orders / lines | 144 / 432 |
| Inventory SKUs / warehouses / stock rows | 30 / 3 / 90 |
| Supplier records / offers | 10 / 40 |

The repository artifact evaluation/datasets/v1/manifest.json records exact fixture references, versions, counts and 27 SHA-256 hashes covering source fixtures, SQL, OpenAPI, supplier lockfile, fixture/mapping/case generators, schemas, oracle, mappings, cases, expected results and fault inputs. Validation recomputes expected results and hashes without modifying the baseline. Clean initialization twice produced identical business data; see [verification](../development/PHASE_2_VERIFICATION.md).

## Mappings and identity

External mappings have mapping ID/version, source system/identifier, target system/identifier or candidates, approval status, review method and notes. There are 65 records: 35 sales identity assertions (one competing duplicate), and 30 catalog assertions. Exactly 22 sales identities and 27 catalog memberships are approved. These are curated synthetic reference assertions validated against source existence and compatible units, not operational human approvals or name-based matches.

Sales-only, inventory-only, absent/null references, two ambiguous candidates, competing conflicting assertions, a rejected M-to-BOX relationship and multiple supplier offers remain explicit. Only a unique approved identity is usable. No source stores these mapping records; no platform mapping feature exists.

## Case structure and split

development/cases.json contains 30 cases: exactly three in each category below. held-out/cases.json contains six additional cases; keep this directory and its expected results out of future prompt examples/model development.

| Development category | Count |
| --- | ---: |
| Single-source retrieval | 3 |
| Two-source questions | 3 |
| Three-source questions | 3 |
| Matching and mismatching identifiers | 3 |
| Ambiguous questions | 3 |
| Unauthorized requests | 3 |
| Unavailable sources / invalid credentials | 3 |
| Malformed / oversized responses | 3 |
| Partial results | 3 |
| Complexity / result-size boundaries | 3 |

Each case has stable ID, question, category, split, required sources, operation/inputs, permissions, source/dataset/mapping versions and an expectedRef. Each corresponding expected.json record contains concrete source facts/calculations, identity/mapping IDs, exact row provenance, expected status/completeness, permissions, fault metadata and unresolved explanation. JSON Schemas and semantic validators enforce these relationships.

Of 36 expected records, 26 are fixture-derived, nine declare future enforcement policy, and one combines authorized inventory facts with an outage-policy expectation. Policy cases do not claim to execute an absent MCP host. Actual database denials and malformed/unauthorized API input are tested separately. Fault payloads are not served by the supplier API. The 65536-byte budget belongs only to an explicitly parameterized hypothetical response case.

Financial arithmetic uses BigInt minor units and PostgreSQL NUMERIC cross-checks; quantities stay separated by unit; UTC periods are [startInclusive,endExclusive). Offer eligibility checks supplier activity, validity, quantity bounds, currency and unit without choosing a cheapest supplier. A valid query can completely establish that no offer is eligible. Missing mappings produce partial/unresolved results, preserving source-local facts.

No LLM generates expected answers. No benchmark engine, accuracy/latency measurement or hypothesis evaluation is implemented. [Ground-truth method](GROUND_TRUTH_METHOD.md) documents the independent calculation and live verification paths.
