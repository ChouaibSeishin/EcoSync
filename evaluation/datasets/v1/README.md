# Dataset 1.0.0

Wholly synthetic Meridian Office & Electronics source snapshot; fixed seed 20261030, reference time 2026-10-15T12:00:00Z. The manifest identifies source versions, immutable files and SHA-256 hashes. Source fixtures remain owned by their individual demo systems; mappings and expected results are external research artifacts.

development/cases.json contains 30 cases, three in each required category. development/expected.json contains explicit facts, exact calculations, source identifiers/provenance, mapping IDs, status/completeness, permissions and unresolved explanations. held-out contains six additional cases and expected results: keep these out of future prompt examples and model development. IDs Dxx/Hxx are stable; expectedRef selects the corresponding caseId.

Mappings contain 22 approved sales-to-inventory identities and 27 approved inventory-to-catalog memberships. Approval is a curated **synthetic reference assertion**, validated against identity/unit evidence; it is not a production mapping, real-world discovery, human operational approval or a match inferred from labels. Ambiguous, conflicting, missing and unit-incompatible assertions remain unusable. Catalog membership can resolve multiple offers without selecting a preferred supplier.

The independent reference calculator is evaluation/oracle/v1.mjs. It reads committed fixture data; it does not import fixture generators. Money uses BigInt minor units; no mixed-currency total or unit conversion is defined. SQL arithmetic in the live source checks provides a separate verification path.

From the repository root, after the supplier package's npm ci:

```sh
node scripts/demo/generate-sales.mjs --check
node scripts/demo/generate-inventory.mjs --check
node scripts/demo/generate-suppliers.mjs --check
node scripts/demo/generate-api-contract.mjs --check
node scripts/demo/prepare-mappings.mjs --check
node scripts/demo/prepare-cases.mjs --check
node scripts/demo/validate-dataset.mjs
node scripts/demo/prepare-ground-truth.mjs
node scripts/demo/verify-ground-truth-live.mjs
node scripts/demo/verify-reproducibility.mjs
```

Default ground-truth preparation recomputes in memory and compares, including hashes. An intentional baseline revision uses prepare-ground-truth.mjs --write after reviewing fixture/case changes; review and version the manifest and resulting diff together. Never use --write to conceal a failed check. The current v1 baseline is committed; future scientific runs must freeze the version before evaluation.

Fault files are deliberately invalid/untrusted examples, not served by the supplier application. Authorization/outage/malformed-tool expectations declare future host policy; source permissions and actual API validation are tested separately now. The 65536-byte budget is an explicit input of one hypothetical case, not an implemented platform limit. There is no benchmark executor, model answer, latency/accuracy result or scientific hypothesis test in this dataset preparation.
