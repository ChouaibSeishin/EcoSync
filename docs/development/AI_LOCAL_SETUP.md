# Local chat and model setup

Use branch `codex/phase-6-llm-cross-system-chat`, Java 21.0.13+9, Node 24.22.2/npm 11.19.1 and Docker Linux containers. Start from the initialized [Phase 5 stack](REST_LOCAL_SETUP.md); fresh checkouts also need the earlier platform/demo/native/database setup and retained keys/readers. Never delete source volumes or regenerate existing credentials silently.

## Start with AI disabled

Start the retained EcoSync demo, native MCP, database gateway and REST services first. `docker start` on the existing containers preserves this workstation's movies overrides; do not recreate its database gateway from a configuration that omits those overrides. `movies_db` is optional to the chat fixtures; NeuroBackend/users_db is unrelated and is not started.

```powershell
./backend/mvnw.cmd -B -ntp -f backend/pom.xml verify
node scripts/start-chat.mjs
```

Use `./backend/mvnw` on POSIX. The default is `AI_ENABLED=false`. Login, integration management, conversation creation/history/archive work without a model. Sending a message reports AI disabled and creates no fake assistant answer. Open [Chat](http://127.0.0.1:5173/chat).

The additive startup helper retains the existing native/database/REST control networks, keys and optional ignored movies backend override. It does not pull a model. Existing backend-only startup profiles still work with AI disabled.

## Explicit synthetic fixture enrollment

```powershell
node scripts/init-chat-demo.mjs
node scripts/start-chat.mjs
```

This uses the ordinary governed registration/inspection/approval/activation APIs for six specifically named Phase 6 demo integrations: native Sales/Inventory, managed Sales/Inventory databases, Supplier REST and Partner REST. Grants are explicit for INTEGRATION_ADMIN; USER is not automatically granted source access. Existing user integrations are not revised. The helper refuses to overwrite a non-active changed demo.

It writes ignored `.verification/chat/demo.json` and `bindings.json`. Bindings attest exact tenant, connector, entity, revision and fingerprint; canonical source identity, reviewed identifier paths and numeric-measure fields are operator configuration. They do not grant execution rights. Restart the backend after binding changes. Native tools without an explicit read-only declaration remain outside chat. A new compatible registered database/API can be used without chat-specific adapters; cross-system identity or calculations over unverified measures remain unavailable until explicitly configured.

## Optional local model

The evaluated candidate is Qwen3 `qwen3:1.7b`, actual model metadata 2.0B, GGUF Q4_K_M, about 1.4 GB of weights, Apache 2.0. Manifest digest:

`8f68893c685c3ddff2aa3fffce2aa60a30bb2da65ca488b61fff134a4d1730e7`

Runtime: Ollama 0.40.15, pinned OCI image digest `sha256:0e59999e0e8f9fd4e704bd788a89b7e4cad399367fb1365f8c102ac662288761`. See the [official model card](https://ollama.com/library/qwen3:1.7b) and [tool API](https://docs.ollama.com/capabilities/tool-calling).

Measured workstation: 16 GiB physical RAM, Docker about 7.7 GiB, GTX 1070 Max-Q 8192 MiB. The provided optional profile uses CPU (three CPUs, 3 GiB container ceiling), not an assumed working GPU runtime. Leave memory for the existing services; runtime image download is several GB in addition to weights. Other hardware requires its own latency/compatibility check.

```powershell
docker compose -f infra/docker/ai.compose.yaml up -d --wait --wait-timeout 180
docker compose -f infra/docker/ai.compose.yaml exec -T ollama ollama pull qwen3:1.7b
node scripts/warm-local-model.mjs
node scripts/verify-local-model.mjs
node scripts/start-chat.mjs --model
node scripts/verify-chat-local-model.mjs --run-real-model
```

Warm-up is an explicit operator setup action with no source data, outside any user query. It never increases the 30-second chat deadline. The initial cold synthetic compatibility request timed out at 30 seconds; a warm request proposed the exact tool/arguments in 11,758 ms. That proves structured tool-call compatibility only. Full source-backed real-model behavior and multi-round latency must be recorded separately. The application fails honestly when the model cannot finish within budget.

The backend verifies the loaded tag's digest before each proposal. Moving/changed/missing models fail closed. To use another reviewed local model, explicitly set `AI_MODEL` and `AI_MODEL_DIGEST` before `--model`; repeat compatibility and source-backed checks. No cloud fallback, provider credentials, automatic pull or public provider endpoint is configured. `--model` and `--verification` cannot be combined.

## Functional and security verification

```powershell
./backend/mvnw.cmd -B -ntp -f backend/pom.xml verify
npm run lint --prefix frontend
npm run typecheck --prefix frontend
npm test --prefix frontend -- --run
npm run build --prefix frontend
node scripts/enable-chat-verification.mjs
node scripts/verify-chat-stack.mjs
node scripts/verify-chat-races.mjs
node scripts/verify-chat-restart.mjs
node scripts/run-chat-e2e.mjs
node scripts/start-chat.mjs
node scripts/verify-chat-disabled.mjs
```

Install the pinned Playwright Chromium through the existing frontend package if needed. Run source-counter tests and browser tests sequentially. `enable-chat-verification` explicitly enables authenticated fixture counters, preserves optional movies gateway overrides and enables only a clearly labeled deterministic model adapter. This is a verification profile, not an LLM and not normal startup. CI exercises actual MCP clients/gateways/source data with this adapter and does not download a model.

The live script uses independent development reference facts and seeded mappings, never held-out questions or expected answers. It saves sanitized `.verification/phase6` evidence. Browser traces are off to avoid recording credentials. Runtime/source payloads and environment are omitted from failure diagnostics.

After verification, use normal `start-chat.mjs` and normal REST/native/database configurations to disable fixture probe routes again, retaining local movies overrides. To stop, use Docker Desktop Stop or Compose `stop`, including `docker compose -f infra/docker/ai.compose.yaml stop` for the optional runtime. Keep model, platform and source volumes. Archive retains history and does not free the conversation storage quota.

## Recovery and troubleshooting

A missing source is a failure, not an empty answer. Check the integration's current state, role approval, revision, configured destination and actual gateway health. Reinspect/reapprove/test/activate through its existing workflow where required; chat does not grant rights. Rebinding a canonical source or numeric measure invalidates old history dependencies.

After restart, incomplete turns are INTERRUPTED; retry as a new request. A transient browser submission retry reuses its request ID and cannot execute the query twice. Cancellation fences delivery but cannot undo an already received remote GET/SQL read. Expired deadlines, exhausted calls/rounds and evidence/context limits are explicit failures, not successful empty responses.

Thirty-second queries, eight calls, six default model rounds, 16 KiB evidence, 64 KiB context and 4,000-character input are operator-controlled safe limits; clients cannot increase them. Evidence/schema limits can reject legitimate large responses. Free-form factual synthesis, fuzzy matching, arbitrary joins, writes, general mapping management and scientific evaluation are outside this phase. [Security and residual risks](../architecture/AI_SECURITY.md), [architecture](../architecture/CHAT_ORCHESTRATION.md), [verification](PHASE_6_VERIFICATION.md).

A controlled stop/start may give a source container a different IP. The gateway intentionally pins the complete DNS address set. Recovery requires an operator gateway restart followed by normal inspect/approve/test/activate; do not weaken destination rules to bypass pinning.

The optional application smoke explicitly creates a temporary USER fixture with only the Partner search grant, then disables the identity and restores any grant it introduced. It records actual Spring AI/MCP/source status separately. A timeout or invalid model response is retained as failure, never relabeled as successful reasoning.

Native MCP bridge clients expire after five idle minutes. The registry now reconnects and rediscovers metadata at admission before a single protected invocation; this is not a retry of a business read. Revoked revisions remain denied.
