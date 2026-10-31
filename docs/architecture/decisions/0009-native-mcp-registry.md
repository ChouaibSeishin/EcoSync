# ADR 0009: PostgreSQL revisions and explicit capability grants

Accepted for Phase 3, 2026-10-31. Java remains the authorization authority. Native MCP registrations use organization-scoped UUIDs, immutable configuration revisions, separate encrypted credential references, discovered schema fingerprints, and append-only approval decisions. Connection health, governance state, and tool approval state are separate.

Every mutation checks current organization membership and INTEGRATION_ADMIN. USER execution requires an explicit current-schema role grant; AUDITOR can inspect metadata but cannot execute. Remote names are dispatch data, never API identifiers. Aliases derive from capability UUIDs. Every call rechecks PostgreSQL state and the remote schema before tools/call. Configuration edits create a new revision and invalidate grants. CAS checks discard validation finishing against an obsolete revision.

The adapter owns bounded transient connections keyed by organization/integration/revision. PostgreSQL owns permissions and configuration. Backend restart reconstructs connections lazily and compares fresh discovery before execution. Local leases carry a unique backend owner ID; replacing an owner retires the prior adapter lease. Phase 3 supports one backend replica; horizontal deployment requires distributed invocation admission and cancellation ownership and is not claimed here.

Disable first fences admission, persists DISABLED, revokes grants, and retires the adapter generation. Transport failure cannot override the persisted fence. No raw arguments/results are retained in audit. Re-enabling creates a new revision and requires validation and approvals again. Removing soft-deletes the registration and preserves audit/revision evidence.
