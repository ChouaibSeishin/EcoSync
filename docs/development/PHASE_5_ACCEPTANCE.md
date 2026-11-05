# Phase 5 acceptance

Status: implementation accepted against observed application evidence, 2026-11-05. Final branch-head verification is a separate handoff gate described below.

Base `ec310f8c25b4778396bdc5f698f3ef7253967401`; branch `codex/phase-5-dynamic-rest-connectors`. [Verification record](PHASE_5_VERIFICATION.md), [architecture](../architecture/REST_CONNECTORS.md), [security](../architecture/REST_SECURITY.md), [ADR 0013](../architecture/decisions/0013-isolated-rest-egress.md).

Application head `4c5c7404fedfc1abe1e1a1d99b40e02441e437b0` passed all five jobs in [actual run 42018755865](https://github.com/ChouaibSeishin/EcoSync/actions/runs/42018755865). [Sanitized CI evidence](evidence/phase5-application-ci.json) records the exact head, job conclusions and observed counts. This historical run does not verify a later commit. The final documentation/regex-guard change requires its own matching remote-head five-job run; its exact SHA/run is recorded in the final handoff and local `.verification/phase5/final-ci.json`, without a subsequent tracked edit invalidating that head.

| Acceptance gate | Observed evidence |
|---|---|
| Dynamic onboarding without core adapters | Independent Supplier OAS 3.0.3/Bearer and Partner OAS 3.1.0/header-key APIs use the same contract importer, registry, UI and generic gateway; both real browser journeys passed |
| Strict import and review | Local references only; malformed, cyclic, external, duplicate and unsupported features rejected; deterministic normalized fingerprints; unsupported operations remain visible and unapprovable |
| Persistent governance | V5 Flyway migration; immutable tenant revisions; explicit GET/role grants; test after approval; stale revision/disable/revoke and recovery tested in 16 real PostgreSQL REST integration tests |
| Credentials and tenant boundaries | Distinct REST AES-GCM purpose/AAD and REST delegation audience; key rotation/ciphertext substitution/revocation tested; write-only secrets; destination/auth changes require replacement secret |
| Actual egress enforcement | 25 REST gateway tests include real controlled TCP and TLS trust/hostname checks, DNS-set changes, ambient proxy bypass, reserved addresses, framing/decompression bounds and scoped cancellation |
| MCP and execution fencing | Neutral catalog; official SDK 2.1.0/2025-11-25 only; 13 actual delegation/transport checks included in 54 live REST checks; current authority before dispatch and result release |
| Failure and lifecycle behavior | Actual HTTP 401/403/404/5xx, redirects, HTML, schema mismatch, gzip/byte/depth/timeout failures; counter-proven admitted cancellation/disable; restart and revision recovery |
| Existing functionality | Four original CI jobs preserved; native and database suites, browsers, independent source/dataset checks remain green |
| Honest test reporting | Zero skipped tests; four failure-only diagnostic steps skipped on the green run; repeated suites across jobs are not added together |

The final guard change removes ambiguity from the validator's own fixed pattern-recognition regex and adds a bounded adversarial regression. It increases backend unit tests from 24 to 25; the importer suite increases from 12 to 13. It does not expand the supported pattern subset.

Known limits: GET only; finite JSON OpenAPI subset; no URL imports/external references/OAuth; explicit operator destinations; local internal Compose has no public egress and is not a per-port firewall; one authority replica; no production certification. Cancellation fences delivery but cannot undo an already received remote request. See [security and residual risks](../architecture/REST_SECURITY.md) and [exact setup/verification commands](REST_LOCAL_SETUP.md). No merge or Phase 6 work is authorized.
