# ADR 0014: Explicit, bounded model proposals over existing MCP authority

Date: 2026-11-07. Status: selected; implementation acceptance requires actual tests and final-head CI.

## Starting point

Phase 5 final branch head is `7afd445dd2690505d8da2a1e421dc941d66823ad`, verified against the actual remote and successful five-job run [42021478856](https://github.com/ChouaibSeishin/EcoSync/actions/runs/42021478856). Earlier application acceptance `4c5c740`/42018755865 remains historical evidence. The only pre-existing change is the Supplier contract whitespace edit; preserve its bytes and exclude it from commits. Branch `codex/phase-6-llm-cross-system-chat` starts from that exact final head. Existing phase branches, default, public visibility, keys, reader grants and volumes remain unchanged.

## Decision and alternatives

Use Spring AI 2.0.2 ChatModel directly with explicit tool definitions and automatic execution disabled. A model response is an untrusted proposal, never an authorization token. A central loop validates each proposal against the current actor/tenant catalog and its original revision, budgets it, invokes the existing native/database/REST registry service, bounds its result, then rechecks authority before model exposure and final delivery. No new source SQL/HTTP adapter, automatic callback executor, ChatClient advisor loop or model credential discovery is introduced.

A server worker owns a persisted query with an idempotent client request UUID; one running turn per conversation. A bounded executor and monotonic 30-second total deadline cover model and source work, including synthesis. Eight calls and explicitly bounded rounds/context apply across the whole turn. Cancellation persists a fence first, cancels the worker and its currently admitted registry execution. Late completions cannot replace a terminal state. Restart marks incomplete work interrupted. One backend replica remains supported.

V6 persists owner/tenant conversations, ordered messages, request state, bounded evidence references and sanitized progress. It does not retain unrestricted source results. Source-derived assistant content is hidden on retrieval if any dependency no longer has current authorization; history is not an authorization cache. Follow-ups revalidate dependencies and retrieve fresh source data rather than replaying raw payloads. Conversation ownership is separate from integration administrator authority.

## Catalog, evidence and grounding

Native capabilities, approved database tables/columns and REST GETs use opaque model tool aliases. Server-side bindings carry connector UUID, entity UUID, revision and approval fingerprint; model arguments cannot replace them. Names, descriptions, schemas and data are untrusted. Do not include endpoints, connection credentials, keyrings or service/delegation tokens. Limit the catalog; expose omitted-capability state honestly.

Normalize successful results into bounded evidence with immutable request-local IDs, source identity, connector/revision/operation, timestamp, correlation, status and truncation. Failures are distinct evidence, not empty successes. Model-selected factual references must resolve to actual values. Deterministic decimal calculations operate on evidence references and explicit compatible currency/unit labels, not numbers invented by the model. Source facts, calculations, interpretation and missing evidence remain distinguishable. Prompt instructions are supplemental; server validation survives a malicious model.

Only seeded mapping version 1.0.0 may establish cross-system identity. Load the approved relationship artifact, never held-out cases or expected answers. Require unique approved records and exact identifiers; reject ambiguous, missing, conflicting and incompatible mappings. Operators explicitly bind concrete integration revisions to canonical fixture identities. Without such a verified binding, unrelated connector types are not assumed independent or silently joined. General mapping management and fuzzy matching remain Phase 7.

## Provider and deployment

Default `AI_ENABLED=false`: persistence and integration management work, chat truthfully reports unavailable, no synthetic answer or model download occurs. Optional Ollama is local only, explicitly configured, with no automatic model pull or cloud fallback. Deterministic test adapters exist only in test/verification configuration and identify themselves as test doubles. CI uses actual MCP services with that adapter; it is not evidence of LLM reasoning.

Measured workstation: 16 GiB physical RAM, Docker about 7.7 GiB, GTX 1070 Max-Q 8192 MiB. Evaluate Qwen3 1.7b Q4_K_M (about 1.4 GB, Apache 2.0, documented tools support) with a bounded context before accepting it. Record actual model manifest digest and runtime image digest, not a moving alias. Hardware measurement is not proof of latency; the 30-second limit still applies and optional real-model evidence is separate.

## Threats and residual limits

Prompt injection in user text, descriptions and source values cannot expand the backend catalog, destinations, SQL scope, grants or budgets. Redact secret-like fields/values before model exposure; never read environment or credential stores for model context. Refuse oversized/malformed proposals and ungrounded factual references. Arbitrary semantic truth of natural-language synthesis cannot be proven by a prompt; use explicit evidence references and deterministic rendering/calculation and report uncertainty. Local provider process/host compromise and authorized-user disclosure remain risks. Prior single-instance governance and local-development networking limits remain; no production certification or scientific accuracy claim is made.

## Official sources checked

- [Spring AI 2.0.2 ChatModel](https://docs.spring.io/spring-ai/reference/api/chatmodel.html), [tool calling](https://docs.spring.io/spring-ai/reference/api/tools.html), [Ollama](https://docs.spring.io/spring-ai/reference/api/chat/ollama-chat.html), [release](https://github.com/spring-projects/spring-ai/releases/tag/v2.0.2), [advisories](https://github.com/spring-projects/spring-ai/security/advisories).
- [Ollama tool calling](https://docs.ollama.com/capabilities/tool-calling), [Qwen3 1.7b model card](https://ollama.com/library/qwen3:1.7b). Exact runtime/model acceptance remains an experiment, not an assumption.

Spring Boot 4.1.2, Java 21 and the existing Spring AI BOM remain pinned. Verify the resolved graph and compilation before accepting provider dependencies. No Phase 7 work or branch merge is authorized.
