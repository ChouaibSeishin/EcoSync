# ADR 0005: GitOps delivery and reproducible comparison

Date: 2026-10-29. Status: accepted design; delivery/evaluation implementation deferred.

## Decision and rationale

Use Docker Compose for the Phase 1 local stack. GitHub Actions begins with actual build/test CI; publishing and delivery mature in Phase 10. Publish successful approved builds to GHCR by commit/version and immutable digest. Kustomize provides base and environment overlays; avoid introducing Helm templating for this application before it is needed. Prefer a separate GitOps repository; an isolated `infra/gitops/` directory is a documented fallback until provisioned. Argo CD reconciles Git; CI does not require direct cluster admin access.

Automatically sync approved development GitOps updates, use explicit production promotion, and document prune/self-heal choices. Revert desired image/config state in Git for rollback; do not rely on imperative rollback being retained under automated reconciliation. Migrations use a controlled Job and backward-compatible schema evolution. No actual cluster or Argo installation is claimed now, and an ambient configured cloud context is not authorization to deploy this project there.

Compare the MCP/LLM system with a conventional deterministic implementation using the same source fixtures, mappings, permissions and output oracle. Add an optional LLM/direct-adapter ablation to distinguish protocol overhead from model orchestration. Fix datasets/config versions, randomize paired runs, retain failures, report denominator counts and uncertainty, and measure integration effort separately from inference performance. Security success is enforced denial, not a fluent refusal.

## Alternatives and consequences

Direct CI `kubectl apply` would not meet the Argo GitOps requirement. Full Helm packaging and extra infrastructure services are deferred pending need. A synthetic benchmark alone cannot establish real organizational integration benefits. No favorable research result is assumed. See [deployment](../../deployment/DEPLOYMENT.md) and [methodology](../../research/METHODOLOGY.md).
