# Local setup and Phase 1 handoff

## What works in Phase 0

Only documentation and dependency verification tooling are provided. There is no backend/frontend application or Compose file to start. No API key, paid model, cloud account or cluster is required for Phase 0.

From repository root:

```powershell
node scripts/verify-foundation.mjs
git diff --check
node scripts/prepare-compatibility.mjs
```

The generator writes disposable, ignored files under `.verification/compatibility/`: a source-free POM and a frontend dependency-only package manifest. It does not create an application. Run a dependency graph probe with available Maven:

```powershell
mvn -B -ntp -f .verification/compatibility/backend/pom.xml org.apache.maven.plugins:maven-dependency-plugin:3.8.1:tree
```

Use Node 24.22.2/npm 11.19.1 for the frontend resolution probe, from `.verification/compatibility/frontend/`:

```powershell
npm install --package-lock-only --ignore-scripts --strict-peer-deps --engine-strict
```

This resolves peer/engine declarations without executing package lifecycle scripts or building the app. `npm ci`, actual TypeScript checks, native bundler loading and browser tests remain Phase 1 evidence. Standard Maven/npm caches require appropriate user access; do not disable TLS or store credentials in a repository to fix access problems. A portable verified Node distribution under ignored `.verification/tools/` can be used without changing global installations.

## Inspected workstation (2026-10-29)

| Component | Observed | Phase 1 implication |
| --- | --- | --- |
| Git | 2.47.0.windows.2; existing empty repo with authenticated private origin | Git credential access requires the full user context in this agent session; never print credential-helper output |
| Java/Javac | Oracle 21.0.4 | Correct major; update to selected supported JDK 21 patch before production-oriented development |
| Maven | 3.9.9 | Meets Boot minimum; Phase 1 adds pinned 3.9.16 wrapper |
| Node/npm | 20.17.0 / 10.8.2 | Too old for selected frontend; use pinned 24.22.2 / 11.19.1 |
| Docker / Compose | 28.3.2 / v2.39.1-desktop.1 | Installed; daemon 28.3.2 responded in full user context; restricted context denied config/pipe access |
| kubectl / Kustomize | v1.32.2 / v5.5.0 | Old client; do not use it as the planned future cluster baseline |
| Kubernetes config | Existing external cloud context | Presence only; connectivity, version, ownership and suitability not verified; no cluster requests or changes performed |
| Helm / Argo CD CLI | v3.17.0 / v2.14.14 | Installed versions only; no Argo server connected or verified |
| kind / GitHub CLI / Python | Unavailable on PATH | Not required for Phase 0; kind only selected if needed for later local Kubernetes |

No parent or repository AGENTS.md was found during inspection. The workspace contained only `.git`, no commits, tracked files or existing status documents. Recheck instructions at the start of subsequent phases.

## Phase 1 prerequisites and intended developer experience

P1-01 adds the Maven wrapper, frontend lockfile/engines and Node version file. Use maintained JDK 21 and the pinned Node/npm, Docker with Linux container support, and accessible Maven Central/npm registries. Database integration tests require real Docker access; inability to start containers must be reported, not replaced silently with an H2-only green build. Tool versions are pinned in [versions.json](../../config/versions.json); coordinate any changes with the compatibility assessment.

The intended commands below are **Phase 1 targets, not executable instructions for the current repository**:

| Planned command/location | Required result before documenting it as usable |
| --- | --- |
| `backend/mvnw.cmd verify` (Windows), `./backend/mvnw verify` (POSIX) | Unit/integration tests and production package succeed; actual working directory/paths confirmed |
| `npm ci`, `npm run lint`, `npm run typecheck`, `npm test -- --run`, `npm run build` in frontend | Locked clean install, strict compilation, meaningful UI tests and production assets |
| `docker compose -f infra/docker/compose.yaml config` and `up --build -d` | Real local platform DB/backend/frontend start; expose only localhost development ports |
| Documented Playwright smoke command | Browser login/status journey uses real backend/DB and reports actionable errors |

Compose initially runs only platform components; Phase 2 adds independent source systems. Use separate named volumes and roles per database, deterministic synthetic fixtures, health checks and explicit network boundaries. Stopping services must not remove persisted data; volume deletion is never a routine verification step.

## Configuration plan

Phase 1 supplies `.env.example` with names and documentation but no working credentials. Local secrets live in an ignored `.env` or environment; production secrets come from an external store/Kubernetes secret references. Values include platform DB connection, one-time admin bootstrap input, cookie/profile settings and (when introduced) a separately supplied credential-encryption key. Bootstrap inputs are cleared after provisioning and never logged.

AI defaults to disabled. Tests use an explicit deterministic model stub profile. Phase 6 adds an optional Spring AI Ollama profile, validates a tool-capable model on available hardware, and records an immutable model artifact/digest; no model download or provider contact on startup by default. A test double cannot be presented as live AI behavior. An alternative provider can be added behind ChatModel configuration when researched and authorized.

Vite's dev proxy will forward `/api` to Spring so browser auth/CSRF is same-origin. Production serves frontend and API under one origin. Frontend configuration contains no secrets. Check UI loading/empty/error states, not fabricated connection totals.

## Troubleshooting boundaries

If Node fails engine checks, use the pinned runtime rather than suppress engine requirements. If Docker or Git works in your terminal but is denied in the restricted agent context, use the approved full-user execution context; do not change ACLs or dump configuration files that might contain secrets. Maven version output emitted an access warning in the restricted shell; record it separately from actual build evidence. If a preconfigured cloud context exists, do not infer deployment authorization from it.
