# Phase 6 verification

Status: in progress, 2026-11-07; no completion claim.

Base `7afd445dd2690505d8da2a1e421dc941d66823ad`, branch `codex/phase-6-llm-cross-system-chat`. Actual remote head/default/visibility and successful Phase 5 final run 42021478856 were verified before modification. The original Supplier OpenAPI whitespace change is preserved. All twelve stopped EcoSync services were started explicitly; the existing movies container was already running. No unrelated stacks or source volumes were reset.

Ordered checkpoints: A handoff/ADR; B provider compatibility and bounded experiment; C persistence/API; D catalog/loop; E budgets; F grounded evidence/calculations; G explicitly bound seeded mappings; H prompt/injection tests; I real React chat; J development-only dataset cases; K adversarial/live/browser regressions; L six-job final-head CI and documentation. Each requires observed evidence. Never inspect held-out cases/answers or tune against them.

Record deterministic test-double evidence separately from real-model evidence. A previous green head cannot prove a newer commit. Report suite skips separately from failure-only diagnostics and preserve earlier failures/corrections. No final tracked edit after successful final-head verification.

## Initial implementation checkpoints

The controlled core checkpoint passed ten new unit tests and 22 actual PostgreSQL conversation/REST-authority tests (controlled model and gateway peer, not live MCP proof). Original 25 backend unit tests also passed before the focused checkpoint. All 32 React tests, TypeScript, ESLint and production build passed. Full live MCP, browser and final six-job CI acceptance remain open.

The first IT run used a new DriverManager connection for each metadata statement under heavy local image extraction; seven of fourteen assertions hit the three-second fixture deadline. A production-shaped six-connection Hikari pool fixed the fixture without changing the query deadlines or assertions; fourteen passed, then the expanded 22-test suite passed. Compilation caught an ambiguous JdbcTemplate callback, a RetryTemplate API assumption, and test syntax issues; corrected using actual dependency bytecode/source before acceptance.

Spring AI Ollama/model/retry/commons resolved exactly to 2.0.2 with Spring Framework 7.0.10 under unchanged Boot 4.1.2/Java 21. No model starter or auto-download is installed. Optional runtime image 0.40.15 was resolved to immutable OCI digest `sha256:0e59999e0e8f9fd4e704bd788a89b7e4cad399367fb1365f8c102ac662288761`. Qwen3 1.7b tag resolves to manifest `8f68893c685c3ddff2aa3fffce2aa60a30bb2da65ca488b61fff134a4d1730e7`, actual details 2.0B/Q4_K_M. Cold compatibility request timed out at 30 seconds; a separately recorded warm request proposed the exact synthetic tool/arguments in 11,758 ms. This is real-model tool-call compatibility, not EcoSync end-to-end or scientific accuracy evidence. No source operations were involved. Keep both results; no cloud fallback or increased query deadline.
