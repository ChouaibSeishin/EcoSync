# Phase 3 verification

Started 2026-10-31 UTC. Authorized scope: native remote MCP only. Phase 3 is in progress; no MCP implementation or compatibility success is claimed yet.

Verified base a38682428457cca417cbac8f20bc14e7c0792f21 exists remotely; Phase 1 is its ancestor; branch codex/phase-3-native-mcp was created at that exact commit. Public visibility and default codex/phase-0-foundation are unchanged. Phase 2 final run 41062443616 and both successful jobs were checked against the exact head.

One pre-existing user edit adds two trailing spaces after the supplier OpenAPI info opening brace. Its binary diff is preserved under ignored .verification/phase3/preexisting-user-change.patch and its scoped stash ID under user-stash-id.txt. The edit is temporarily separated to permit immutable fixture/hash checks; it must be restored at handoff and must not be included in Phase 3 commits.

Checkpoint A: Phase 2 handoff addenda are recorded. Official specification/SDK/Spring AI research and a real dual-era interoperability proof must pass before selecting the production client architecture or beginning the registry/UI implementation.

Checkpoint A compatibility gate passed on 2026-10-31. Current official releases and exact stable/RC POMs were rechecked. All modern specification pages were captured/inspected from the official repository; 2025 lifecycle/HTTP and SDK APIs were compared. The actual Java 2.1.0 -> Java 2025 proof passed; Java -> modern-only TS was explicitly unsupported at initialize; official TS 2.4.0 -> both peers passed real discovery, schemas, structured invocation, authentication, timeout and disconnect checks. Modern header/body mismatch, Origin and GET behavior were tested. Sanitized wire metadata contains no tokens. An initial Windows JDK NIO selector failure occurred before communication; the pinned isolated Linux proof then passed and cleaned up. ADR 0008 selects the official dual-era client service; no production registry/UI implementation began before this proof.
