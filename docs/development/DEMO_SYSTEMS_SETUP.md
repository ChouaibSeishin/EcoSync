# Independent demo systems setup

Use Node 24.22.2/npm 11.19.1 and Docker Compose v2 from the repository root. These commands do not require the platform, Java, an LLM or paid services.

```sh
node scripts/init-demo.mjs
docker compose --env-file infra/docker/demo.env -f infra/docker/demo-systems.compose.yaml config --quiet
docker compose --env-file infra/docker/demo.env -f infra/docker/demo-systems.compose.yaml build supplier
docker compose --env-file infra/docker/demo.env -f infra/docker/demo-systems.compose.yaml up -d --wait --wait-timeout 240
```

The init script creates seven distinct random credentials in ignored infra/docker/demo.env and preserves existing configuration. Keep it local. Compose config --quiet avoids printing resolved credentials. Do not put this file, database dumps, inspect output containing environment variables or request headers into Git/CI artifacts.

Sales PostgreSQL: localhost:55433, database sales, schema sales, account sales_reader. Inventory MySQL: localhost:53307, database inventory, account inventory_reader. Supplier HTTP: [localhost:18081](http://localhost:18081/health/ready); obtain the local bearer token from the ignored configuration through your local secret tooling. Do not paste it into documentation. Port overrides SALES_PORT, INVENTORY_PORT and SUPPLIER_PORT are supported. No platform database credentials work here.

Each service can start independently by appending sales, inventory or supplier to the up command. There are no cross-service depends_on declarations. Supplier has no database dependency. Container names and volumes are scoped by Compose project (default ecosync-demo-v1); use -p for a separate environment.

```sh
node scripts/demo/generate-sales.mjs --check
node scripts/demo/generate-inventory.mjs --check
node scripts/demo/generate-suppliers.mjs --check
node scripts/demo/verify-sales.mjs
node scripts/demo/verify-inventory.mjs
```

Additional full ecosystem and dataset checks are documented in the [verification record](PHASE_2_VERIFICATION.md). Initial schema and seed migrations run only on an empty volume. V001 owns the schema; V002 contains the generated deterministic seed. Each database records SHA-256 migration checksums in schema_history; readers cannot query this control table. Existing volumes are never silently reseeded. MySQL DDL is not transactional; a failed first initialization must be diagnosed rather than marked ready. Use a new project/volume for clean reproduction; preserve the failed volume for inspection.

Stop only demo services (data persists):

```sh
docker compose --env-file infra/docker/demo.env -f infra/docker/demo-systems.compose.yaml stop
```

A later up restarts them. Never use down -v on data you intend to preserve. Verification creates separately named fresh projects and retains their volumes. Dispose of those only after inspecting the exact project/volume names yourself.

The dedicated bridge network has no attachment to ecosync-phase1_default. All published ports bind 127.0.0.1. Future MCP services may explicitly join the demo project's sources network through an override using reader credentials; no such connection exists in Phase 2. Loopback development HTTP/SQL is not a production transport/security deployment. No auto-upgrade or migration of a persistent fixture version is provided.

Supplier build/tests (independent of Docker databases):

```sh
cd demo-systems/supplier-api
npm ci
npm run verify
npm audit --audit-level=high
```
