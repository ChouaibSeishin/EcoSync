# Phase 1 verification

In progress, 2026-10-30 UTC. Phase 1 only; base `6a1749f40d394933c0e4b421048eab96818da436` verified against origin. Repository API reports public visibility; it was not changed.

## Checkpoint A

PASS: portable Node 24.22.2 archive SHA-256 matches Phase 0 evidence; Temurin JDK 21.0.13+9 downloaded from Adoptium and SHA-256 verified. Maven wrapper plugin 3.3.4 generated scripts for Maven 3.9.16; distribution checked against publisher SHA-512 and `distributionSha256Sum` pinned. Actual wrapper reported Maven 3.9.16 with Eclipse Adoptium Java 21.0.13. No global tool installation changed.

Actual frontend application package and lockfile generated separately from Phase 0 probes. `npm ci` passed with engine-strict and strict-peer-deps enabled: 226 packages added, 227 audited, zero reported vulnerabilities. No compile/UI/runtime success is claimed at this checkpoint.

Additional ArchUnit core 1.5.1 selected from official Maven metadata/user guide for executable module-boundary tests; JUnit Jupiter 6 remains Boot-managed. Official Spring Security 7.1.2 release/advisory information and current CSRF/session/Actuator/Testcontainers documentation rechecked. This is not an exhaustive backend vulnerability scan.

## Remaining checkpoints

Backend/PostgreSQL, authentication, frontend/Compose, tests/CI and final documentation remain in progress. Failures and actual final outcomes will be recorded here.

## Checkpoint B

Backend source and test compilation and production packaging passed. Architecture and database-failure unit tests passed. The real PostgreSQL migration test passed after correcting Testcontainers image compatibility metadata for the verified official digest: fresh migration, idempotent rerun, validation and cross-organization composite foreign-key rejection. Compose config validated; the new platform DB container is healthy with a new named volume. No existing volume was deleted.

The initial migration test failed before connection because the tag+digest was rejected by Testcontainers image-name compatibility checks. The same official digest is retained and explicitly marked PostgreSQL-compatible. The first security-context test then exposed PER_CLASS startup ordering; that separate Checkpoint C test is being repaired and rerun.
