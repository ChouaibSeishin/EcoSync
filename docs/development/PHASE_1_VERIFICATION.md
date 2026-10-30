# Phase 1 verification

Completed, 2026-10-30 UTC; chronological checkpoint notes retained. Phase 1 only; base `6a1749f40d394933c0e4b421048eab96818da436` verified against origin. Repository API reports public visibility; it was not changed.

## Checkpoint A

PASS: portable Node 24.22.2 archive SHA-256 matches Phase 0 evidence; Temurin JDK 21.0.13+9 downloaded from Adoptium and SHA-256 verified. Maven wrapper plugin 3.3.4 generated scripts for Maven 3.9.16; distribution checked against publisher SHA-512 and `distributionSha256Sum` pinned. Actual wrapper reported Maven 3.9.16 with Eclipse Adoptium Java 21.0.13. No global tool installation changed.

Actual frontend application package and lockfile generated separately from Phase 0 probes. `npm ci` passed with engine-strict and strict-peer-deps enabled: 226 packages added, 227 audited, zero reported vulnerabilities. No compile/UI/runtime success is claimed at this checkpoint.

Additional ArchUnit core 1.5.1 selected from official Maven metadata/user guide for executable module-boundary tests; JUnit Jupiter 6 remains Boot-managed. Official Spring Security 7.1.2 release/advisory information and current CSRF/session/Actuator/Testcontainers documentation rechecked. This is not an exhaustive backend vulnerability scan.

## Remaining checkpoints

Historical intermediate note: these checkpoints were then in progress; all were subsequently completed as documented below and in the final handoff addendum.

## Checkpoint B

Backend source and test compilation and production packaging passed. Architecture and database-failure unit tests passed. The real PostgreSQL migration test passed after correcting Testcontainers image compatibility metadata for the verified official digest: fresh migration, idempotent rerun, validation and cross-organization composite foreign-key rejection. Compose config validated; the new platform DB container is healthy with a new named volume. No existing volume was deleted.

The initial migration test failed before connection because the tag+digest was rejected by Testcontainers image-name compatibility checks. The same official digest is retained and explicitly marked PostgreSQL-compatible. The first security-context test then exposed PER_CLASS startup ordering; that separate Checkpoint C test is being repaired and rerun.

## Checkpoint C

PASS: complete Maven verify on Temurin 21.0.13/Maven 3.9.16: four unit tests and ten PostgreSQL-backed integration tests, zero failures/errors/skips. Tests cover generic auth errors, input limits, actual login throttling, CSRF, session rotation, logout invalidation, role/tenant denials, bootstrap idempotency, password hashing, safe errors and public health. Production jar packaged successfully.

Failures corrected before acceptance: PER_CLASS test lifecycle attempted dynamic DB properties before container start; explicit startup now precedes property access. The next run caught a login-path check using servletPath (empty in MockMvc) and an unrealistic session-rotation test request; the filter uses context-qualified request URI and the test now marks its existing client session valid. The final full suite passed. Real-browser rotation/logout checks remain part of D/E.

## Local application integration and final review

PASS: final frontend lint, strict type check, ten component tests and production build. Full Compose images built and setup/backend/frontend/database reached healthy state. PostgreSQL data/volume was preserved throughout. An initial duplicate shared-image export race was fixed by giving only the backend service a build definition; setup consumes its image. Windows rejected localhost 8080, so the configurable host API port is now 18080 (internal backend 8080); other services were not stopped.

PASS: Playwright real login, missing-CSRF rejection, protected access, cookie rotation/HttpOnly/SameSite, actual navigation and unavailable features, responsive layout and logout/stale-cookie rejection. An initial run started before frontend readiness; the runner now has a bounded readiness gate. A cold first UI request exceeded the original five-second assertion window; direct and browser status-only diagnostics showed successful 204/200 session round trips, and the suite uses a declared fifteen-second assertion deadline with zero retries.

PASS: controlled real-stack checks verified runtime DB restrictions, non-null migration checksum, stable user/workspace identity after backend restart, stale-session rejection, liveness 200/readiness 503 during DB outage, explicit DOWN API/UI state and restored healthy UI after DB restart. No volume deletion or credential reset.

The permission review found overly broad inherited DML on Flyway/bootstrap control metadata. V2 applies a versioned correction to the existing volume, retaining only identity/workspace SELECT access for the runtime; real privilege assertions now reject UPDATE/DELETE on controls and app_user. V1 was not rewritten. Native backend verify after V2 passed all fourteen tests and packaging.

