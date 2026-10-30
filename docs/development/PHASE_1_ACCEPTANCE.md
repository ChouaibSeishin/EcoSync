# Phase 1 acceptance record

Verified 2026-10-30. Application code tested by successful GitHub Actions run [41047054438](https://github.com/ChouaibSeishin/EcoSync/actions/runs/41047054438) at commit de83c20e94c90c42475133217fe6d06b5afc7c9b. Final handoff commits update documentation/evidence only. Phase 0 remains at 6a1749f40d394933c0e4b421048eab96818da436; no merge or force push.

| Package | Verified acceptance |
| --- | --- |
| P1-01 | Maintained portable JDK and reused Node verified against publisher checksums; actual wrapper 3.9.16 with pinned distribution checksum; exact real frontend lockfile and strict clean npm ci |
| P1-02 | Actual Boot 4 MVC build/package, safe REST errors/correlation IDs, process liveness distinct from DB-dependent readiness, package boundary tests |
| P1-03 | Actual PostgreSQL/Flyway/JPA schema; two immutable versioned migrations, idempotent rerun and validation; cross-org composite FK rejection; separated migrator/read-only runtime and digest-pinned image; persistent volume preserved |
| P1-04 | Actual session login, hashing, rotation/logout, CSRF, bounded throttle, protected/role/tenant checks and one-time bootstrap; negative PostgreSQL-backed tests and browser cookie assertions |
| P1-05 | Strict React application, accessible desktop/mobile navigation/dialogs, real login/session/status/overview APIs, meaningful empty/unavailable/error/loading states; ten component tests, lint/types and production build |
| P1-06 | Actual Compose backend/frontend/metadata DB, same-origin proxy cookies/CSRF, health, localhost-only ports, startup without AI credentials, state across restart and visible DB failure/recovery |
| P1-07 | Four backend unit tests, ten PostgreSQL-backed integration tests, ten frontend component tests, full real browser smoke and explicit restart/outage/runtime privilege verification; no blanket skips |
| P1-08 | Actual successful GitHub Actions run, SHA-pinned official actions, contents read, no publish/deploy credentials; native Linux builds/tests/Compose/browser/runtime checks inspected |
| P1-09 | README/status/roadmap/backlog/setup/architecture/verification updated; official-source and runtime/CI evidence retained; staged secret/pin/whitespace review passed; incremental branch commits/push |

No application feature from Phase 2 or later is implemented. Runtime sessions/throttle are single-instance and in memory; the frontend container is a localhost development server, not production hosting. General identity administration, persistent audit views and deployment hardening remain later work. Backend/container vulnerability assessment is not claimed as exhaustive; npm audit returned zero for the inspected frontend graph.
