# Phase 0 verification report

Date: 2026-10-29 UTC. Scope: foundation documentation and dependency/design verification only. No application results are inferred from dependency resolution.

## Inspection and environment checks

Ran repository/ancestor instruction discovery, `git status --short --branch`, `git remote -v`, `git log -5 --oneline`, tool discovery and version commands. Workspace was empty except `.git`; `git log` failed because the initial branch had no commits. Authenticated `git -c credential.interactive=false ls-remote --symref origin` succeeded with no refs, confirming the remote was empty. Origin was checked before any planned push.

Ran `java -version`, `javac -version`, `mvn -version`, `node --version`, `npm --version`, `docker --version`, `docker compose version`, `docker info --format '{{.ServerVersion}}'`, `kubectl version --client --output=yaml`, `kubectl config current-context`, `helm version --short`, and `argocd version --client --short`. Findings are recorded in [local setup](LOCAL_SETUP.md). Maven version output included a restricted-shell access warning. Docker daemon/config and Kubernetes config checks initially failed with access denied in the restricted context; read-only full-user-context retries returned Docker server 28.3.2 and an existing cloud context. No cluster connectivity or resource query was performed, and no configuration/secrets were dumped.

## Official documentation and metadata

Inspected official Spring, MCP, Node/Vite, GitHub Actions, Kubernetes and Argo documentation, exact release source/POMs and publisher npm/Docker manifests. Recorded the supported Spring pair, actual managed versions, frontend engines/peers and the current-spec MCP gap in [compatibility](../architecture/COMPATIBILITY.md). Unavailable old documentation paths were replaced by current official docs/tagged sources, with failures disclosed there.

## Foundation and dependency checks

| Check actually executed | Result / limits |
| --- | --- |
| `node scripts/verify-foundation.mjs` | PASS in full user context: 18 required deliverables, 17 Markdown files, 38 local file links and 30 exact dependency declarations. Initial restricted-context invocation failed with EPERM resolving the workspace parent; rerun passed. |
| `node --preserve-symlinks --preserve-symlinks-main --check scripts/verify-foundation.mjs` and same command for `prepare-compatibility.mjs` | PASS JavaScript syntax checks; no application code involved |
| `node scripts/prepare-compatibility.mjs` | PASS: generates source-free backend/frontend probes only under ignored `.verification/compatibility/` |
| `mvn -B -ntp -f .verification/compatibility/backend/pom.xml '-Dmaven.repo.local=.verification/m2' org.apache.maven.plugins:maven-dependency-plugin:3.8.1:tree` | PASS, Maven BUILD SUCCESS, 57.235s; available Maven 3.9.9/Oracle Java 21.0.4. Resolved Boot 4.1.2/AI 2.0.2 graph; selected managed versions asserted below. Not compilation/startup or Maven wrapper verification. |
| Official Node `node-v24.22.2-win-x64.zip` download and SHA-256 comparison against that release's SHASUMS256.txt | PASS; archive extracted only under ignored `.verification/tools/`; no global installation modified. Archive SHA-256 in evidence JSON. Portable runtime reported v24.22.2 and npm 11.19.1. |
| `npm install --package-lock-only --ignore-scripts --strict-peer-deps --engine-strict` with workspace cache, in frontend probe, using portable Node/npm | PASS; npm reports 227 audited packages, zero vulnerabilities, 58s. Lock snapshot has 252 package entries including root and platform-optional dependencies. No lifecycle scripts, native bundler execution or browser test. npm advertised major 12; retained Node's bundled pinned npm 11.19.1. |
| Explicit assertions against Maven tree and npm lock | PASS: SDK 2.1.0, Jupiter 6.0.3, Testcontainers 2.0.5, Flyway 12.4.0, PostgreSQL JDBC 42.7.14, MySQL JDBC 9.7.0 and Security 7.1.2; all 19 frontend direct versions match manifest and resolved entries. A one-off exporter first failed from a syntax typo; corrected rerun passed and saved evidence. Final SHA-256 recomputation also matched both retained snapshots. |
| `git diff --check`, `git diff --cached --check`, staged path review and targeted secret-pattern scan | PASS: no whitespace errors or matching credential/private-key patterns; not an exhaustive secret/security scan. Git ignore checks confirmed `.env`, verification downloads/caches and secret files are ignored; `.env.example` can be tracked. |

The exact executed npm invocation used the portable `node.exe` with its bundled `node_modules/npm/bin/npm-cli.js`, adding that portable directory to PATH only for that shell, plus `--cache` pointing to `.verification/npm-cache`. Full transfer/debug logs remain locally under ignored `.verification/`. No credentials or personal fixture data are in tracked evidence.

Retained evidence:

- [Machine-readable outcomes and SHA-256 hashes](evidence/phase-0-compatibility.json)
- [Sanitized resolved Maven tree](evidence/maven-dependency-tree.txt)
- [Frontend probe lock snapshot](evidence/frontend-probe.package-lock.json)

The npm audit is a point-in-time report for that probe graph. No Maven vulnerability scan, container scan, external-link crawler or Mermaid rendering check was run. Official pages were inspected as recorded in the compatibility ledger; diagrams were reviewed as text. Future application install/compile/runtime checks must still run.

## Checks intentionally not run

Backend/frontend compile, unit/integration/end-to-end suites, Compose service startup, real MCP discovery/invocation, live model calls, security enforcement tests, CI runs, image builds/scans, Kubernetes validation and deployment: **not run**, because the corresponding application/infrastructure artifacts are not implemented in Phase 0. Security design is not security test evidence. No benchmark data/results have been generated.

## Acceptance scope

Phase 0's four design criteria are complete: decisions documented, repository structure defined, exact compatible dependency declarations/graph snapshots recorded, and Phase 1 work ready with prerequisites and acceptance tests. A runnable application and its own locks, actual connector security, protocol conformance, CI success and deployment remain future phase acceptance gates. No paid credentials or external infrastructure changes are needed to proceed with the Phase 1 local skeleton after tool prerequisites are satisfied.

## Git and Phase 1 handoff

Work is isolated on `codex/phase-0-foundation`. Initial commit `9c36648` contains the design/roadmap/pins and verification tooling; the subsequent evidence commit records final outcomes and status. Before push, verify the exact existing origin and use a normal branch push with no force. Final remote SHA comparison and clean working-tree check are reported in the phase handoff rather than invented in this document before those operations run.

Phase 1 starts at P1-01 in [BACKLOG.md](../../BACKLOG.md): select the pinned maintained local toolchain, add wrappers/real frontend lockfile, then implement the backend and PostgreSQL/Flyway baseline. No Phase 1 application artifacts were introduced by this work.
