# Phase 6 acceptance

Status: implementation and verification in progress, 2026-11-07. No final remote completion claim.

Exact base `7afd445dd2690505d8da2a1e421dc941d66823ad`, final Phase 5 run 42021478856. Branch `codex/phase-6-llm-cross-system-chat`. Preserve the earlier application acceptance evidence and all Phase 0–5 branches/default settings.

Required gates: persistent owner/tenant conversations; truthful AI-disabled startup; optional pinned local provider; contextual catalog over all three existing MCP mechanisms; independent backend validation; 30-second/eight-call bounds; cited evidence, deterministic arithmetic and explicitly bound seeded mappings; no guessed joins; partial/empty/failure distinctions; injection/authorization/cancellation tests; actual chat browsers and AI-disabled restart persistence; all five unchanged jobs plus a dedicated sixth successful job at the exact final remote head.

Deterministic model-adapter tests prove host control and real source execution, not model reasoning or LLM accuracy. The warm real-model synthetic tool-call experiment is separately identified; cold timeout and any unverified full-model behavior remain explicit limitations. [Verification](PHASE_6_VERIFICATION.md), [architecture](../architecture/CHAT_ORCHESTRATION.md), [security](../architecture/AI_SECURITY.md), [local setup](AI_LOCAL_SETUP.md).

Do not merge or begin Phase 7. Completion requires actual observed final-head CI, not an earlier green commit.
