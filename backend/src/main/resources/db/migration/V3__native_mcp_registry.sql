CREATE TABLE mcp_integration (
 organization_id uuid NOT NULL REFERENCES organization(id), id uuid NOT NULL,
 display_name varchar(100) NOT NULL, state varchar(16) NOT NULL CHECK(state IN ('DRAFT','VALIDATING','ACTIVE','DEGRADED','DISABLED')),
 health varchar(16) NOT NULL DEFAULT 'UNKNOWN' CHECK(health IN ('UNKNOWN','HEALTHY','UNREACHABLE')),
 configuration_version bigint NOT NULL CHECK(configuration_version>0), created_by uuid NOT NULL REFERENCES app_user(id),
 created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP, updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
 last_connected_at timestamptz, last_discovered_at timestamptz, removed boolean NOT NULL DEFAULT false,
 PRIMARY KEY(organization_id,id)
);
CREATE UNIQUE INDEX mcp_name_unique ON mcp_integration(organization_id,lower(display_name)) WHERE NOT removed;
CREATE TABLE mcp_credential (
 organization_id uuid NOT NULL REFERENCES organization(id), id uuid NOT NULL,
 key_id varchar(64) NOT NULL, nonce bytea NOT NULL CHECK(octet_length(nonce)=12), ciphertext bytea NOT NULL,
 created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP, revoked boolean NOT NULL DEFAULT false,
 PRIMARY KEY(organization_id,id)
);
CREATE TABLE mcp_configuration (
 organization_id uuid NOT NULL, integration_id uuid NOT NULL, version bigint NOT NULL,
 connection_type varchar(16) NOT NULL DEFAULT 'NATIVE_MCP' CHECK(connection_type='NATIVE_MCP'),
 endpoint varchar(512) NOT NULL, transport varchar(32) NOT NULL DEFAULT 'STREAMABLE_HTTP' CHECK(transport='STREAMABLE_HTTP'),
 credential_id uuid NOT NULL, protocol_policy varchar(16) NOT NULL CHECK(protocol_policy IN ('AUTO_DUAL','LEGACY_2025','MODERN_2026')),
 fingerprint char(64) NOT NULL, created_by uuid NOT NULL REFERENCES app_user(id), created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
 PRIMARY KEY(organization_id,integration_id,version),
 FOREIGN KEY(organization_id,integration_id) REFERENCES mcp_integration(organization_id,id),
 FOREIGN KEY(organization_id,credential_id) REFERENCES mcp_credential(organization_id,id)
);
CREATE TABLE mcp_capability (
 organization_id uuid NOT NULL, integration_id uuid NOT NULL, version bigint NOT NULL, id uuid NOT NULL,
 remote_name varchar(128) NOT NULL, fingerprint char(64) NOT NULL, tool_json jsonb NOT NULL,
 protocol varchar(16) NOT NULL, server_json jsonb NOT NULL, present boolean NOT NULL DEFAULT true,
 discovered_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
 PRIMARY KEY(organization_id,id), UNIQUE(organization_id,integration_id,version,remote_name,fingerprint),
 FOREIGN KEY(organization_id,integration_id,version) REFERENCES mcp_configuration(organization_id,integration_id,version)
);
CREATE TABLE mcp_approval (
 organization_id uuid NOT NULL, id uuid NOT NULL, capability_id uuid NOT NULL,
 role varchar(32) NOT NULL CHECK(role IN ('INTEGRATION_ADMIN','USER')),
 status varchar(16) NOT NULL CHECK(status IN ('APPROVED','REVOKED','STALE')), actor uuid NOT NULL REFERENCES app_user(id),
 created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
 PRIMARY KEY(organization_id,id), FOREIGN KEY(organization_id,capability_id) REFERENCES mcp_capability(organization_id,id)
);
CREATE UNIQUE INDEX mcp_current_approval ON mcp_approval(organization_id,capability_id,role) WHERE status='APPROVED';
CREATE TABLE mcp_event (
 organization_id uuid NOT NULL, integration_id uuid NOT NULL, id uuid NOT NULL,
 actor uuid NOT NULL REFERENCES app_user(id), version bigint NOT NULL, capability_id uuid,
 protocol varchar(16), kind varchar(24) NOT NULL, status varchar(32) NOT NULL,
 authorization_outcome varchar(16) NOT NULL, error_code varchar(64), started_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
 ended_at timestamptz, duration_ms bigint,
 PRIMARY KEY(organization_id,id), FOREIGN KEY(organization_id,integration_id) REFERENCES mcp_integration(organization_id,id)
);
CREATE INDEX mcp_events_recent ON mcp_event(organization_id,integration_id,started_at DESC);
DO $$ BEGIN
 IF EXISTS(SELECT 1 FROM pg_roles WHERE rolname='ecosync_runtime') THEN
  GRANT SELECT,INSERT,UPDATE ON mcp_integration,mcp_credential,mcp_capability,mcp_approval,mcp_event TO ecosync_runtime;
  GRANT SELECT,INSERT ON mcp_configuration TO ecosync_runtime;
 END IF;
END $$;
