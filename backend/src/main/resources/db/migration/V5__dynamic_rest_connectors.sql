CREATE TABLE rest_credential (
 organization_id uuid NOT NULL REFERENCES organization(id), id uuid NOT NULL,
 key_id varchar(64) NOT NULL, nonce bytea NOT NULL CHECK(octet_length(nonce)=12), ciphertext bytea NOT NULL,
 revoked boolean NOT NULL DEFAULT false, created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
 PRIMARY KEY(organization_id,id)
);
CREATE TABLE rest_connector (
 organization_id uuid NOT NULL REFERENCES organization(id), id uuid NOT NULL, display_name varchar(100) NOT NULL,
 configuration_version bigint NOT NULL CHECK(configuration_version>0),
 state varchar(16) NOT NULL DEFAULT 'DRAFT' CHECK(state IN('DRAFT','INSPECTED','APPROVED','ACTIVE','DISABLED','REVOKED')),
 health varchar(24) NOT NULL DEFAULT 'UNKNOWN' CHECK(health IN('UNKNOWN','HEALTHY','UNREACHABLE','AUTHENTICATION_FAILED')),
 created_by uuid NOT NULL REFERENCES app_user(id), created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
 updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP, PRIMARY KEY(organization_id,id)
);
CREATE UNIQUE INDEX rest_connector_name ON rest_connector(organization_id,lower(display_name));
CREATE TABLE rest_configuration (
 organization_id uuid NOT NULL, connector_id uuid NOT NULL, version bigint NOT NULL,
 base_url varchar(512) NOT NULL, auth_type varchar(24) NOT NULL CHECK(auth_type IN('NONE','BEARER','HEADER_API_KEY')),
 auth_header varchar(64) NOT NULL, credential_id uuid, document jsonb NOT NULL, document_fingerprint char(64) NOT NULL,
 contract jsonb NOT NULL, fingerprint char(64) NOT NULL, created_by uuid NOT NULL REFERENCES app_user(id),
 created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP, PRIMARY KEY(organization_id,connector_id,version),
 FOREIGN KEY(organization_id,connector_id) REFERENCES rest_connector(organization_id,id),
 FOREIGN KEY(organization_id,credential_id) REFERENCES rest_credential(organization_id,id),
 CHECK((auth_type='NONE' AND credential_id IS NULL) OR (auth_type<>'NONE' AND credential_id IS NOT NULL))
);
CREATE TABLE rest_operation (
 organization_id uuid NOT NULL, connector_id uuid NOT NULL, version bigint NOT NULL, id uuid NOT NULL,
 operation_key varchar(100) NOT NULL, supported boolean NOT NULL, metadata jsonb NOT NULL, fingerprint char(64) NOT NULL,
 PRIMARY KEY(organization_id,id), UNIQUE(organization_id,connector_id,version,operation_key),
 FOREIGN KEY(organization_id,connector_id,version) REFERENCES rest_configuration(organization_id,connector_id,version)
);
CREATE TABLE rest_approval (
 organization_id uuid NOT NULL, id uuid NOT NULL, operation_id uuid NOT NULL,
 role varchar(32) NOT NULL CHECK(role IN('INTEGRATION_ADMIN','USER')), fingerprint char(64) NOT NULL,
 status varchar(16) NOT NULL CHECK(status IN('APPROVED','REVOKED','STALE')),
 actor uuid NOT NULL REFERENCES app_user(id), created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
 PRIMARY KEY(organization_id,id), FOREIGN KEY(organization_id,operation_id) REFERENCES rest_operation(organization_id,id)
);
CREATE UNIQUE INDEX rest_current_approval ON rest_approval(organization_id,operation_id,role) WHERE status='APPROVED';
CREATE TABLE rest_event (
 organization_id uuid NOT NULL, connector_id uuid NOT NULL, id uuid NOT NULL, actor uuid NOT NULL REFERENCES app_user(id),
 version bigint NOT NULL, operation_id uuid, kind varchar(24) NOT NULL, status varchar(32) NOT NULL, error_code varchar(64),
 started_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP, ended_at timestamptz, duration_ms bigint,
 PRIMARY KEY(organization_id,id), FOREIGN KEY(organization_id,connector_id) REFERENCES rest_connector(organization_id,id)
);
CREATE INDEX rest_events_recent ON rest_event(organization_id,connector_id,started_at DESC);
DO $$ BEGIN
 IF EXISTS(SELECT 1 FROM pg_roles WHERE rolname='ecosync_runtime') THEN
  GRANT SELECT,INSERT,UPDATE ON rest_connector,rest_credential,rest_approval,rest_event TO ecosync_runtime;
  GRANT SELECT,INSERT ON rest_configuration,rest_operation TO ecosync_runtime;
 END IF;
END $$;
