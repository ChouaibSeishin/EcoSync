# Project status

Updated: 2026-10-30 UTC. Authorized scope: **Phase 1 only**.

## Current delivery state

Phase 0 is accepted and preserved at 6a1749f40d394933c0e4b421048eab96818da436. **Phase 1 is complete.** All nine work packages have passed their scoped acceptance criteria locally and in actual CI. See [the acceptance record](docs/development/PHASE_1_ACCEPTANCE.md).

Work branch: codex/phase-1-foundation, based on verified local/remote Phase 0. GitHub's repository API reports **public** visibility; this work did not change visibility. No unrelated starting user changes were present.

## Implemented and locally verified

- Temurin Java 21.0.13+9, checksum-pinned Maven Wrapper 3.9.16, verified Node 24.22.2/npm 11.19.1 and a real application lockfile/strict clean install.
- Spring MVC platform with sanitized REST errors, correlation IDs, status-only public liveness/readiness and authenticated live DB status.
- PostgreSQL/Flyway organization/user/membership/role schema, tenant composite constraints and one-time bootstrap marker. Separate non-superuser migrator and read-only runtime credentials; a versioned permission correction protects control metadata on the existing volume.
- Actual session authentication, adaptive password hashing, login throttle, CSRF, session rotation/logout, role and tenant enforcement; controlled random initial administrator credentials.
- Responsive React dashboard, navigation, login/session state, empty integrations, error/loading/retry states, system status and explicitly unavailable AI. No fake operational metrics or AI answers.
- Digest-pinned localhost Compose stack with one-shot setup; metadata survives backend restart. Real database outage behavior is visible in health/API/UI and recovers after restart.
- Four backend unit tests, ten PostgreSQL-backed integration tests, ten frontend component tests and a real Playwright smoke journey passed. Source/link/pin/whitespace checks and screenshot review passed after correcting mobile navigation accessibility.

Detailed commands, results, corrected failures and limitations: [Phase 1 verification](docs/development/PHASE_1_VERIFICATION.md). Runtime evidence and toolchain hashes are retained there. Local app: http://127.0.0.1:5173; API host port 18080, DB host port 55432. Initial credentials are only in the ignored local configuration; none are documented or committed.

## Limits and next boundary

The frontend container is a development Vite server. Sessions and bounded login throttling are single-instance/in-memory; a backend restart intentionally invalidates sessions. General identity/role administration, persistent audit browsing, multi-instance abuse controls and production deployment remain later work. No comprehensive backend/container vulnerability assessment or production-readiness claim is made.

Only platform metadata PostgreSQL is included. There are no demo source systems, MCP/gateway/native server integrations, external customer DB/API connectors, model calls, chat orchestration, mapping UI, benchmark results, Kubernetes/Argo resources or GHCR publishing. The accepted MCP compatibility gap remains a Phase 3 gate.

Actual CI passed: [run 41047054438](https://github.com/ChouaibSeishin/EcoSync/actions/runs/41047054438), code commit de83c20e94c90c42475133217fe6d06b5afc7c9b. Final documentation/evidence commits do not change application code. Phase 2 requires explicit authorization; its next task is P2-01, independent synthetic source systems and ground truth.
