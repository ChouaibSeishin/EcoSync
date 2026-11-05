# Phase 5 verification record

Status: implementation and verification in progress, 2026-11-05. No final remote acceptance claim yet.

Exact base: `ec310f8c25b4778396bdc5f698f3ef7253967401`, `codex/phase-4-managed-db-connectors`, final [run 41814237164](https://github.com/ChouaibSeishin/EcoSync/actions/runs/41814237164). Historical application head `18aafb7d41011171e64da25e14cc1bdf363fc533` and run 41812225715 are preserved. New branch: `codex/phase-5-dynamic-rest-connectors`. Previous branches/default/public visibility must be verified unchanged at final handoff.

## Distinct evidence classes

- Backend unit tests: original ten plus ten importer/typed-schema tests; actual Supplier document, both supported OpenAPI eras, malformed/duplicate/unsupported/refs/bounds/fingerprints.
- `RestRegistryIT`: twelve real PostgreSQL/Flyway/AES/tenant/immutable revision/race tests. The gateway peer in this suite is explicitly a controlled test double; source behavior is proved elsewhere.
- REST gateway unit/controlled socket tests: destination sets/rebinding, parsing/framing/decompression, cancellation/concurrency, actual local TCP/redirect trap, bounded DNS and TLS identity/trust. No public internet test dependency.
- Independent Partner API: three Node tests against real loopback HTTP; no EcoSync backend or database needed.
- React component tests: prior 22 plus five REST tests. Local single-worker runs avoid host contention without skipping tests or changing assertions. Default parallel local runs exposed the earlier native cancellation test's one-second async timing sensitivity; the single-worker suite passed unchanged. Subsequent loaded-host runs reproduced the same finite DOM-observation delay; Testing Library observation now has a five-second bound. Application/security deadlines and every prior behavior assertion are unchanged.
- `scripts/verify-rest-stack.mjs`: actual independent Supplier/Partner containers, real gateway SDK MCP, signed adversarial contexts, source request counters, status/redirect/schema/limits/recovery/history/network tests. Counter-based tests must run serially with browser journeys, because both intentionally contact the same independent fixture.
- `scripts/run-rest-e2e.mjs`: two real browser journeys, both APIs registered by JSON contract/configuration, explicit review/approval/test/activation/discovery/invocation/history/revoke. Desktop/mobile screenshots remain local under `.verification/phase5`.
- Existing four CI jobs remain complete and unchanged; the fifth REST job must run these checks against the final commit.

## Retained corrections

Initial importer test incorrectly counted only four business operations; inspecting the complete Supplier contract showed seven including health/contract endpoints. The test now checks all seven supported operations; no fixture was altered. Gateway destination tests caught missing explicit IPv6 documentation range rejection; the policy was fixed, then the full gateway suite passed. The first registry test run exposed a test-only missing runtime role in its disposable PostgreSQL; provisioning it before Flyway enabled actual immutable privilege assertions. Frontend old cancellation tests were slow under parallel Windows load; the same assertions pass serially.

The first local REST source network creation was external to Compose; marking that operator-created internal network external fixed the ownership-label mismatch without deleting data. The official TypeScript probe required the same bounded close and hidden-tool error handling as Phase 4; those test-harness corrections do not replace execution authorization. Raw Host-header testing must use Node HTTP, since fetch may override a supplied Host.

Final counts, final SHA, run URL, conclusions, skipped-test counts and sanitized evidence will be added only after observing them. Failure-only diagnostic steps are reported separately from skipped tests.

First remote five-job run [42008379151](https://github.com/ChouaibSeishin/EcoSync/actions/runs/42008379151), head `a561de0f383a7059c2a06b13e9f631a79cead94b`: demo passed; four Java/UI jobs reached the frontend and failed TypeScript on the new test selectors. Testing Library role queries use exact string names by default and do not accept Playwright's `exact` option. The unsupported option was removed from new component tests; no application authorization or earlier assertion was relaxed. Final acceptance requires a new successful run.
