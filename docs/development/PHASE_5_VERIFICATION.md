# Phase 5 verification record

Status: implementation verified, 2026-11-05. Application acceptance and final branch-head handoff are distinguished explicitly below.

Exact base: `ec310f8c25b4778396bdc5f698f3ef7253967401`, `codex/phase-4-managed-db-connectors`, final [run 41814237164](https://github.com/ChouaibSeishin/EcoSync/actions/runs/41814237164). Historical application head `18aafb7d41011171e64da25e14cc1bdf363fc533` and run 41812225715 are preserved. New branch: `codex/phase-5-dynamic-rest-connectors`. Previous branches/default/public visibility must be verified unchanged at final handoff.

## Distinct evidence classes

- Backend unit tests at application acceptance: original ten plus twelve importer/typed-schema and two strict envelope tests, 24 total; actual Supplier document, both supported OpenAPI eras, malformed/duplicate/unsupported/refs/bounds/fingerprints. The final fixed-regex guard adds one importer test, making 25 total/13 importer tests; that later commit has a separate final-head CI gate.
- `RestRegistryIT`: sixteen real PostgreSQL/Flyway/AES/tenant/immutable revision/race tests, included in 54 total backend PostgreSQL integration tests. The gateway peer in this suite is explicitly a controlled test double; source behavior is proved elsewhere.
- REST gateway unit/controlled socket tests: destination sets/rebinding, parsing/framing/decompression, cancellation/concurrency, actual local TCP/redirect trap, bounded DNS and TLS identity/trust. No public internet test dependency.
- Independent Partner API: three Node tests against real loopback HTTP; no EcoSync backend or database needed.
- React component tests: prior 22 plus five REST tests. Local single-worker runs avoid host contention without skipping tests or changing assertions. Default parallel local runs exposed the earlier native cancellation test's one-second async timing sensitivity; the single-worker suite passed unchanged. Subsequent loaded-host runs reproduced the same finite DOM-observation delay; Testing Library observation now has a five-second bound. Application/security deadlines and every prior behavior assertion are unchanged.
- `scripts/verify-rest-stack.mjs`: actual independent Supplier/Partner containers, real gateway SDK MCP, signed adversarial contexts, source request counters, status/redirect/schema/limits/recovery/history/network tests. Counter-based tests must run serially with browser journeys, because both intentionally contact the same independent fixture.
- `scripts/run-rest-e2e.mjs`: two real browser journeys, both APIs registered by JSON contract/configuration, explicit review/approval/test/activation/discovery/invocation/history/revoke. Desktop/mobile screenshots remain local under `.verification/phase5`.
- Existing four CI jobs remain complete and unchanged; the fifth REST job runs these checks against the checked-out commit.

## Retained corrections

Initial importer test incorrectly counted only four business operations; inspecting the complete Supplier contract showed seven including health/contract endpoints. The test now checks all seven supported operations; no fixture was altered. Gateway destination tests caught missing explicit IPv6 documentation range rejection; the policy was fixed, then the full gateway suite passed. The first registry test run exposed a test-only missing runtime role in its disposable PostgreSQL; provisioning it before Flyway enabled actual immutable privilege assertions. Frontend old cancellation tests were slow under parallel Windows load; the same assertions pass serially.

The first local REST source network creation was external to Compose; marking that operator-created internal network external fixed the ownership-label mismatch without deleting data. The official TypeScript probe required the same bounded close and hidden-tool error handling as Phase 4; those test-harness corrections do not replace execution authorization. Raw Host-header testing must use Node HTTP, since fetch may override a supplied Host.

## Observed application acceptance

Exact head `4c5c7404fedfc1abe1e1a1d99b40e02441e437b0`, [run 42018755865](https://github.com/ChouaibSeishin/EcoSync/actions/runs/42018755865): completed/success, all five jobs successful. Actual job metadata and full logs were inspected; [sanitized evidence](evidence/phase5-application-ci.json) retains conclusions and meaningful counts. These counts are per suite, not a sum of duplicated executions across jobs.

| Job | Actual result and meaningful counts |
|---|---|
| verify | Success: 24 backend unit, 54 real PostgreSQL integration, 27 frontend, one foundation browser; persistence/readiness/outage checks |
| Independent demo sources and dataset | Success: 14 Supplier tests, 36 independent expected cases, actual reader/constraint denials, two fresh independently credentialed initializations with equal business fingerprints |
| Native MCP interoperability and governance | Success: 25 client tests, actual Java/TypeScript dual-era matrix, 30 live stack checks, two browsers; frontend 27 |
| Managed database gateway and connector governance | Success: 17 gateway unit and ten real PostgreSQL/MySQL integration, 11 actual wire proof, 13 direct delegation/transport checks, 58 live stack checks, two database plus two native browsers |
| Dynamic REST connectors and isolated MCP egress | Success: 24 backend unit/54 PostgreSQL integration, 25 REST gateway, three Partner HTTP tests, 27 frontend, 54 actual REST stack checks including 13 direct SDK checks; two REST plus two database plus two native browsers |

Zero skipped tests. Four skipped steps are deliberately failure-only diagnostics (demo/native/database/REST); none contains acceptance tests. Lint, TypeScript, production builds, dependency/pin/secret/diff and preserved-job reviews passed. Existing dependency pins and action SHAs are retained. Only the exactly pinned build-helper-maven-plugin 3.6.2 is new; its official release, requirements, add-source documentation and advisory page were reviewed before selection. It shares the pure contract source with the gateway without introducing a runtime dependency.

Local evidence independently records 54 live REST checks and two real browser journeys in [sanitized live evidence](evidence/phase5-rest-stack.json); desktop/mobile screenshots were visually reviewed locally. Actual TLS tests cover trusted hostname success, identity mismatch and untrusted trust chain. The HTTP fixture proves an admitted request before cancel/disable fencing. The TCP timeout fixture uses an explicitly reserved unused address inside this project's private source subnet, not host discovery or public internet scanning.

## Final branch-head gate

The documentation/evidence commit also removes a redundant alternative in the subset validator's constant regex and adds a one-second adversarial recognition regression (13 importer tests passed locally). Backend unit count becomes 25. The application acceptance run above is not claimed as proof for this later commit. After committing/pushing, inspect its own exact remote SHA and five-job workflow, save `.verification/phase5/final-ci.json` and report its URL, conclusions and actual counts in the final handoff. Do not make another tracked edit after that successful final-head run. Preserve the original Supplier whitespace edit unstaged, restore normal fixture startup and retain existing movies access/volumes.

First remote five-job run [42008379151](https://github.com/ChouaibSeishin/EcoSync/actions/runs/42008379151), head `a561de0f383a7059c2a06b13e9f631a79cead94b`: demo passed; four Java/UI jobs reached the frontend and failed TypeScript on the new test selectors. Testing Library role queries use exact string names by default and do not accept Playwright's `exact` option. The unsupported option was removed from new component tests; no application authorization or earlier assertion was relaxed. Subsequent actual five-job runs 42009801652 (92cea40), 42014280249 (91727fe) and 42018755865 (4c5c740) all succeeded; each proves only its own head.