Screenshot review found an offscreen mobile sidebar could remain keyboard-accessible and an in-transition capture obscured content. Closed mobile navigation now uses visibility hiding, explicit expanded/controls semantics, Escape/focus handling and stable screenshots; strengthened real-browser assertions passed, including Escape, expanded state, hidden closed navigation and renewed desktop/mobile screenshot review. A one-off source-edit helper had a quoting error before any write; checked replacements corrected it. No failed feature test was skipped.

## Actual local commands and evidence

Backend: backend/mvnw.cmd -B -ntp -Dmaven.repo.local=.verification/m2 -f backend/pom.xml verify, using the verified portable JAVA_HOME (local cache option only). Frontend: npm ci; npm run lint; npm run typecheck; npm test -- --run; npm run build. Browser: node scripts/run-e2e.mjs. Integration: docker compose -f infra/docker/compose.yaml config --quiet; build backend frontend; up -d --wait --wait-timeout 180; node scripts/verify-local-stack.mjs. Review: node scripts/verify-foundation.mjs; node scripts/review-repository.mjs; git diff --check and staged diff review.

[Toolchain/action checksum evidence](evidence/phase-1-toolchain.json) and [sanitized runtime results](evidence/phase-1-runtime-local.json) are retained. Screenshots and full logs stay locally ignored; password-bearing browser failure snapshots are never uploaded or committed.

## CI and Git

Historical intermediate state before push (subsequently resolved by both successful CI runs below). Workflow: .github/workflows/ci.yml, verified official checkout v6/setup-java v5/setup-node v6 commit SHAs, contents: read only, no publish/deployment secrets. It runs backend PostgreSQL tests, strict frontend checks, real Compose/browser and persistence/outage checks. Final run URL/result and commit list will be recorded after inspection.

## Final actual CI result and acceptance

**PASS**: [GitHub Actions run 41047054438](https://github.com/ChouaibSeishin/EcoSync/actions/runs/41047054438), code commit de83c20e94c90c42475133217fe6d06b5afc7c9b. Every job/step completed successfully, including Ubuntu Java/PostgreSQL tests, frontend installation/lint/type/test/build/audit, Compose startup, browser security smoke, persistence and DB outage/recovery. [Sanitized run/step evidence](evidence/phase-1-ci.json) is committed. No CI success was inferred from local results.

[All nine acceptance criteria](PHASE_1_ACCEPTANCE.md) are complete within Phase 1 scope. Local final results: four unit and ten PostgreSQL integration tests; ten frontend tests; one comprehensive Playwright smoke; separate real-stack persistence/fault/privilege assertions. Production backend jar and frontend assets built. No skips or live/paid-model dependency. Targeted source/known-secret/pin review and documentation/whitespace checks passed.

Incremental application commits: a54f3b2 (toolchain/wrapper/lock), 6c4e7ba (backend/DB foundation), 81cf755 (session/tenant security), c26e1b6 (runtime least privilege), 3094a97 (responsive UI/local integration), de83c20 (actual CI and verified setup). Final documentation/evidence commit follows; its exact hash is in Git history. Only codex/phase-1-foundation is pushed; Phase 0 stays unchanged. Repository visibility is public, verified through GitHub API, and was not modified.

Known limits: localhost-only Vite development container; in-memory single-instance sessions/throttle; no general identity/role administration UI or persistent audit browsing yet; optional host-mode startup documented but Compose is the exercised end-to-end acceptance path; no comprehensive backend/container vulnerability scan. No production deployment or later-phase integration/AI/research result is claimed. Phase 2 has not started and requires explicit authorization.

## Final Phase 1 handoff addendum (reconciled on Phase 2 branch)

Application implementation was fully verified at de83c20e94c90c42475133217fe6d06b5afc7c9b by [run 41047054438](https://github.com/ChouaibSeishin/EcoSync/actions/runs/41047054438). The later 603cfb993566f465d0c3328f175d71b27f6a4942 change clarified optional host-development documentation; it did not change application code. The final Phase 1 branch commit was itself fully verified by successful [run 41048264591](https://github.com/ChouaibSeishin/EcoSync/actions/runs/41048264591). Both actual run heads/statuses were rechecked through GitHub API. Phase 1 is complete, not CI-pending. Earlier failure/correction notes below are retained as chronological evidence; this addendum supersedes their intermediate progress wording. Phase 1 history/branch is not rewritten.
