# Native MCP local setup

Use the pinned toolchain and existing setup in [LOCAL_SETUP.md](LOCAL_SETUP.md) and [DEMO_SYSTEMS_SETUP.md](DEMO_SYSTEMS_SETUP.md). These commands preserve the existing named volumes and bootstrap identity. Run from the repository root. Do not run `down -v` or delete data directories.

```powershell
node scripts/init-local.mjs
node scripts/init-demo.mjs
node scripts/init-mcp.mjs
& backend/mvnw.cmd -B -ntp -f backend/pom.xml verify
& backend/mvnw.cmd -B -ntp -f mcp-servers/sales/pom.xml package
npm ci --prefix mcp-client
npm run verify --prefix mcp-client
npm ci --prefix mcp-servers/inventory
npm run verify --prefix mcp-servers/inventory
docker compose --env-file infra/docker/demo.env -f infra/docker/demo-systems.compose.yaml up -d --build --wait
docker compose --env-file infra/docker/demo.env --env-file infra/docker/.env.mcp -f infra/docker/mcp-demo.compose.yaml up -d --build --wait
docker compose --env-file infra/docker/.env --env-file infra/docker/.env.mcp -f infra/docker/compose.yaml -f infra/docker/mcp-platform.override.yaml up -d --build --wait
```

On Linux use `./backend/mvnw` instead of `mvnw.cmd`. Before starting the platform with the override, allow only its UID to read the external key file: `sudo chown 10001:10001 .verification/mcp/keyring.json` and `sudo chmod 600 .verification/mcp/keyring.json`. Preserve that ownership when rotating keys. Windows Docker Desktop bind permissions differ; the local read-only mount has been exercised successfully. Protect the ignored files with the host account's filesystem permissions.

`init-mcp.mjs` generates three independent bearer values in ignored `infra/docker/.env.mcp` and an AES-256 keyring in ignored `.verification/mcp/keyring.json`. It preserves existing files. Keep a protected backup of the keyring and platform database together; losing the keys loses credential decryption. No key is stored in Git or PostgreSQL. Changing the active key ID affects new writes; retain old keys while old ciphertext exists. Replace an integration credential through Edit configuration, then validate, review and activate its new revision. Removing a registration revokes its credential reference; disabling retains the reference for a reviewed replacement revision.

Open the existing frontend at `http://127.0.0.1:5173` (or the configured WEB_PORT), and sign in with the existing bootstrap account. Integrations → Add MCP server:

| Server | Endpoint entered in UI | Protocol policy | Credential source |
|---|---|---|---|
| Sales | `http://sales-mcp:8091/mcp` | Legacy 2025-11-25 | SALES_MCP_TOKEN in the ignored MCP environment |
| Inventory | `http://inventory-mcp:8092/mcp` | Modern 2026-07-28 | INVENTORY_MCP_TOKEN in the ignored MCP environment |

Test connection & discover, inspect schemas, explicitly approve admin and/or USER access to selected tools, then Activate integration. Manual invocation uses JSON arguments. For Sales revenue: `{"startInclusive":"2026-10-01T00:00:00Z","endExclusive":"2026-11-01T00:00:00Z","currency":"USD"}`. Inventory stock: `{"sku":"WH-700"}`. Results are actual untrusted source data; no AI executes them. Inspect the correlation ID and execution history, revoke a capability or disable the integration, and confirm new calls are unavailable. To re-enable a disabled registration, save a new revision and review it again.

The backend connects only to its metadata and MCP control networks. The adapter connects only to MCP control. Native servers alone connect to the source network with existing reader credentials. MCP ports are not published on the host. Supplier REST remains independent and is not registered through Ecosync.

An operator can permit another compatible native MCP endpoint by updating the exact endpoint allowlist in both Java and the adapter, then restarting those services. Registration itself requires no source or frontend code changes. Production requires approved HTTPS endpoints with valid TLS; the example HTTP exceptions apply only to the explicit development environment. OAuth, STDIO, arbitrary DB/REST registration and managed connectors are not implemented.

For the complete disposable verification, enable the authenticated test-only SQL admission counters:

```powershell
docker compose --env-file infra/docker/demo.env --env-file infra/docker/.env.mcp -f infra/docker/mcp-demo.compose.yaml -f verification/mcp-compatibility/observe.compose.yaml up -d --build --wait
node scripts/verify-mcp-stack.mjs
node scripts/run-mcp-e2e.mjs
```

The stack verifier intentionally restarts the backend and stops/restarts MCP services, preserves all volumes, and removes only its own registrations while retaining their audit history. It checks zero source SQL admissions for denied operations, real recovery, network membership, runtime DB privileges and actual service logs for credential values. Run it when a short local service interruption is acceptable. Counters are disabled by default, token-protected, never MCP tools, and have no published ports. Browser traces are off to avoid retaining registration credentials.

Stopping the MCP project does not stop the platform or source project. The ordinary platform Compose file starts with MCP management disabled; the explicit override enables management without making platform readiness depend on upstream MCP availability. Current limits and unsupported features are in [MCP_INTEGRATIONS.md](../architecture/MCP_INTEGRATIONS.md) and [MCP_SECURITY.md](../architecture/MCP_SECURITY.md).
