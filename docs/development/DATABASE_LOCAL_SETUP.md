# Database connector local setup

Use the Phase 4 feature branch `codex/phase-4-managed-db-connectors`. Keep Java 21.0.13+9, Node 24.22.2/npm 11.19.1 and the existing Docker Linux engine. [Native MCP setup](MCP_LOCAL_SETUP.md) remains valid independently. These commands preserve existing volumes, credentials, bootstrap identity and encryption keys. Do not delete volumes or reset Docker to initialize Phase 4.

## Build and start

Build the Java artifacts with the existing wrapper:

```powershell
./backend/mvnw.cmd -B -ntp -f backend/pom.xml verify
./backend/mvnw.cmd -B -ntp -f managed-gateway/pom.xml verify
./backend/mvnw.cmd -B -ntp -f mcp-servers/sales/pom.xml package
```

Clean-install/build the pinned client, Inventory and frontend packages using their existing scripts. For an already built local Phase 3 stack, retain those images and rebuild the changed platform/frontend/gateway. Initialize configuration only through the idempotent helpers:

```powershell
node scripts/init-local.mjs
node scripts/init-demo.mjs
node scripts/init-mcp.mjs
docker compose --env-file infra/docker/demo.env -f infra/docker/demo-systems.compose.yaml up -d --build --wait --wait-timeout 240
node scripts/init-database.mjs
node scripts/provision-database-readers.mjs
docker compose --env-file infra/docker/demo.env --env-file infra/docker/.env.mcp -f infra/docker/mcp-demo.compose.yaml up -d --build --wait --wait-timeout 180
docker compose --env-file infra/docker/.env.database -f infra/docker/database-gateway.compose.yaml up -d --build --wait --wait-timeout 180
docker compose --env-file infra/docker/.env --env-file infra/docker/.env.mcp --env-file infra/docker/.env.database -f infra/docker/compose.yaml -f infra/docker/mcp-platform.override.yaml -f infra/docker/database-platform.override.yaml up -d --build --wait --wait-timeout 180
```

On POSIX, use `./backend/mvnw`. Before starting backend on Linux, retain host ownership and grant runtime group access:

```sh
sudo chgrp 10001 .verification/mcp/keyring.json .verification/database/assertion-private.der
chmod 640 .verification/mcp/keyring.json .verification/database/assertion-private.der
chmod 644 .verification/database/assertion-public.der
```

Windows Docker bind permissions differ; preserve the host files and verify the containers can read their intended mounts. Gateway receives only the public delegation key. Backend alone receives the retained AES keyring and private signing key. No generated secret belongs in Git or a browser screenshot.

`init-database.mjs` creates ignored `infra/docker/.env.database` and `.verification/database/assertion-{private,public}.der` once. A partial keypair fails closed rather than replacing either file. Existing environment values and keypairs are retained. The helper records the actual source network subnet as the explicit development private-CIDR policy; changing networks requires operator review. The account provisioner creates only dedicated gateway readers and grants SELECT on the original business tables; it does not reset an existing account password, change native readers or migrate source business data.

## Test through the application

Open [the workspace](http://127.0.0.1:5173), sign in with your retained local administrator, select **Integrations → Databases → Add database**.

| Setting | Sales | Inventory |
|---|---|---|
| Type | PostgreSQL | MySQL |
| Host | sales | inventory |
| Port | 5432 | 3306 |
| Database | sales | inventory |
| Permitted schemas | sales | inventory |
| Username | gateway_sales_reader | gateway_inventory_reader |
| Password source, read locally | GATEWAY_SALES_READER_PASSWORD in ignored .env.database | GATEWAY_INVENTORY_READER_PASSWORD in ignored .env.database |
| TLS | Uncheck only for these explicit local development fixtures | Uncheck only for these explicit local development fixtures |

The host is the database container name reachable from the private gateway, not a browser/host loopback port. Verified TLS remains the form default. The fixtures do not provide operator-trusted production certificates; selecting verified TLS should fail closed rather than silently falling back.

1. Register and confirm DRAFT. Test connection and inspect the real schema; this fetches bounded metadata without sample rows.
2. Select `sales.products`, choose `product_id` and `product_name`, then approve the admin scope. For Inventory choose `inventory.items`, `sku` and `product_label`. Primary-key columns are required for deterministic ordering.
3. Activate the database, discover the two neutral MCP tools, and read rows in the manual explorer. Set maximum rows to 2 to see explicit truncation and bounded paging.
4. Add an EQ filter: product_id text `1001` returns **USB-C Dock**; sku text `WH-700` returns **Dock USB-C Standard**. Source rows are real independently owned fixture data.
5. Approve only the desired USER columns if users should read them. AUDITOR has metadata/history access without execution. Revoke a grant or disable the connector; new reads must be denied.
6. Editing creates a new immutable revision. Inspect, review and approve it again. Changing an already approved column selection requires reinspection before the replacement approval.

No database/table/column is auto-approved. Another permitted database or table uses the same dynamic forms and handlers; no core/frontend recompilation is needed. A destination outside operator policy requires an operator configuration change, not a tenant bypass. REST/OpenAPI, AI/chat and joins remain later phases.

## Verification and recovery

```powershell
node scripts/run-database-e2e.mjs
node scripts/run-mcp-e2e.mjs
```

The adversarial stack checker additionally needs the explicit observation override:

```powershell
docker compose --env-file infra/docker/.env.database -f infra/docker/database-gateway.compose.yaml -f verification/gateway-compatibility/observe.compose.yaml up -d --build --wait --wait-timeout 180
node scripts/verify-database-stack.mjs
```

That private test-only override enables token-protected admission counters and a bounded pre-dispatch hold for deterministic concurrency/restart/cancellation tests. They are not MCP tools, fetch no fake data, are not enabled by the normal profile, and cannot bypass current authority checks. The checker creates/soft-removes its registrations and disables its fixture identities, retains audit, temporarily stops/restarts only named EcoSync services, and restores them. Run it only against this local synthetic stack. It does not reset volumes, source data or the original administrator.

Gateway restart reconstructs the neutral catalog from authority; fresh calls still require current persisted grants and live metadata. Backend restart marks incomplete audits INTERRUPTED. Source outages set connection health unreachable without granting permissions; inspect again and explicitly reactivate after recovery. A changed DNS address set is denied until an operator validates the new target and restarts the gateway. Keys load at startup; maintain old encryption key versions while old ciphertext/backups exist, back up keys separately and reload the appropriate process during rotation.

Native Windows HTTP/NIO limitations remain visible from Phase 3. If host JDBC/Testcontainers networking or log collection fails, the same ten compiled gateway JDBC integration cases can run in the pinned Linux gateway image using the documented Testcontainers sibling-container pattern. First build/test-compile and generate the test classpath:

```powershell
./backend/mvnw.cmd -B -ntp -f managed-gateway/pom.xml package
./backend/mvnw.cmd -B -ntp -f managed-gateway/pom.xml org.apache.maven.plugins:maven-dependency-plugin:3.8.1:build-classpath "-Dmdep.outputFile=$PWD/.verification/phase4/gateway.classpath"
node scripts/run-gateway-linux-tests.mjs
```

The helper mounts only compiled test artifacts, the dependency repository and its runner output, plus the Docker socket needed to create its disposable databases. It does not mount platform environment/key files. CI normally uses Maven verify directly on Linux. [Verification](PHASE_4_VERIFICATION.md) records which execution actually passed and historical failures; do not infer a pass from a fallback plan.
