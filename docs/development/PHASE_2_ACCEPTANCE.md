# Phase 2 acceptance

Status 2026-10-30: **Phase 2 complete**. Checkpoints A-G passed locally and in [actual run 41061522829](https://github.com/ChouaibSeishin/EcoSync/actions/runs/41061522829), code commit 002cdd6348dd1f39481255ae4472d66a967ce559. Both jobs completed successfully; no Phase 3 work is authorized or started.

| Requirement | Delivered evidence |
| --- | --- |
| Exact Phase 1 base and reconciliation | Branch based on 603cfb9; final-run addenda in status and both Phase 1 reports; prior branches preserved |
| Independent sales PostgreSQL | Separate sales schema/volume/roles; V001/V002; 628 exact business rows, seven reader denials, invalid password and five constraints |
| Independent inventory MySQL | Separate InnoDB database/volume/accounts; 123 exact rows, eight reader denials, invalid password and five constraints |
| Independent supplier REST | Node 24/TypeScript/Fastify, own immutable JSON, non-root container; 14 passing test groups, validated OpenAPI, exact real HTTP snapshot |
| Heterogeneous semantics | Integer sales IDs, case-sensitive SKUs, supplier catalog/offer IDs; currency/units/time/discount/validity explicit |
| External reference mappings | 65 versioned records; approvals, ambiguity, conflicting candidates, null/missing and rejected unit relationships |
| Independent ground truth | 30 development (3/category) plus 6 held-out; explicit facts/calculations/provenance/status; deterministic BigInt oracle and independent live SQL |
| Reproducibility | 27 SHA-256 artifacts; two fresh random-credential projects have identical actual business fingerprint |
| Isolated lifecycle and persistence | Each source stopped/restarted independently; rows retained; platform ready with all demos stopped; localhost bindings/resource/non-root checks |
| Phase 1 regression | Four backend unit + ten PostgreSQL integration tests, ten frontend tests, build/lint/types/audit, real browser and outage/recovery passed locally and in the preserved CI job |
| Actual CI / pushed branch | Branch codex/phase-2-demo-ecosystem pushed; run 41061522829: verify success, Independent demo sources and dataset success |
| Scope boundary | No MCP/connectors/AI/chat/mapping UI/deployment/benchmark execution; Phase 3 awaits explicit authorization |

The initial run 41060229162 passed Phase 1 but failed demo initialization; the portability fix and successful rerun are retained in the verification history. A subsequent documentation-only acceptance commit is subject to the same full workflow; its exact run is reported in the task handoff.

[Verification record](PHASE_2_VERIFICATION.md) contains actual commands, corrected failures, dataset fingerprint, Git checkpoints and final run evidence. [Setup](DEMO_SYSTEMS_SETUP.md), [architecture](../architecture/DEMO_ECOSYSTEM.md), [dictionary](../architecture/DEMO_DATA_DICTIONARY.md), [API contract](../architecture/DEMO_API_CONTRACT.md), [dataset](../research/DATASET_SPECIFICATION.md) and [ground-truth method](../research/GROUND_TRUTH_METHOD.md) define the shipped contracts and limitations.
