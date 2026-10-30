# Local setup

Platform application setup; updated 2026-10-30 for the Phase 2 branch. The repository's default branch still contains Phase 0. Use `codex/phase-2-demo-ecosystem` for the current checkout; the accepted Phase 1 branch remains preserved. Independent source setup is in [demo systems setup](DEMO_SYSTEMS_SETUP.md). The repository API currently reports public visibility; no visibility changes were made.

## Prerequisites

| Tool | Required baseline |
| --- | --- |
| Java | Temurin JDK 21.0.13+9, Java release 21 |
| Maven | Wrapper downloads/verifies 3.9.16; no global Maven installation required |
| Node / npm | 24.22.2 / 11.19.1, exact engines enforced by frontend/.npmrc |
| Docker | Linux containers and Docker Compose v2; daemon access required by integration tests |
| Browser tests | Pinned Playwright package; install its Chromium browser once |

This workstation originally had Oracle 21.0.4/Node 20.17. Portable verified tools remain under ignored `.verification/tools/`. To reuse them in this workstation's PowerShell session:

```powershell
$taskJdk = Join-Path $PWD '.verification/tools/jdk-21.0.13+9'
$taskNode = Join-Path $PWD '.verification/tools/node-v24.22.2-win-x64'
$env:JAVA_HOME = $taskJdk
$env:PATH = "$taskJdk/bin;$taskNode;" + $env:PATH
java -version
node --version
npm --version
```

A fresh clone does not include portable tool downloads; install the pinned tools from their official distributions or supply equivalent verified local paths. No global uninstall/upgrade was performed by the agent. In this managed agent environment, Git/Docker/Node entry-point access needed full user context; do not weaken ACLs or TLS to work around a restricted-context failure.

## Build and test

Windows PowerShell, from repository root:

```powershell
Push-Location backend
./mvnw.cmd -B -ntp verify
Pop-Location
Push-Location frontend
npm ci
npm run lint
npm run typecheck
npm test -- --run
npm run build
npx --no-install playwright install chromium
Pop-Location
node scripts/verify-foundation.mjs
```

POSIX:

```sh
(cd backend && ./mvnw -B -ntp verify)
(cd frontend && npm ci && npm run lint && npm run typecheck && npm test -- --run && npm run build)
(cd frontend && npx --no-install playwright install --with-deps chromium)
node scripts/verify-foundation.mjs
```

`verify` compiles/packages the backend, runs unit tests and Failsafe `*IT` tests against actual PostgreSQL Testcontainers. There is no H2 alternative or blanket Docker skip. A missing Docker daemon fails the relevant test. Spring AI is a managed BOM only; no model is contacted. Frontend dependencies and the actual application lockfile are separate from historical Phase 0 probes.

## Initialize and start

```powershell
node scripts/init-local.mjs
docker compose -f infra/docker/compose.yaml config --quiet
docker compose -f infra/docker/compose.yaml up --build -d --wait --wait-timeout 180
docker compose -f infra/docker/compose.yaml ps
```

Build the backend jar first as above; its Dockerfile packages that already verified artifact. Compose builds one shared backend image used by setup and runtime, plus the frontend development image. Base images are pinned to verified linux/amd64 manifests in [container-images.json](../../config/container-images.json). Docker Desktop on another CPU needs matching platform emulation or deliberately reverified architecture-specific pins.

The generator preserves any existing `infra/docker/.env`; it does not overwrite or print credentials. Read the initial username/password from that file locally and sign in at [127.0.0.1:5173](http://127.0.0.1:5173). Database credentials are unique random values. Port defaults: frontend 5173, backend 18080 (container 8080), DB 55432; all host bindings are localhost. Adjust ports in the ignored file if occupied; never stop unrelated services to free them.

Database initialization creates an owner for local initialization, a non-superuser migrator owning schema `app`, and a runtime role with SELECT on identity/workspace tables, no access to migration/bootstrap control tables, and no schema CREATE/superuser/createdb privileges. The one-shot setup process runs Flyway and initial administrator provisioning. The backend runs as a non-root container user with runtime DB credentials, migrations off, and no bootstrap password. Schema validation is enabled; Hibernate auto-update is not used.

The bootstrap marker and advisory lock prevent concurrent or repeated administrator reset. Additional identity/role administration is not exposed in this release. A later start reuses the persisted identity; changing bootstrap values cannot replace it. Local `.env` and stopped setup-container metadata retain local configuration, so protect workstation access. After initial provisioning, retain the administrator password securely and remove the stopped setup container if desired; the running backend has none of that bootstrap input. Database password changes require explicit role rotation; editing the environment alone does not change passwords in an existing volume.

## Browser, persistence and outage checks

```powershell
node scripts/run-e2e.mjs
node scripts/verify-local-stack.mjs
```

The smoke runner reads generated local credentials without printing them; operators/CI may supply `E2E_USERNAME`, `E2E_PASSWORD` and `E2E_BASE_URL`. The runtime verifier is intentionally for this specific local Compose project: it checks real browser layout, runtime-role privileges, migration checksums, metadata persistence across backend restart, invalidation of old sessions, DB outage liveness/readiness/API/UI behavior and recovery. It restores the database in a finally block and never removes volumes. Local screenshots/results stay ignored under `.verification/`; test screenshots/traces must not be used to publish credentials.

## Run backend/frontend on the host (optional)

First initialize the database and run setup via Compose. Stop only this project's backend/frontend containers before using the same host ports. Supply backend environment values from your local configuration:

| Variable | Host development value |
| --- | --- |
| DB_URL | jdbc:postgresql://127.0.0.1:55432/ecosync (adjust selected port) |
| DB_USERNAME | ecosync_runtime |
| DB_PASSWORD | Your ignored local DB_RUNTIME_PASSWORD value |
| SERVER_PORT | 18080 (match the selected host API port) |
| COOKIE_SECURE | false for localhost HTTP only |
| MIGRATIONS_ENABLED | false after the one-shot setup |

Then run `./mvnw spring-boot:run` from backend (Windows: `./mvnw.cmd spring-boot:run`) and `npx --no-install vite --host 127.0.0.1` from frontend. The Vite proxy defaults to host 127.0.0.1:18080; override `API_PROXY_TARGET` if needed. Primary end-to-end acceptance uses the actual Compose stack; this optional host invocation is a development convenience, not separate verified deployment evidence.

## Health, sessions and stopping

Public status-only probes: `/actuator/health/liveness` checks process state; `/actuator/health/readiness` includes DB state and returns 503 during DB loss. Other Actuator endpoints are denied. Authenticated `/api/system/status` reports live DB availability without connection details. The frontend exposes loading/error/retry states, empty integration management and unavailable AI honestly.

Sessions use HttpOnly/SameSite Strict cookies, rotate at login and invalidate at logout. Secure cookies are the default; Compose has a clearly local HTTP exception. Authentication state is not in localStorage. Login throttling is bounded and single-instance; it resets after restart. Data persists across restart; in-memory sessions intentionally do not.

```powershell
docker compose -f infra/docker/compose.yaml stop
# To resume without resetting data:
docker compose -f infra/docker/compose.yaml up -d --wait --wait-timeout 180
```

Do not use volume deletion to fix startup or change credentials. No Kubernetes, Argo CD, GHCR publishing, paid model, source-system connector or cloud credentials are needed here.
