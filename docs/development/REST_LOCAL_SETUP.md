# Local REST setup

Branch `codex/phase-5-dynamic-rest-connectors`; Java 21.0.13+9, Node 24.22.2/npm 11.19.1 and Docker Linux engine. Existing [platform](LOCAL_SETUP.md), [demo](DEMO_SYSTEMS_SETUP.md), [native](MCP_LOCAL_SETUP.md) and [database](DATABASE_LOCAL_SETUP.md) setup remain valid. Preserve generated environment/key files, bootstrap identity, source accounts and all volumes.

## Build and start

For an initialized Phase 4 workspace, start the retained existing containers first. This workstation's optional `.verification/movies/start.ps1` preserves movies network/reader access; it is not a portable project dependency. Build with the wrapper:

```powershell
./backend/mvnw.cmd -B -ntp -f backend/pom.xml verify
./backend/mvnw.cmd -B -ntp -f rest-gateway/pom.xml verify
node scripts/init-rest.mjs
node scripts/start-rest.mjs
```

On POSIX use `./backend/mvnw`. Before starting on Linux, allow the runtime group to read only the intended retained key files:

```sh
sudo chgrp 10001 .verification/mcp/keyring.json .verification/rest/assertion-private.der
chmod 640 .verification/mcp/keyring.json .verification/rest/assertion-private.der
chmod 644 .verification/rest/assertion-public.der
```

`init-rest.mjs` creates ignored `infra/docker/.env.rest`, a separate REST delegation keypair and an internal REST source network once. It refuses a partial keypair, preserves existing credentials/key IDs and records the actual private CIDR. `start-rest.mjs` starts independent Partner/gateway, attaches Supplier to the REST source network using an additive source override, and rebuilds the opted-in platform. It preserves the optional ignored movies backend override when present. It never starts or depends on NeuroBackend/users_db. Fresh checkout also needs the earlier setup/build commands before these steps; no prebuilt local image is assumed in CI.

Operator destinations (both authority and gateway): `http://supplier:8081|dev,http://partner:8095/directory|dev`. Canonical effective ports are explicit. No UI entry expands the allowlist. For an external API, operators must separately supply an approved HTTPS destination, routing/firewall/trust and permitted private CIDRs where applicable. This local internal-network profile deliberately has no public egress. Never attach the backend or database gateway to API source networks to fix connectivity.

## Register through the UI

Open [Integrations](http://127.0.0.1:5173/integrations) → **REST APIs → Add REST API**.

| Field | Supplier | Partner |
|---|---|---|
| Base URL | `http://supplier:8081` | `http://partner:8095/directory` |
| Contract JSON | `demo-systems/supplier-api/openapi.json` | `demo-systems/partner-api/openapi.json` |
| Authentication | HTTP Bearer | Header API key |
| Header name | empty | `X-Partner-Key` |
| Secret (read locally; never paste into issues/logs) | `SUPPLIER_API_TOKEN` in ignored `infra/docker/demo.env` | `PARTNER_API_KEY` in ignored `infra/docker/.env.rest` |
| Representative operation | `listSuppliers` | `directorySearch` |
| Example typed parameters | `{"pageSize":2}` | `{"limit":2}` |

Register saves DRAFT and imports without source execution. Inspect the normalized operations and unsupported reasons. Explicitly approve the selected GET for INTEGRATION_ADMIN (and USER separately if intended), choose it in the explorer, then **Test approved API authentication**. That test executes the approved GET and discards source data; it cannot bypass approval. On HEALTHY/APPROVED, activate. Discover the neutral tools and invoke through MCP. Review untrusted JSON, correlation/history and explicit truncation. Partner `{"name":"River & Pine"}` demonstrates safe query encoding; `{"limit":125}` demonstrates the 100-item returned-data cap. Unsupported POST remains unapprovable.

Disable/revoke take effect before cleanup and block late result delivery. Revoking a credential requires a replacement secret when revising. Changing the destination/base path or auth type/header requires a replacement secret; blank retains a secret only for the same target and auth settings. A revised document/destination/auth configuration creates immutable new operation IDs and invalidates old approval. Reinspect/approve/test/activate explicitly. There is no chat or AI execution.

## Verification and recovery

```powershell
node --test demo-systems/partner-api/server.test.mjs
npm run lint --prefix frontend
npm run typecheck --prefix frontend
npm test --prefix frontend -- --run --maxWorkers=1
npm run build --prefix frontend
node scripts/start-rest.mjs --verification
node scripts/verify-rest-stack.mjs
node scripts/run-rest-e2e.mjs
```

The explicit verification profile enables authenticated fixture counters and controlled adversarial source responses; these are source fixture endpoints, never MCP tools, and are absent from normal startup. Run stack and browser tests sequentially so source counters remain meaningful. Verification registers unique synthetic integrations and revokes only its own records; it preserves user integrations. Browser traces are off to avoid recording typed secrets; screenshots occur after registration clears secret fields. Earlier regression commands remain in their respective phase docs. Pinned Playwright Chromium must be installed through the existing frontend package before browser tests.

After gateway restart, wait for Docker readiness and explicit tool refresh; the neutral catalog reconstructs from persisted authority on a five-second reconciliation interval. No query retries are automatic. Unexpected DNS changes fail closed: review operator routing and restart gateway only after verifying the new safe destination set. 401/403 from a source require credential/permission review, not a weaker destination/TLS policy. Network/gateway outages remain classified errors; they do not grant or revoke approvals automatically. Failed source tests require a successful approved test before healthy execution.

Stop with Docker Desktop Stop or the corresponding Compose `stop` commands. Do not delete volumes. Restart existing configuration with `node scripts/start-rest.mjs`; retain earlier gateway/native source containers as needed. Use ordinary startup without `--verification` to turn controlled probe routes off again.
