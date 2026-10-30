# ADR 0006: Phase 1 identity and local runtime

Date: 2026-10-30. Status: implementation under verification.

Use Spring Security's form-authentication filter with JSON outcomes, server-side sessions, session-ID rotation and default CSRF protection. A public token endpoint supplies a session-bound masked token; the frontend obtains a fresh token before every unsafe request, including login/logout. No browser localStorage authentication data. Cookies are HttpOnly/SameSite Strict and Secure by default; the localhost-only Compose stack explicitly disables Secure for local HTTP.

Authentication uses JPA user lookup and a delegating adaptive password encoder. A bounded in-memory per-account/per-address login throttle limits attempts; it is intentionally single-instance and resets on process restart. Shared/multi-instance abuse controls remain hardening work. Tenant access is derived from authenticated membership and current enabled records; a platform administrator does not bypass tenant membership. General user/role administration UI is not implemented in Phase 1.

A one-shot setup process migrates with a non-superuser migration role and provisions an administrator transactionally under a database advisory lock. An immutable bootstrap marker prevents reset on later runs. The running backend uses a non-DDL runtime role and never receives bootstrap credentials. Local random credentials are generated into an ignored .env; no shared password exists. The local Docker owner account is for initialization only and is not used by the application.

Backend Docker images package the already verified Maven artifact rather than repeating Docker-dependent tests inside image builds. Build the jar before Compose. The frontend container is a development Vite server with a same-origin API proxy; it is not a production hosting configuration. Native local tests run on Windows; CI targets Ubuntu and linux/amd64 digest-pinned images. No publishing, Kubernetes or Argo resources are introduced.

Readiness includes DB availability; liveness excludes it. Health endpoints expose status only. The authenticated status API reports a live DB failure without exposing connection settings. Data persists in a named volume, while in-memory login sessions intentionally expire across backend restarts.
