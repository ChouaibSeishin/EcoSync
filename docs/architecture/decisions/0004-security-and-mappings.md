# ADR 0004: Policy enforcement, evidence and explicit semantics

Date: 2026-10-29. Status: accepted for implementation.

## Context

Tool access and semantically correct answers require more than protocol standardization. An LLM's suggested tool call or mapping is untrusted input.

## Decision

Use same-origin server sessions with CSRF, organization-scoped authorization and execution-time capability checks. Keep secret references separate from connection metadata; encrypt local credential storage with externally supplied keys. Apply outbound destination policy to MCP, JDBC and REST connections, with exact development-only demo exceptions. Start with read-only schema-aware operations; no arbitrary SQL, commands, unbounded results or model-controlled credentials.

Require immutable, reviewed canonical mappings for identity/unit/currency conversion. Deterministic code joins data and computes totals; the model interprets questions and explains evidence. Missing or ambiguous relationships produce explicit limitations. Mapping suggestions cannot approve themselves. Seed a small reviewed fixture mapping for Phase 6; build general mapping UI/lifecycle in Phase 7.

Store sanitized provenance and operational timelines, not private model reasoning. A failed source remains visible. Bound calls, deadlines, payloads and retries independently of prompt instructions. Define retention before retaining raw conversational/source data in shared environments.

## Consequences

Security controls accompany features before Phase 8. Integration setup requires review of scopes and capabilities, so onboarding cannot be a single unrestricted URL/password form. Explicit mappings require administrator effort; measure that effort honestly in evaluation. Details and negative-test gates: [security](../SECURITY.md), [data model](../DATA_MODEL.md).
